package com.afam.galleria.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.afam.galleria.data.MediaItem
import com.afam.galleria.ui.nav.LocalNavigator
import com.afam.galleria.ui.nav.LocalThumbRegistry
import com.afam.galleria.ui.theme.Ios
import com.afam.galleria.ui.theme.IosType
import com.afam.galleria.util.Dates
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

val COLUMN_STEPS = listOf(2, 3, 4, 5, 7)

/** Stato della modalità di selezione multipla. */
@Stable
class SelectionState {
    var active by mutableStateOf(false)
    var selected by mutableStateOf<Set<Long>>(emptySet())

    val count: Int get() = selected.size

    fun start() {
        active = true
    }

    fun exit() {
        active = false
        selected = emptySet()
    }

    fun toggle(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun isSelected(id: Long) = id in selected

    fun items(from: List<MediaItem>): List<MediaItem> {
        val ids = selected
        return from.filter { it.id in ids }
    }
}

/**
 * Griglia di foto in stile iOS: pizzica per cambiare il numero di colonne,
 * tieni premuto e trascina per selezionare più elementi.
 */
@Composable
fun MediaGrid(
    items: List<MediaItem>,
    state: LazyGridState,
    columns: Int,
    selection: SelectionState,
    favorites: Set<Long>,
    onOpen: (MediaItem, Rect?) -> Unit,
    modifier: Modifier = Modifier,
    onColumnsChange: ((Int) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    spacing: Dp = 1.5.dp,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    badge: ((MediaItem) -> String?)? = null,
    fastScroll: Boolean = true,
    fastScrollTop: Dp = 0.dp,
    fastScrollBottom: Dp = 0.dp,
    selectionEnabled: Boolean = true,
) {
    val registry = LocalThumbRegistry.current
    val nav = LocalNavigator.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val headerCount = if (header != null) 1 else 0

    val indexOf = remember(items) {
        HashMap<Long, Int>(items.size * 2).also { map -> items.forEachIndexed { i, it -> map[it.id] = i } }
    }
    val currentItems by rememberUpdatedState(items)
    val currentIndexOf by rememberUpdatedState(indexOf)
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentColumns by rememberUpdatedState(columns)
    val currentOnColumnsChange by rememberUpdatedState(onColumnsChange)

    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val thumbSize = when {
        screenWidthPx / columns > 420 -> Thumbs.LARGE
        screenWidthPx / columns > 190 -> Thumbs.GRID
        else -> Thumbs.SMALL
    }

    // Mantiene visibile l'elemento aperto nel visualizzatore (per l'animazione di chiusura).
    LaunchedEffect(nav.viewerItemId, items) {
        val id = nav.viewerItemId ?: return@LaunchedEffect
        val index = indexOf[id] ?: return@LaunchedEffect
        val info = state.layoutInfo
        val visible = info.visibleItemsInfo.firstOrNull { it.key == id }
        val fullyVisible = visible != null &&
            visible.offset.y >= info.viewportStartOffset + info.beforeContentPadding &&
            visible.offset.y + visible.size.height <= info.viewportEndOffset - info.afterContentPadding
        if (!fullyVisible) {
            val viewport = info.viewportEndOffset - info.viewportStartOffset
            state.scrollToItem(index + headerCount, -(viewport / 3))
        }
    }

    // Selezione trascinando.
    var dragAnchor by remember { mutableStateOf<Int?>(null) }
    var dragCurrent by remember { mutableStateOf<Int?>(null) }
    var dragAdds by remember { mutableStateOf(true) }
    var dragBase by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var dragPointer by remember { mutableStateOf<Offset?>(null) }
    var autoScroll by remember { mutableFloatStateOf(0f) }

    fun indexAt(pos: Offset): Int? {
        val info = state.layoutInfo
        val y = pos.y.roundToInt() + info.viewportStartOffset
        val x = pos.x.roundToInt()
        val hit = info.visibleItemsInfo.firstOrNull { item ->
            x >= item.offset.x && x < item.offset.x + item.size.width &&
                y >= item.offset.y && y < item.offset.y + item.size.height
        } ?: return null
        val key = hit.key as? Long ?: return null
        return currentIndexOf[key]
    }

    fun applyDrag() {
        val a = dragAnchor ?: return
        val b = dragCurrent ?: return
        val list = currentItems
        val lo = min(a, b).coerceIn(0, list.lastIndex)
        val hi = max(a, b).coerceIn(0, list.lastIndex)
        val range = (lo..hi).map { list[it].id }
        selection.selected = if (dragAdds) dragBase + range else dragBase - range.toSet()
    }

    LaunchedEffect(autoScroll) {
        if (autoScroll == 0f) return@LaunchedEffect
        while (isActive) {
            state.scrollBy(autoScroll)
            dragPointer?.let { p -> indexAt(p)?.let { dragCurrent = it; applyDrag() } }
            delay(16)
        }
    }

    Box(modifier) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier
                .fillMaxSize()
                // Pizzico con due dita: cambia il numero di colonne.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var zoom = 1f
                        var fired = false
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                zoom *= event.calculateZoom()
                                val change = currentOnColumnsChange
                                if (!fired && change != null) {
                                    val i = COLUMN_STEPS.indexOf(currentColumns).let { if (it < 0) 2 else it }
                                    if (zoom > 1.25f && i > 0) {
                                        change(COLUMN_STEPS[i - 1]); fired = true
                                    } else if (zoom < 0.8f && i < COLUMN_STEPS.lastIndex) {
                                        change(COLUMN_STEPS[i + 1]); fired = true
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                // Tocco, pressione prolungata e trascinamento per selezionare.
                .pointerInput(selectionEnabled) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var up: PointerInputChange? = null
                        var cancelled = false
                        val finished = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.size > 1) {
                                    cancelled = true; break
                                }
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || change.isConsumed) {
                                    cancelled = true; break
                                }
                                if (change.changedToUp()) {
                                    up = change; break
                                }
                                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    cancelled = true; break
                                }
                            }
                            true
                        }
                        if (finished != null) {
                            val tapped = up
                            if (!cancelled && tapped != null) {
                                val index = indexAt(tapped.position)
                                if (index != null) {
                                    tapped.consume()
                                    val item = currentItems[index]
                                    if (selection.active) selection.toggle(item.id)
                                    else currentOnOpen(item, registry[item.id])
                                }
                            }
                            return@awaitEachGesture
                        }
                        if (!selectionEnabled) return@awaitEachGesture
                        // Pressione prolungata: entra in selezione e trascina.
                        val start = indexAt(down.position) ?: return@awaitEachGesture
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val startId = currentItems[start].id
                        if (!selection.active) {
                            selection.start()
                            selection.selected = emptySet()
                        }
                        dragAdds = !selection.isSelected(startId)
                        dragBase = selection.selected
                        dragAnchor = start
                        dragCurrent = start
                        applyDrag()
                        val edge = 72.dp.toPx()
                        val height = size.height.toFloat()
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            dragPointer = change.position
                            indexAt(change.position)?.let {
                                if (it != dragCurrent) {
                                    dragCurrent = it
                                    applyDrag()
                                }
                            }
                            val y = change.position.y
                            autoScroll = when {
                                y < edge -> -((edge - y) / edge * 28f).coerceAtLeast(4f)
                                y > height - edge -> ((y - (height - edge)) / edge * 28f).coerceAtLeast(4f)
                                else -> 0f
                            }
                        }
                        autoScroll = 0f
                        dragPointer = null
                        dragAnchor = null
                        dragCurrent = null
                    }
                },
        ) {
            if (header != null) {
                item(key = "__header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                    header()
                }
            }
            items(items, key = { it.id }, contentType = { "media" }) { item ->
                GridCell(
                    item = item,
                    thumbSize = thumbSize,
                    favorite = item.id in favorites,
                    selecting = selection.active,
                    selected = selection.active && item.id in selection.selected,
                    badge = badge?.invoke(item),
                    onPositioned = { rect -> registry.put(item.id, rect) },
                    onDisposed = { rect -> registry.remove(item.id, rect) },
                )
            }
            if (footer != null) {
                item(key = "__footer", span = { GridItemSpan(maxLineSpan) }, contentType = "footer") {
                    footer()
                }
            }
        }

        if (fastScroll && items.size > 150) {
            FastScroller(
                state = state,
                total = items.size + headerCount,
                label = { index ->
                    val i = (index - headerCount).coerceIn(0, currentItems.lastIndex)
                    currentItems.getOrNull(i)?.let { Dates.monthYear(it.dateTaken) }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = fastScrollTop, bottom = fastScrollBottom),
                onScrollTo = { index -> scope.launch { state.scrollToItem(index) } },
            )
        }
    }
}

@Composable
private fun GridCell(
    item: MediaItem,
    thumbSize: Int,
    favorite: Boolean,
    selecting: Boolean,
    selected: Boolean,
    badge: String?,
    onPositioned: (Rect) -> Unit,
    onDisposed: (Rect?) -> Unit,
) {
    var lastRect by remember { mutableStateOf<Rect?>(null) }
    DisposableEffect(item.id) {
        onDispose { onDisposed(lastRect) }
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .onGloballyPositioned { coords ->
                val rect = coords.boundsInWindow()
                lastRect = rect
                onPositioned(rect)
            }
    ) {
        MediaThumbnail(item, Modifier.fillMaxSize(), size = thumbSize)
        val shadowText = IosType.caption2.copy(
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 6f),
        )
        if (item.isVideo) {
            Text(
                Dates.duration(item.duration),
                style = shadowText,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 5.dp, bottom = 3.dp),
            )
        }
        if (favorite) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 4.dp, bottom = 4.dp)
                    .size(13.dp),
            )
        }
        if (badge != null) {
            Text(
                badge,
                style = shadowText,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp),
            )
        }
        if (selecting && selected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.22f))
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .size(22.dp)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Ios.colors.accent)
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
    }
}

/** Cursore per scorrere velocemente librerie grandi, con l'etichetta del mese. */
@Composable
fun FastScroller(
    state: LazyGridState,
    total: Int,
    label: (Int) -> String?,
    onScrollTo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ios.colors
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val fraction by remember(total) {
        derivedStateOf {
            val visible = state.layoutInfo.visibleItemsInfo.size
            val maxFirst = (total - visible).coerceAtLeast(1)
            (state.firstVisibleItemIndex.toFloat() / maxFirst).coerceIn(0f, 1f)
        }
    }
    val shown = dragging || state.isScrollInProgress
    var keepVisible by remember { mutableStateOf(false) }
    LaunchedEffect(shown) {
        if (shown) keepVisible = true else {
            delay(1200)
            keepVisible = false
        }
    }
    val alpha by animateFloatAsState(if (shown || keepVisible) 1f else 0f, tween(250), label = "scroller")
    if (alpha <= 0.01f) return

    BoxWithConstraints(modifier.fillMaxHeight().width(160.dp)) {
        val trackPx = constraints.maxHeight.toFloat()
        val thumbPx = with(density) { 46.dp.toPx() }
        val f = if (dragging) dragFraction else fraction
        val y = ((trackPx - thumbPx) * f).roundToInt().coerceAtLeast(0)
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, y) }
                .alpha(alpha),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dragging) {
                val text = label((dragFraction * (total - 1)).roundToInt())
                if (text != null) {
                    Text(
                        text,
                        style = IosType.footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = c.label,
                        modifier = Modifier
                            .shadow(6.dp, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.card)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            Box(
                Modifier
                    .width(30.dp)
                    .height(46.dp)
                    .pointerInput(total) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                dragging = true
                                dragFraction = fraction
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        ) { change, dy ->
                            change.consume()
                            val range = (trackPx - thumbPx).coerceAtLeast(1f)
                            dragFraction = (dragFraction + dy / range).coerceIn(0f, 1f)
                            onScrollTo((dragFraction * (total - 1)).roundToInt())
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(if (dragging) 8.dp else 6.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (c.isDark) Color(0xCCAEAEB2) else Color(0xB3636366))
                )
            }
        }
    }
}

/** Valore assoluto massimo, usato per decidere la direzione di trascinamento. */
internal fun dominantAxisIsVertical(delta: Offset): Boolean = abs(delta.y) > abs(delta.x)
