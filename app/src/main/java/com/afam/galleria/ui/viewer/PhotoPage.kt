package com.afam.galleria.ui.viewer

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.calculateCentroid
import androidx.compose.ui.input.pointer.calculatePan
import androidx.compose.ui.input.pointer.calculateZoom
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.afam.galleria.data.MediaItem
import com.afam.galleria.ui.components.Thumbs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

/** Stato di zoom e spostamento di una foto. */
@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var container: Size = Size.Zero
    var content: Size = Size.Zero

    val isZoomed: Boolean get() = scale > 1.02f

    fun clamp(o: Offset, s: Float): Offset {
        if (s <= 1f) return Offset.Zero
        val maxX = max(0f, (content.width * s - container.width) / 2f)
        val maxY = max(0f, (content.height * s - container.height) / 2f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    fun transform(centroid: Offset, zoom: Float, pan: Offset) {
        val newScale = (scale * zoom).coerceIn(0.7f, 8f)
        val center = Offset(container.width / 2f, container.height / 2f)
        val c = centroid - center
        val raw = c - (c - offset) * (newScale / scale) + pan
        offset = if (newScale >= 1f) clamp(raw, newScale) else raw * newScale
        scale = newScale
    }

    /** Sposta l'immagine ingrandita; restituisce true se si è mossa. */
    fun pan(delta: Offset): Boolean {
        val next = clamp(offset + delta, scale)
        val moved = next != offset
        offset = next
        return moved
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }
}

/** Calcola il rettangolo in cui un'immagine con il rapporto [aspect] viene mostrata adattata al contenitore. */
fun fitSize(container: Size, aspect: Float): Size {
    if (container.width <= 0f || container.height <= 0f || aspect <= 0f) return container
    val containerAspect = container.width / container.height
    return if (aspect > containerAspect) Size(container.width, container.width / aspect)
    else Size(container.height * aspect, container.height)
}

/**
 * Gestione dei gesti di una pagina: pizzico per lo zoom, trascinamento quando ingrandita,
 * trascinamento verticale per chiudere (passa il controllo al visualizzatore).
 */
suspend fun PointerInputScope.viewerGestures(
    zoom: ZoomState?,
    onZoomChange: (Boolean) -> Unit,
    onDismissDrag: (Offset) -> Unit,
    onDismissEnd: (Float) -> Unit,
    onZoomEnd: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // 0 = indeciso, 1 = zoom/pan, 2 = chiusura, 3 = lascia al pager
        var mode = 0
        var total = Offset.Zero
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (mode != 3) {
                if (pressed.size >= 2 && zoom != null && mode != 2) {
                    mode = 1
                    zoom.transform(event.calculateCentroid(), event.calculateZoom(), event.calculatePan())
                    onZoomChange(zoom.isZoomed)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                } else if (pressed.size == 1) {
                    val change = pressed[0]
                    val delta = change.positionChange()
                    if (zoom != null && zoom.isZoomed && mode != 2) {
                        mode = 1
                        zoom.pan(delta)
                        change.consume()
                    } else if (mode == 2) {
                        tracker.addPosition(change.uptimeMillis, change.position)
                        onDismissDrag(delta)
                        change.consume()
                    } else if (mode == 0) {
                        total += delta
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (total.getDistance() > viewConfiguration.touchSlop) {
                            if (abs(total.y) > abs(total.x) * 1.2f) {
                                mode = 2
                                onDismissDrag(total)
                                change.consume()
                            } else {
                                mode = 3
                            }
                        }
                    }
                }
            }
        } while (event.changes.any { it.pressed })
        when (mode) {
            2 -> onDismissEnd(tracker.calculateVelocity().y)
            1 -> onZoomEnd()
        }
    }
}

@Composable
fun PhotoPage(
    item: MediaItem,
    isCurrent: Boolean,
    dragOffset: Offset,
    dragScale: Float,
    onTap: () -> Unit,
    onZoomChange: (Boolean) -> Unit,
    onDismissDrag: (Offset) -> Unit,
    onDismissEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zoom = remember(item.id) { ZoomState() }
    val currentOnZoomChange by rememberUpdatedState(onZoomChange)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDismissDrag by rememberUpdatedState(onDismissDrag)
    val currentOnDismissEnd by rememberUpdatedState(onDismissEnd)

    val thumb = remember(item.cacheKey) { Thumbs.peekBest(item) }
    val full by produceState(initialValue = null as android.graphics.Bitmap?, item.cacheKey) {
        value = Thumbs.loadFull(context, item, 4096)
    }

    LaunchedEffect(isCurrent) {
        if (!isCurrent && (zoom.scale != 1f || zoom.offset != Offset.Zero)) {
            zoom.reset()
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val container = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val bitmap = full ?: thumb
        val aspect = when {
            bitmap != null && bitmap.height > 0 -> bitmap.width.toFloat() / bitmap.height
            else -> item.aspect
        }
        zoom.container = container
        zoom.content = fitSize(container, aspect)

        val layer = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val s = zoom.scale * (if (isCurrent) dragScale else 1f)
                scaleX = s
                scaleY = s
                translationX = zoom.offset.x + (if (isCurrent) dragOffset.x else 0f)
                translationY = zoom.offset.y + (if (isCurrent) dragOffset.y else 0f)
            }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(item.id) {
                    viewerGestures(
                        zoom = zoom,
                        onZoomChange = { currentOnZoomChange(it) },
                        onDismissDrag = { currentOnDismissDrag(it) },
                        onDismissEnd = { currentOnDismissEnd(it) },
                        onZoomEnd = {
                            if (zoom.scale < 1f) {
                                val startScale = zoom.scale
                                val startOffset = zoom.offset
                                scope.launch {
                                    animate(0f, 1f, animationSpec = spring(stiffness = 500f)) { v, _ ->
                                        zoom.scale = startScale + (1f - startScale) * v
                                        zoom.offset = startOffset * (1f - v)
                                    }
                                    currentOnZoomChange(false)
                                }
                            }
                        },
                    )
                }
                .pointerInput(item.id) {
                    detectTapGestures(
                        onTap = { currentOnTap() },
                        onDoubleTap = { tap ->
                            val startScale = zoom.scale
                            val startOffset = zoom.offset
                            val targetScale = if (zoom.isZoomed) 1f else 2.6f
                            val center = Offset(container.width / 2f, container.height / 2f)
                            val targetOffset = if (targetScale == 1f) Offset.Zero
                            else zoom.clamp((tap - center) * (1f - targetScale), targetScale)
                            scope.launch {
                                animate(0f, 1f, animationSpec = spring(dampingRatio = 0.9f, stiffness = 400f)) { v, _ ->
                                    zoom.scale = startScale + (targetScale - startScale) * v
                                    zoom.offset = startOffset + (targetOffset - startOffset) * v
                                }
                            }
                            currentOnZoomChange(targetScale > 1f)
                        },
                    )
                }
        ) {
            if (item.isGif) {
                AnimatedImage(item, layer)
            } else if (bitmap != null) {
                val image = remember(bitmap) { bitmap.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = layer,
                )
            }
        }
    }
}

/** GIF e immagini animate, riprodotte con AnimatedImageDrawable. */
@Composable
fun AnimatedImage(item: MediaItem, modifier: Modifier) {
    val context = LocalContext.current
    val drawable by produceState<Drawable?>(null, item.cacheKey) {
        value = withContext(Dispatchers.IO) {
            try {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.contentResolver, item.uri))
            } catch (e: Exception) {
                null
            }
        }
    }
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        },
        update = { view ->
            if (view.drawable !== drawable) {
                view.setImageDrawable(drawable)
                (drawable as? AnimatedImageDrawable)?.start()
            }
        },
        modifier = modifier,
    )
}
