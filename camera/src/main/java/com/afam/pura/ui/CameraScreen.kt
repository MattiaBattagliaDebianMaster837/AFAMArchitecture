package com.afam.pura.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Timer10
import androidx.compose.material.icons.filled.Timer3
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.afam.pura.camera.CameraCatalog
import com.afam.pura.camera.LensStop
import com.afam.pura.camera.Stops
import com.afam.pura.settings.Aspect
import com.afam.pura.settings.CaptureMode
import com.afam.pura.settings.FlashMode
import com.afam.pura.settings.Timer
import com.afam.pura.ui.theme.Pura
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Schermata della fotocamera. [viewfinder] disegna l'anteprima reale (o un'immagine finta nei test).
 */
@Composable
fun CameraLayout(
    state: UiState,
    actions: CameraActions,
    message: String?,
    shutterBlink: Int,
    viewfinder: @Composable (PreviewGeometry) -> Unit,
) {
    val s = state.settings
    var showSettings by remember { mutableStateOf(false) }
    var proParam by remember { mutableStateOf<ProParam?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    var hintKey by remember { mutableStateOf(0) }
    val recording = state.status.recording

    LaunchedEffect(hintKey) {
        if (hint != null) {
            delay(1600)
            hint = null
        }
    }
    fun showHint(text: String) {
        hint = text
        hintKey++
    }

    // Rotazione delle icone verso il lato più corto, sempre per la via più breve.
    var rotTarget by remember { mutableFloatStateOf(-state.orientation.toFloat()) }
    LaunchedEffect(state.orientation) {
        val desired = -state.orientation.toFloat()
        var delta = (desired - rotTarget) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        rotTarget += delta
    }
    val rot by animateFloatAsState(rotTarget, tween(280), label = "rot")

    val density = LocalDensity.current
    val topInset = max(
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
        WindowInsets.displayCutout.asPaddingValues().calculateTopPadding(),
    )
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val w = maxWidth
        val h = maxHeight
        val video = s.mode == CaptureMode.VIDEO
        // Proporzioni dell'anteprima in verticale (altezza / larghezza).
        val previewRatio = state.status.previewSize?.let { it.width.toFloat() / it.height }
            ?: if (video) 16f / 9f else s.aspect.ratio
        val previewH = w * previewRatio
        val topBarH = topInset + 52.dp
        val bottomH = 186.dp + bottomInset
        val previewTop = if (topBarH + previewH <= h - bottomH) topBarH else ((h - previewH) / 2).coerceAtLeast(0.dp)
        // Area effettivamente salvata (es. 1:1 dentro un'anteprima 4:3).
        val captureRatio = if (video) previewRatio else s.aspect.ratio
        val captureH = if (abs(captureRatio - previewRatio) > 0.02f) w * captureRatio else previewH
        val captureTop = previewTop + (previewH - captureH) / 2

        // ---- Anteprima e gesti ----
        // La TextureView occupa sempre tutto lo schermo (dimensione fissa): così Android non
        // reimposta mai la risoluzione del flusso, che resta quella scelta per il sensore.
        val corner = if (previewTop > topInset) 14.dp else 0.dp
        viewfinder(PreviewGeometry(top = previewTop, height = previewH, width = w, corner = corner))
        val haptic = LocalHapticFeedback.current
        val currentState by rememberUpdatedState(state)
        val evPerPx = with(density) { 1f / 22.dp.toPx() }
        val swipeThreshold = with(density) { 70.dp.toPx() }
        Box(
            Modifier
                .offset(y = previewTop)
                .size(w, previewH)
                .clip(RoundedCornerShape(corner))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { o -> actions.focusAt(o.x / size.width, o.y / size.height, false) },
                        onLongPress = { o ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            actions.focusAt(o.x / size.width, o.y / size.height, true)
                        },
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var pinching = false
                        var axis = 0
                        var pan = Offset.Zero
                        var evAcc = 0f
                        val startEv = if (currentState.manualActive) currentState.manual.ev else currentState.photoEv
                        var lastEv = startEv
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.isConsumed }) break
                            val pressed = event.changes.count { it.pressed }
                            if (pressed >= 2) {
                                pinching = true
                                val z = event.calculateZoom()
                                if (z != 1f) actions.setZoom(currentState.zoom * z)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (!pinching) {
                                val p = event.calculatePan()
                                pan += p
                                if (axis == 0 && pan.getDistance() > viewConfiguration.touchSlop) {
                                    axis = if (abs(pan.x) > abs(pan.y)) 1 else 2
                                }
                                if (axis == 2 && currentState.status.focusPoint != null) {
                                    evAcc -= p.y * evPerPx
                                    val cam = currentState.camera
                                    val ev = (startEv + evAcc.roundToInt()).coerceIn(cam.evMin, cam.evMax)
                                    if (ev != lastEv) {
                                        lastEv = ev
                                        actions.setEv(ev)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                                if (axis != 0) event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        if (!pinching && axis == 1 && abs(pan.x) > swipeThreshold && !currentState.status.recording) {
                            val modes = CaptureMode.entries
                            val i = modes.indexOf(currentState.settings.mode) + if (pan.x < 0) 1 else -1
                            if (i in modes.indices) actions.setMode(modes[i])
                        }
                    }
                },
        ) {
            // Nasconde l'anteprima mentre la sessione si riconfigura.
            val black by animateFloatAsState(if (state.status.ready) 0f else 1f, tween(if (state.status.ready) 220 else 60), label = "black")
            if (black > 0.01f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = black)))

            val blink = remember { Animatable(0f) }
            LaunchedEffect(shutterBlink) {
                if (shutterBlink > 0) {
                    blink.snapTo(0.85f)
                    blink.animateTo(0f, tween(220))
                }
            }
            if (blink.value > 0.01f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = blink.value)))

            // Maschera per le proporzioni scattate dentro l'anteprima.
            if (captureH < previewH - 1.dp) {
                val band = (previewH - captureH) / 2
                Box(Modifier.fillMaxWidth().height(band).background(Color.Black.copy(alpha = 0.72f)).align(Alignment.TopCenter))
                Box(Modifier.fillMaxWidth().height(band).background(Color.Black.copy(alpha = 0.72f)).align(Alignment.BottomCenter))
            }
            Box(Modifier.offset(y = (previewH - captureH) / 2).size(w, captureH)) {
                GridOverlay(s.grid, Modifier.fillMaxSize())
            }
            FocusOverlay(state, Modifier.fillMaxSize())
            if (s.level) LevelOverlay(Modifier.align(Alignment.Center))
            if (s.histogram) {
                state.histogram?.let { Histogram(it, Modifier.align(Alignment.TopStart).padding(10.dp).rotate(0f)) }
            }
            if (state.countdown > 0) {
                Text(
                    "${state.countdown}",
                    color = Color.White,
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.align(Alignment.Center).rotate(rot),
                )
            }
            if (recording) {
                RecordingBadge(state.status.paused, Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
            }
            state.status.error?.let { err ->
                Text(
                    err,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            }
        }

        // ---- Barra superiore ----
        TopBar(
            state = state,
            rotation = rot,
            onFlash = {
                actions.cycleFlash()
                val next = nextFlashLabel(state)
                if (next != null) showHint(next)
            },
            onTimer = {
                val t = Timer.entries[(s.timer.ordinal + 1) % Timer.entries.size]
                actions.update { it.copy(timer = t) }
                showHint(if (t == Timer.OFF) "Timer disattivato" else "Timer ${t.label}")
            },
            onAspect = {
                val a = Aspect.entries[(s.aspect.ordinal + 1) % Aspect.entries.size]
                actions.update { it.copy(aspect = a) }
            },
            onSettings = { showSettings = true },
            onVideoManual = {
                val on = !s.videoManual
                actions.update { it.copy(videoManual = on) }
                showHint(if (on) "Comandi manuali attivi" else "Video automatico")
            },
            onClose = if (state.intentMode) ({ actions.close() }) else null,
            modifier = Modifier.padding(top = topInset).height(52.dp).fillMaxWidth(),
        )

        // ---- Comandi in basso ----
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = bottomInset),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(hint != null || message != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    hint ?: message ?: "",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .background(Pura.Panel, RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
            if (state.stops.isNotEmpty() && !state.status.maxRes) {
                ZoomPills(state.stops, state.zoom, state.status.cameraId, rot, onSelect = actions::selectLens)
                Spacer(Modifier.height(10.dp))
            }
            AnimatedVisibility(
                visible = state.manualActive,
                enter = fadeIn() + slideInVertically { it / 3 },
                exit = fadeOut() + slideOutVertically { it / 3 },
            ) {
                ProPanel(
                    state = state,
                    selected = proParam,
                    onSelect = { proParam = if (proParam == it) null else it },
                    actions = actions,
                    rotation = rot,
                )
            }
            ModeSelector(
                mode = s.mode,
                enabled = !recording && !state.intentMode,
                onMode = actions::setMode,
                modifier = Modifier.padding(top = 6.dp),
            )
            ShutterRow(state, rot, actions)
        }

        AnimatedVisibility(showSettings, enter = fadeIn() + slideInVertically { it / 8 }, exit = fadeOut()) {
            SettingsScreen(
                state = state,
                actions = actions,
                onClose = { showSettings = false },
            )
        }
        if (showSettings) BackHandler { showSettings = false }

        state.intentPreview?.let { preview ->
            IntentReview(preview, onAccept = actions::intentAccept, onRetake = actions::intentRetake)
        }
    }
}

/** Posizione dell'anteprima sullo schermo. */
data class PreviewGeometry(val top: Dp, val height: Dp, val width: Dp, val corner: Dp)

private fun nextFlashLabel(state: UiState): String? {
    if (!state.camera.hasFlash) return null
    val video = state.settings.mode == CaptureMode.VIDEO
    val order = if (video) listOf(FlashMode.OFF, FlashMode.TORCH) else FlashMode.entries
    val next = order[(order.indexOf(state.settings.flash).coerceAtLeast(0) + 1) % order.size]
    return when (next) {
        FlashMode.OFF -> "Flash disattivato"
        FlashMode.AUTO -> "Flash automatico"
        FlashMode.ON -> "Flash attivo"
        FlashMode.TORCH -> "Torcia accesa"
    }
}

@Composable
private fun TopBar(
    state: UiState,
    rotation: Float,
    onFlash: () -> Unit,
    onTimer: () -> Unit,
    onAspect: () -> Unit,
    onSettings: () -> Unit,
    onVideoManual: () -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier,
) {
    val s = state.settings
    val video = s.mode == CaptureMode.VIDEO
    Row(modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onClose != null) {
            RoundIcon(Icons.Filled.Close, "Chiudi", rotation, onClose)
        }
        if (state.camera.hasFlash) {
            val icon = when (s.flash) {
                FlashMode.OFF -> Icons.Filled.FlashOff
                FlashMode.AUTO -> Icons.Filled.FlashAuto
                FlashMode.ON -> Icons.Filled.FlashOn
                FlashMode.TORCH -> Icons.Filled.FlashlightOn
            }
            RoundIcon(icon, "Flash", rotation, onFlash, tint = if (s.flash == FlashMode.OFF) Color.White else Pura.Yellow)
        }
        if (!video) {
            val icon = when (s.timer) {
                Timer.OFF -> Icons.Filled.TimerOff
                Timer.S3 -> Icons.Filled.Timer3
                Timer.S10 -> Icons.Filled.Timer10
            }
            RoundIcon(icon, "Timer", rotation, onTimer, tint = if (s.timer == Timer.OFF) Color.White else Pura.Yellow)
        }
        Spacer(Modifier.weight(1f))
        // Formato attivo: quello che si salverà davvero.
        val badges = buildList {
            val st = state.status
            if (video) {
                st.videoSize?.let { add(if (it.width >= 3840) "4K" else "${it.height}p") }
                add("${st.videoFps}")
            } else {
                if (st.maxRes) add("${state.camera.maxMp.roundToInt()} MP")
                if (st.raw) add("RAW")
                if (st.ultraHdr) add("HDR")
            }
        }
        badges.forEach { Badge(it, Modifier.padding(horizontal = 2.dp).rotate(rotation)) }
        Spacer(Modifier.weight(1f))
        if (video) {
            Text(
                "M",
                color = if (s.videoManual) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (s.videoManual) Pura.Yellow else Color.Transparent)
                    .clickable(onClick = onVideoManual)
                    .border(1.5.dp, if (s.videoManual) Pura.Yellow else Color.White.copy(alpha = 0.8f), RoundedCornerShape(50))
                    .padding(horizontal = 11.dp, vertical = 4.dp)
                    .rotate(rotation),
            )
            Spacer(Modifier.width(6.dp))
        }
        if (!video) {
            Text(
                s.aspect.label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onAspect)
                    .border(1.5.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .rotate(rotation),
            )
            Spacer(Modifier.width(6.dp))
        }
        RoundIcon(Icons.Filled.Settings, "Impostazioni", rotation, onSettings)
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.Black,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.background(Color.White, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
fun RoundIcon(
    icon: ImageVector,
    description: String,
    rotation: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 44.dp,
    background: Color = Color.Transparent,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(24.dp).rotate(rotation))
    }
}

@Composable
private fun RecordingBadge(paused: Boolean, modifier: Modifier) {
    var start by remember { mutableStateOf(System.currentTimeMillis()) }
    var accumulated by remember { mutableStateOf(0L) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(paused) {
        if (paused) {
            accumulated += System.currentTimeMillis() - start
        } else {
            start = System.currentTimeMillis()
            while (true) {
                now = System.currentTimeMillis()
                delay(250)
            }
        }
    }
    val elapsed = (accumulated + if (paused) 0 else (now - start).coerceAtLeast(0)) / 1000
    val text = "%02d:%02d".format(elapsed / 60, elapsed % 60)
    Row(
        modifier.background(Pura.Panel, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(if (paused) Color.White else Pura.Accent, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ZoomPills(
    stops: List<LensStop>,
    zoom: Float,
    cameraId: String?,
    rotation: Float,
    onSelect: (LensStop) -> Unit,
) {
    // Il pulsante attivo è quello più vicino (per difetto) allo zoom corrente sulla fotocamera attiva.
    val onCamera = stops.filter { it.cameraId == cameraId }
    val activeCam = onCamera.ifEmpty { stops }
    val active = activeCam.lastOrNull { zoom >= it.zoom - 0.05f } ?: activeCam.firstOrNull()
    Row(
        Modifier.background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stops.forEach { stop ->
            val selected = stop == active
            val label = if (selected && stop.cameraId == cameraId && abs(zoom - stop.zoom) > 0.05f) {
                CameraCatalog.formatZoom(zoom * stop.ratio / stop.zoom) + "×"
            } else stop.label + if (selected) "×" else ""
            Box(
                Modifier
                    .size(if (selected) 40.dp else 34.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f))
                    .clickable { onSelect(stop) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) Pura.Yellow else Color.White,
                    fontSize = if (selected) 12.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.rotate(rotation),
                )
            }
        }
    }
}

@Composable
private fun ModeSelector(mode: CaptureMode, enabled: Boolean, onMode: (CaptureMode) -> Unit, modifier: Modifier) {
    Row(modifier.height(36.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        CaptureMode.entries.forEach { m ->
            val selected = m == mode
            Text(
                m.label,
                color = if (selected) Color.Black else Color.White.copy(alpha = if (enabled) 0.85f else 0.4f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Color.White else Color.Transparent)
                    .clickable(enabled = enabled && !selected) { onMode(m) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ShutterRow(state: UiState, rotation: Float, actions: CameraActions) {
    val s = state.settings
    val video = s.mode == CaptureMode.VIDEO
    val recording = state.status.recording
    Row(
        Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Sinistra: ultima foto (o pausa durante la registrazione).
        if (recording) {
            RoundIcon(
                if (state.status.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                "Pausa",
                rotation,
                actions::togglePause,
                size = 52.dp,
                background = Color.White.copy(alpha = 0.15f),
            )
        } else {
            Box(
                Modifier
                    .size(52.dp)
                    .rotate(rotation)
                    .clip(CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(enabled = state.thumb != null && !state.intentMode, onClick = actions::openGallery),
            ) {
                state.thumb?.let {
                    Image(it, contentDescription = "Ultima foto", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }

        ShutterButton(video = video, recording = recording, busy = state.status.busy || state.countdown > 0, onClick = actions::shutter)

        RoundIcon(
            Icons.Filled.Cameraswitch,
            "Cambia fotocamera",
            rotation,
            actions::toggleFront,
            size = 52.dp,
            background = Color.White.copy(alpha = 0.15f),
            modifier = Modifier.alpha(if (recording) 0.3f else 1f),
        )
    }
}

@Composable
private fun ShutterButton(video: Boolean, recording: Boolean, busy: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val inner by animateFloatAsState(if (busy) 0.82f else 1f, tween(120), label = "inner")
    Box(
        Modifier
            .size(80.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable(interactionSource = interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            recording -> Box(Modifier.size(30.dp).background(Pura.Accent, RoundedCornerShape(6.dp)))
            video -> Box(Modifier.size(62.dp).background(Pura.Accent, CircleShape))
            else -> Box(Modifier.size(64.dp * inner).background(Color.White, CircleShape))
        }
    }
}

@Composable
private fun IntentReview(preview: androidx.compose.ui.graphics.ImageBitmap, onAccept: () -> Unit, onRetake: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(enabled = false) {}) {
        Image(preview, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            RoundIcon(Icons.Filled.Refresh, "Riprova", 0f, onRetake, size = 64.dp, background = Color.White.copy(alpha = 0.2f))
            RoundIcon(Icons.Filled.Check, "Usa foto", 0f, onAccept, size = 64.dp, background = Pura.Accent)
        }
    }
}

/** Valore leggibile dell'esposizione in automatico, per i comandi PRO. */
fun liveExposureLabel(ns: Long) = if (ns > 0) Stops.formatShutter(ns) else "--"
