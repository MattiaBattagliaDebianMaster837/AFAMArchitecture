package com.afam.galleria.ui.editor

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Exposure
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PhotoFilter
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vignette
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afam.galleria.GalleryViewModel
import com.afam.galleria.data.MediaItem
import com.afam.galleria.ui.components.LocalOverlay
import com.afam.galleria.ui.components.MediaThumbnail
import com.afam.galleria.ui.components.SheetAction
import com.afam.galleria.ui.components.Thumbs
import com.afam.galleria.ui.components.iosClickable
import com.afam.galleria.ui.components.plainClickable
import com.afam.galleria.ui.theme.IosType
import com.afam.galleria.util.SystemBarsEffect
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val EditYellow = Color(0xFFFFD60A)
private val ToolGray = Color(0xFF8E8E93)

private fun adjustIcon(a: Adjust): ImageVector = when (a) {
    Adjust.EXPOSURE -> Icons.Rounded.Exposure
    Adjust.BRIGHTNESS -> Icons.Rounded.LightMode
    Adjust.CONTRAST -> Icons.Rounded.Brightness6
    Adjust.SATURATION -> Icons.Rounded.InvertColors
    Adjust.WARMTH -> Icons.Rounded.Thermostat
    Adjust.TINT -> Icons.Rounded.Colorize
    Adjust.VIGNETTE -> Icons.Rounded.Vignette
}

private class AspectOption(val label: String, val ratio: Float?)

@Composable
fun EditorScreen(vm: GalleryViewModel, item: MediaItem, isTop: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val overlay = LocalOverlay.current
    val saving by vm.saving.collectAsStateWithLifecycle()

    var params by remember { mutableStateOf(EditParams()) }
    var tab by remember { mutableIntStateOf(0) }
    var adjust by remember { mutableStateOf(Adjust.EXPOSURE) }
    var autoOn by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
    var aspectRatio by remember { mutableStateOf<Float?>(null) }

    val base by produceState<Bitmap?>(null, item.cacheKey) {
        value = EditRenderer.preview(context, item)
    }
    val oriented = remember(base, params.rotation, params.flipH) {
        base?.let { EditRenderer.orient(it, params.rotation, params.flipH) }
    }
    val image = remember(oriented) { oriented?.asImageBitmap() }
    val colorFilter = remember(params.values, params.filter) {
        if (params.hasColorChanges) ColorFilter.colorMatrix(ColorMatrix(ColorMath.build(params))) else null
    }

    SystemBarsEffect(darkBackground = true)

    fun cancel() {
        if (params.isUnchanged) onClose()
        else overlay.actionSheet(
            actions = listOf(SheetAction("Annulla modifiche", destructive = true) { onClose() }),
        )
    }

    BackHandler(enabled = isTop && !saving) { cancel() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .plainClickable { }
    ) {
        // Barra superiore.
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Annulla",
                style = IosType.body,
                color = Color.White,
                modifier = Modifier
                    .iosClickable(enabled = !saving) { cancel() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                when (tab) {
                    0 -> adjust.title.uppercase()
                    1 -> "FILTRI"
                    else -> "RITAGLIA"
                },
                style = IosType.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = ToolGray,
            )
            Spacer(Modifier.weight(1f))
            val canSave = !params.isUnchanged && !saving && base != null
            Text(
                "Fine",
                style = IosType.headline,
                color = if (canSave) EditYellow else EditYellow.copy(alpha = 0.35f),
                modifier = Modifier
                    .iosClickable(enabled = canSave) {
                        vm.exportEdit(item, params) { ok -> if (ok) onClose() }
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }

        // Area immagine.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (image == null) {
                CircularProgressIndicator(color = Color.White)
            } else if (tab == 2) {
                CropEditor(
                    image = image,
                    crop = params.crop,
                    ratio = aspectRatio,
                    colorFilter = colorFilter,
                    onCropChange = { params = params.copy(crop = it) },
                )
            } else {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                showOriginal = true
                                tryAwaitRelease()
                                showOriginal = false
                            })
                        }
                ) {
                    drawCropped(image, params.crop, if (showOriginal) null else colorFilter,
                        if (showOriginal) 0f else params[Adjust.VIGNETTE])
                }
                if (showOriginal) {
                    Text(
                        "ORIGINALE",
                        style = IosType.caption.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .clip(RoundedCornerShape(6.dp))
                            .background(EditYellow)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }

        // Strumenti.
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            when (tab) {
                0 -> AdjustTools(
                    params = params,
                    selected = adjust,
                    autoOn = autoOn,
                    onSelect = { adjust = it },
                    onAuto = {
                        autoOn = !autoOn
                        params = if (autoOn) {
                            params.with(Adjust.EXPOSURE, 0.08f).with(Adjust.CONTRAST, 0.15f)
                                .with(Adjust.SATURATION, 0.18f).with(Adjust.WARMTH, 0.05f)
                        } else {
                            params.copy(values = emptyMap())
                        }
                    },
                    onChange = { params = params.with(adjust, it) },
                )
                1 -> FilterTools(item = item, params = params, onSelect = { params = params.copy(filter = it) })
                else -> CropTools(
                    imageSize = oriented?.let { Size(it.width.toFloat(), it.height.toFloat()) },
                    ratio = aspectRatio,
                    onRotate = {
                        params = params.copy(rotation = (params.rotation + 270) % 360, crop = CropRect())
                        aspectRatio = null
                    },
                    onFlip = {
                        val c = params.crop
                        params = params.copy(flipH = !params.flipH, crop = CropRect(1f - c.right, c.top, 1f - c.left, c.bottom))
                    },
                    onAspect = { ratio, crop ->
                        aspectRatio = ratio
                        if (crop != null) params = params.copy(crop = crop)
                    },
                    onReset = {
                        params = params.copy(rotation = 0, flipH = false, crop = CropRect())
                        aspectRatio = null
                    },
                )
            }
        }

        // Schede: Regola · Filtri · Ritaglia.
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                Triple("Regola", Icons.Rounded.Tune, 0),
                Triple("Filtri", Icons.Rounded.PhotoFilter, 1),
                Triple("Ritaglia", Icons.Rounded.Crop, 2),
            ).forEach { (label, icon, index) ->
                val active = tab == index
                Column(
                    Modifier
                        .iosClickable { tab = index }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(icon, label, tint = if (active) EditYellow else ToolGray, modifier = Modifier.size(26.dp))
                    Text(label, style = IosType.caption2, color = if (active) EditYellow else ToolGray)
                }
            }
        }
    }

    if (saving) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .plainClickable { },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text("Salvataggio…", style = IosType.subhead, color = Color.White)
            }
        }
    }
}

/** Rettangolo in cui disegnare un contenuto con il rapporto indicato, centrato. */
private fun fit(container: Size, aspect: Float): Rect {
    val ca = container.width / container.height
    val size = if (aspect > ca) Size(container.width, container.width / aspect)
    else Size(container.height * aspect, container.height)
    return Rect(
        Offset((container.width - size.width) / 2f, (container.height - size.height) / 2f),
        size,
    )
}

private fun DrawScope.drawCropped(image: ImageBitmap, crop: CropRect, colorFilter: ColorFilter?, vignette: Float) {
    val src = EditRenderer.cropRect(image.width, image.height, crop)
    val dst = fit(size, src.width().toFloat() / src.height())
    drawImage(
        image = image,
        srcOffset = IntOffset(src.left, src.top),
        srcSize = IntSize(src.width(), src.height()),
        dstOffset = IntOffset(dst.left.roundToInt(), dst.top.roundToInt()),
        dstSize = IntSize(dst.width.roundToInt(), dst.height.roundToInt()),
        colorFilter = colorFilter,
    )
    if (vignette > 0f) {
        val radius = hypot(dst.width, dst.height) / 2f
        drawRect(
            brush = Brush.radialGradient(
                0f to Color.Transparent,
                0.45f to Color.Transparent,
                1f to Color.Black.copy(alpha = vignette * 0.8f),
                center = dst.center,
                radius = radius,
            ),
            topLeft = dst.topLeft,
            size = dst.size,
        )
    }
}

private enum class Handle { NONE, TL, TR, BL, BR, MOVE }

@Composable
private fun CropEditor(
    image: ImageBitmap,
    crop: CropRect,
    ratio: Float?,
    colorFilter: ColorFilter?,
    onCropChange: (CropRect) -> Unit,
) {
    val currentCrop by rememberUpdatedState(crop)
    val currentRatio by rememberUpdatedState(ratio)
    val currentOnChange by rememberUpdatedState(onCropChange)
    val imgW = image.width.toFloat()
    val imgH = image.height.toFloat()

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(image) {
                var handle = Handle.NONE
                var working = currentCrop
                val touch = 36.dp.toPx()
                detectDragGestures(
                    onDragStart = { pos ->
                        val area = fit(Size(size.width.toFloat(), size.height.toFloat()), imgW / imgH)
                        working = currentCrop
                        val l = area.left + working.left * area.width
                        val t = area.top + working.top * area.height
                        val r = area.left + working.right * area.width
                        val b = area.top + working.bottom * area.height
                        fun near(x: Float, y: Float) = abs(pos.x - x) < touch && abs(pos.y - y) < touch
                        handle = when {
                            near(l, t) -> Handle.TL
                            near(r, t) -> Handle.TR
                            near(l, b) -> Handle.BL
                            near(r, b) -> Handle.BR
                            pos.x in l..r && pos.y in t..b -> Handle.MOVE
                            else -> Handle.NONE
                        }
                    },
                    onDragEnd = { handle = Handle.NONE },
                    onDragCancel = { handle = Handle.NONE },
                ) { change, drag ->
                    if (handle == Handle.NONE) return@detectDragGestures
                    change.consume()
                    val area = fit(Size(size.width.toFloat(), size.height.toFloat()), imgW / imgH)
                    val dx = drag.x / area.width
                    val dy = drag.y / area.height
                    working = moveCrop(working, handle, dx, dy, currentRatio, imgW, imgH)
                    currentOnChange(working)
                }
            }
    ) {
        val area = fit(size, imgW / imgH)
        drawImage(
            image = image,
            dstOffset = IntOffset(area.left.roundToInt(), area.top.roundToInt()),
            dstSize = IntSize(area.width.roundToInt(), area.height.roundToInt()),
            colorFilter = colorFilter,
        )
        val r = Rect(
            area.left + crop.left * area.width,
            area.top + crop.top * area.height,
            area.left + crop.right * area.width,
            area.top + crop.bottom * area.height,
        )
        val dim = Color.Black.copy(alpha = 0.6f)
        drawRect(dim, topLeft = Offset(area.left, area.top), size = Size(area.width, r.top - area.top))
        drawRect(dim, topLeft = Offset(area.left, r.bottom), size = Size(area.width, area.bottom - r.bottom))
        drawRect(dim, topLeft = Offset(area.left, r.top), size = Size(r.left - area.left, r.height))
        drawRect(dim, topLeft = Offset(r.right, r.top), size = Size(area.right - r.right, r.height))
        // Griglia dei terzi.
        val grid = Color.White.copy(alpha = 0.45f)
        for (i in 1..2) {
            val x = r.left + r.width * i / 3f
            val y = r.top + r.height * i / 3f
            drawLine(grid, Offset(x, r.top), Offset(x, r.bottom), strokeWidth = 1f)
            drawLine(grid, Offset(r.left, y), Offset(r.right, y), strokeWidth = 1f)
        }
        drawRect(Color.White, topLeft = r.topLeft, size = r.size, style = Stroke(width = 1.5.dp.toPx()))
        // Angoli spessi.
        val len = 20.dp.toPx()
        val w = 3.5.dp.toPx()
        fun corner(x: Float, y: Float, sx: Float, sy: Float) {
            drawLine(Color.White, Offset(x, y), Offset(x + sx * len, y), strokeWidth = w)
            drawLine(Color.White, Offset(x, y), Offset(x, y + sy * len), strokeWidth = w)
        }
        corner(r.left, r.top, 1f, 1f)
        corner(r.right, r.top, -1f, 1f)
        corner(r.left, r.bottom, 1f, -1f)
        corner(r.right, r.bottom, -1f, -1f)
    }
}

private fun moveCrop(c: CropRect, handle: Handle, dx: Float, dy: Float, ratio: Float?, imgW: Float, imgH: Float): CropRect {
    val minSize = 0.08f
    var l = c.left
    var t = c.top
    var r = c.right
    var b = c.bottom
    when (handle) {
        Handle.MOVE -> {
            val w = r - l
            val h = b - t
            l = (l + dx).coerceIn(0f, 1f - w)
            t = (t + dy).coerceIn(0f, 1f - h)
            return CropRect(l, t, l + w, t + h)
        }
        Handle.TL -> { l += dx; t += dy }
        Handle.TR -> { r += dx; t += dy }
        Handle.BL -> { l += dx; b += dy }
        Handle.BR -> { r += dx; b += dy }
        Handle.NONE -> return c
    }
    l = l.coerceIn(0f, c.right - minSize)
    r = r.coerceIn(c.left + minSize, 1f)
    t = t.coerceIn(0f, c.bottom - minSize)
    b = b.coerceIn(c.top + minSize, 1f)
    if (ratio != null) {
        // Altezza normalizzata richiesta per mantenere il rapporto.
        var w = r - l
        var h = w * imgW / (imgH * ratio)
        val maxH = if (handle == Handle.TL || handle == Handle.TR) b else 1f - t
        if (h > maxH) {
            h = maxH
            w = h * imgH * ratio / imgW
            if (handle == Handle.TL || handle == Handle.BL) l = r - w else r = l + w
        }
        if (handle == Handle.TL || handle == Handle.TR) t = b - h else b = t + h
    }
    return CropRect(l, t, r, b)
}

/** Ritaglio centrato più grande possibile con il rapporto indicato. */
private fun centeredCrop(ratio: Float, imgW: Float, imgH: Float): CropRect {
    var w = 1f
    var h = imgW / (imgH * ratio)
    if (h > 1f) {
        h = 1f
        w = imgH * ratio / imgW
    }
    val l = (1f - w) / 2f
    val t = (1f - h) / 2f
    return CropRect(l, t, l + w, t + h)
}

@Composable
private fun CropTools(
    imageSize: Size?,
    ratio: Float?,
    onRotate: () -> Unit,
    onFlip: () -> Unit,
    onAspect: (Float?, CropRect?) -> Unit,
    onReset: () -> Unit,
) {
    val options = remember(imageSize) {
        listOf(
            AspectOption("Libero", null),
            AspectOption("Originale", imageSize?.let { it.width / it.height }),
            AspectOption("Quadrato", 1f),
            AspectOption("16:9", 16f / 9f),
            AspectOption("9:16", 9f / 16f),
            AspectOption("4:3", 4f / 3f),
            AspectOption("3:4", 3f / 4f),
            AspectOption("4:5", 4f / 5f),
        )
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(Icons.Rounded.RotateLeft, "Ruota", onRotate)
            Spacer(Modifier.width(8.dp))
            ToolButton(Icons.Rounded.Flip, "Rifletti", onFlip)
            Spacer(Modifier.weight(1f))
            Text(
                "RIPRISTINA",
                style = IosType.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = EditYellow,
                modifier = Modifier
                    .iosClickable(onClick = onReset)
                    .padding(8.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(options, key = { it.label }) { option ->
                val selected = option.ratio == ratio || (option.ratio == null && ratio == null)
                Text(
                    option.label,
                    style = IosType.footnote.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) Color.Black else Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) EditYellow else Color(0xFF2C2C2E))
                        .iosClickable {
                            val r = option.ratio
                            val crop = if (r != null && imageSize != null) centeredCrop(r, imageSize.width, imageSize.height) else null
                            onAspect(r, crop)
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0xFF2C2C2E))
            .iosClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun AdjustTools(
    params: EditParams,
    selected: Adjust,
    autoOn: Boolean,
    onSelect: (Adjust) -> Unit,
    onAuto: () -> Unit,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        val value = params[selected]
        Text(
            (value * 100).roundToInt().toString(),
            style = IosType.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = if (value != 0f) EditYellow else ToolGray,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                AdjustCircle(Icons.Rounded.AutoFixHigh, if (autoOn) 1f else 0f, autoOn, false, onAuto)
            }
            items(Adjust.entries.toList(), key = { it.name }) { a ->
                AdjustCircle(adjustIcon(a), params[a] / max(abs(a.min), abs(a.max)), a == selected, params[a] != 0f) { onSelect(a) }
            }
        }
        Spacer(Modifier.height(10.dp))
        RulerSlider(
            value = value,
            min = selected.min,
            max = selected.max,
            onChange = onChange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AdjustCircle(icon: ImageVector, fraction: Float, selected: Boolean, changed: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .iosClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 2.dp.toPx()
            drawCircle(
                color = if (selected) Color.White.copy(alpha = 0.25f) else Color(0xFF2C2C2E),
                radius = size.minDimension / 2f - stroke,
            )
            if (fraction != 0f) {
                drawArc(
                    color = EditYellow,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction.coerceIn(-1f, 1f),
                    useCenter = false,
                    topLeft = Offset(stroke, stroke),
                    size = Size(size.width - 2 * stroke, size.height - 2 * stroke),
                    style = Stroke(width = stroke),
                )
            }
        }
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) Color.White else if (changed) EditYellow else ToolGray,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Righello orizzontale in stile iOS: si trascina sotto l'indicatore centrale. */
@Composable
private fun RulerSlider(
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onChange)
    val ticks = 50
    Canvas(
        modifier
            .height(44.dp)
            .pointerInput(min, max) {
                val spacing = 9.dp.toPx()
                detectHorizontalDragGestures { change, dx ->
                    change.consume()
                    val perPx = (max - min) / (ticks * spacing)
                    var next = (currentValue - dx * perPx).coerceIn(min, max)
                    if (abs(next) < (max - min) * 0.012f) next = 0f
                    currentOnChange(next)
                }
            }
    ) {
        val spacing = 9.dp.toPx()
        val total = ticks * spacing
        val center = size.width / 2f
        val origin = center - (value - min) / (max - min) * total
        for (i in 0..ticks) {
            val x = origin + i * spacing
            if (x < -2f || x > size.width + 2f) continue
            val major = i % 5 == 0
            val fade = 1f - (abs(x - center) / center).coerceIn(0f, 1f) * 0.85f
            drawLine(
                color = Color.White.copy(alpha = (if (major) 0.85f else 0.4f) * fade),
                start = Offset(x, size.height * (if (major) 0.28f else 0.36f)),
                end = Offset(x, size.height * (if (major) 0.72f else 0.64f)),
                strokeWidth = if (major) 1.6f else 1f,
            )
        }
        if (min < 0f) {
            val zeroX = origin + (0f - min) / (max - min) * total
            drawCircle(Color.White, radius = 2.5.dp.toPx(), center = Offset(zeroX, size.height * 0.12f))
        }
        drawRoundRect(
            color = EditYellow,
            topLeft = Offset(center - 1.5.dp.toPx(), size.height * 0.15f),
            size = Size(3.dp.toPx(), size.height * 0.7f),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
    }
}

@Composable
private fun FilterTools(item: MediaItem, params: EditParams, onSelect: (PhotoFilter) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(PhotoFilter.entries.toList(), key = { it.name }) { f ->
                val selected = params.filter == f
                val filterColor = remember(f) {
                    if (f == PhotoFilter.ORIGINAL) null else ColorFilter.colorMatrix(ColorMatrix(ColorMath.filter(f)))
                }
                Column(
                    Modifier
                        .width(76.dp)
                        .iosClickable { onSelect(f) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        f.title.uppercase(),
                        style = IosType.caption2,
                        color = if (selected) EditYellow else ToolGray,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(6.dp))
                    MediaThumbnail(
                        item,
                        Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .then(if (selected) Modifier.border(2.dp, EditYellow, RoundedCornerShape(4.dp)) else Modifier),
                        size = Thumbs.SMALL,
                        colorFilter = filterColor,
                    )
                }
            }
        }
    }
}

@Suppress("unused")
private fun clampUnit(v: Float) = min(1f, max(0f, v))
