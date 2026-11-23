package com.afam.pura.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afam.pura.CameraViewModel
import com.afam.pura.camera.LensStop
import com.afam.pura.settings.CaptureMode
import com.afam.pura.settings.Manual
import com.afam.pura.settings.Settings
import kotlinx.coroutines.delay

/** Collega il ViewModel allo schermo della fotocamera. */
@Composable
fun CameraScreen(
    vm: CameraViewModel,
    onOpenGallery: () -> Unit,
    onRequestLocation: () -> Unit,
    onIntentAccept: () -> Unit,
    onClose: (() -> Unit)?,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val zoom by vm.zoom.collectAsStateWithLifecycle()
    val photoEv by vm.photoEv.collectAsStateWithLifecycle()
    val thumb by vm.thumb.collectAsStateWithLifecycle()
    val countdown by vm.countdown.collectAsStateWithLifecycle()
    val orientation by vm.orientation.collectAsStateWithLifecycle()
    val histogram by vm.histogram.collectAsStateWithLifecycle()
    val intentShot by vm.intentShot.collectAsStateWithLifecycle()
    val controller by vm.controller.collectAsStateWithLifecycle()

    val spec = remember(controller, status.cameraId, settings.front, settings.backCameraId) { vm.currentSpec() }
    val cameraUi = remember(spec) { spec?.let { CameraUi.from(it) } ?: CameraUi() }
    val stops = remember(controller, settings.front) { vm.lensStops() }
    val info = remember(controller) { controller?.catalog?.let { CameraUi.describe(it) }.orEmpty() }
    val thumbImage = remember(thumb) { thumb?.asImageBitmap() }
    val intentImage = remember(intentShot) { intentShot?.preview?.asImageBitmap() }

    var message by remember { mutableStateOf<String?>(null) }
    var blink by remember { mutableIntStateOf(0) }
    LaunchedEffect(vm) {
        vm.messages.collect {
            message = it
            delay(2500)
            message = null
        }
    }
    LaunchedEffect(vm) { vm.shutterFlash.collect { blink++ } }

    val state = UiState(
        settings = settings,
        status = status,
        live = live,
        camera = cameraUi,
        stops = stops,
        zoom = zoom,
        photoEv = photoEv,
        thumb = thumbImage,
        countdown = countdown,
        orientation = orientation,
        histogram = histogram,
        intentPreview = intentImage,
        intentMode = vm.intentMode,
        cameraInfo = info,
    )

    val actions = remember(vm) {
        object : CameraActions {
            override fun shutter() = vm.shutter()
            override fun setMode(mode: CaptureMode) = vm.setMode(mode)
            override fun toggleFront() = vm.toggleFront()
            override fun cycleFlash() = vm.cycleFlash()
            override fun selectLens(stop: LensStop) = vm.selectLens(stop)
            override fun setZoom(z: Float) = vm.setZoom(z)
            override fun setEv(ev: Int) = vm.setPhotoEv(ev)
            override fun focusAt(x: Float, y: Float, lock: Boolean) { vm.focusAt(x, y, lock) }
            override fun clearFocus() { vm.clearFocus() }
            override fun openGallery() = onOpenGallery()
            override fun togglePause() = vm.togglePause()
            override fun update(transform: (Settings) -> Settings) = vm.updateSettings(transform)
            override fun updateManual(transform: (Manual) -> Manual) = vm.updateManual(transform)
            override fun requestLocation() = onRequestLocation()
            override fun intentAccept() = onIntentAccept()
            override fun intentRetake() = vm.clearIntentShot()
            override fun close() { onClose?.invoke() }
        }
    }

    val manualFocus = state.manualActive && settings.manual.focus != null
    CameraLayout(
        state = state,
        actions = actions,
        message = message,
        shutterBlink = blink,
    ) { geometry ->
        Viewfinder(
            controller = controller,
            geometry = geometry,
            peaking = settings.peaking && manualFocus,
            zebra = settings.zebra && state.manualActive,
            sampleLuma = status.needsLuma,
            wantHistogram = settings.histogram,
            onLuma = { mean, hist -> vm.onPreviewLuma(mean, hist) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
