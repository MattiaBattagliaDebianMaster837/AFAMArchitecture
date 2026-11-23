package com.afam.pura.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PointF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.SessionConfiguration
import android.location.Location
import android.media.ImageReader
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Range
import android.util.Size
import android.view.Surface
import com.afam.pura.settings.CaptureMode
import com.afam.pura.settings.FlashMode
import com.afam.pura.settings.ImageProfile
import com.afam.pura.settings.Manual
import com.afam.pura.settings.NoiseReduction
import com.afam.pura.settings.PhotoFormat
import com.afam.pura.settings.Resolution
import com.afam.pura.settings.Settings
import com.afam.pura.settings.SettingsStore
import com.afam.pura.settings.Sharpening
import com.afam.pura.settings.ToneCurve
import com.afam.pura.settings.WhiteBalance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

sealed interface CamEvent {
    /** L'otturatore è scattato (per animazione e suono). */
    data object Shutter : CamEvent
    data class Saved(val uri: Uri, val thumb: Bitmap?, val video: Boolean) : CamEvent
    /** Foto per un'altra app (richiesta IMAGE_CAPTURE). */
    data class IntentPhoto(val bytes: ByteArray, val thumb: Bitmap?) : CamEvent
    data class Message(val text: String) : CamEvent
}

data class CamStatus(
    val cameraId: String? = null,
    /** Dimensione (orizzontale) dei fotogrammi dell'anteprima. */
    val previewSize: Size? = null,
    val ready: Boolean = false,
    val error: String? = null,
    val recording: Boolean = false,
    val paused: Boolean = false,
    val busy: Boolean = false,
    val stillSize: Size? = null,
    val raw: Boolean = false,
    val ultraHdr: Boolean = false,
    val maxRes: Boolean = false,
    val videoSize: Size? = null,
    val videoFps: Int = 30,
    /** Serve la luminosità dell'anteprima (esposizione semi-automatica). */
    val needsLuma: Boolean = false,
    val wbCalibrating: Boolean = false,
    val focusPoint: PointF? = null,
    val aeAfLocked: Boolean = false,
)

data class LiveInfo(
    val iso: Int = 0,
    val exposureNs: Long = 0,
    val focus: Float = 0f,
    val afState: Int = CaptureResult.CONTROL_AF_STATE_INACTIVE,
    val zoom: Float = 1f,
    val flashRequired: Boolean = false,
)

/**
 * Tutta la logica Camera2. Gira su un thread dedicato: i metodi pubblici si limitano a
 * inoltrare il lavoro a quel thread, quindi si possono chiamare dall'interfaccia.
 */
class CameraController(
    private val context: Context,
    val catalog: CameraCatalog,
    private val store: SettingsStore,
) {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val thread = HandlerThread("pura-camera", android.os.Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val handler = Handler(thread.looper)
    private val executor = Executor { handler.post(it) }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saver = MediaSaver(context)
    private val recorder = VideoRecorder(context)
    private val sound = MediaActionSound()

    private val _status = MutableStateFlow(CamStatus())
    val status: StateFlow<CamStatus> = _status
    private val _live = MutableStateFlow(LiveInfo())
    val live: StateFlow<LiveInfo> = _live
    private val _events = MutableSharedFlow<CamEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<CamEvent> = _events

    @Volatile var deviceOrientation: Int = 0
    @Volatile var locationProvider: (() -> Location?)? = null
    @Volatile var audioAllowed: () -> Boolean = { false }

    // ---- Stato interno (solo sul thread della fotocamera) ----
    private var settings: Settings = Settings()
    private var forIntent = false
    private var started = false
    private var texture: SurfaceTexture? = null
    private var previewSurface: Surface? = null
    private var device: CameraDevice? = null
    private var opening: String? = null
    private var openRetries = 0
    private var session: CameraCaptureSession? = null
    private var plan: Plan? = null
    private var sessionPlan: Plan? = null
    private var useSafePlan = false
    private var generation = 0
    private var stillReader: ImageReader? = null
    private var rawReader: ImageReader? = null
    private var recorderSurface: Surface? = null
    private var recording = false
    private var recordingUri: Uri? = null
    private var recordingFd: android.os.ParcelFileDescriptor? = null

    private var zoom = 1f
    private var photoEv = 0
    private var focusRegion: MeteringRectangle? = null
    private var focusPoint: PointF? = null
    private var aeAfLocked = false
    private var pendingLock = false

    // Esposizione semi-automatica (priorità ISO o tempi) calcolata dal software.
    private var semiIso = 200
    private var semiExposure = 16_666_666L
    private var lastAutoIso = 0
    private var lastAutoExposure = 0L

    // Calibrazione per i Kelvin.
    private var wbDaylight: Pair<RggbChannelVector, ColorSpaceTransform>? = null
    private var wbCalibrating = false
    private var wbFrames = 0

    // Ultimo stato 3A dall'anteprima.
    private var afState: Int? = null
    private var aeState: Int? = null
    private var lastLivePost = 0L

    // ---- Scatto ----
    private enum class Shot { IDLE, WAIT_AF, WAIT_PRECAPTURE, CAPTURING }
    private var shot = Shot.IDLE
    private var afTriggered = false
    private var precaptureFrames = 0
    private var sawPrecapture = false
    private var afTriggerSeen = false
    // Autofocus continuo che non parte mai (alcuni driver): si passa alla messa a fuoco a comando.
    private var continuousAfBroken = false
    private var afInactiveFrames = 0
    private var pending: PendingShot? = null

    private class PendingShot(
        val baseName: String,
        val wantJpeg: Boolean,
        val wantRaw: Boolean,
        val jpegOrientation: Int,
        val location: Location?,
        val crop: Float?,
        val mirror: Boolean,
        val quality: Int,
        val ultraHdr: Boolean,
        val forIntent: Boolean,
        val spec: CameraSpec,
    ) {
        var jpeg: ByteArray? = null
        var raw: ByteBuffer? = null
        var rawSize: Size? = null
        var result: TotalCaptureResult? = null
        var failed = false
    }

    private data class Plan(
        val cameraId: String,
        val video: Boolean,
        val preview: Size,
        val still: Size?,
        val stillFormat: Int,
        val maxRes: Boolean,
        val crop: Float?,
        val raw: Size?,
        val videoSize: Size?,
        val fps: Int,
        val videoParams: VideoRecorder.Params?,
        val fancy: Boolean,
    )

    init {
        handler.post { runCatching { sound.load(MediaActionSound.SHUTTER_CLICK) } }
    }

    private companion object {
        /** Il fuoco al tocco resta finché non muovi il telefono (o al massimo per 30 s). */
        const val TAP_FOCUS_TIMEOUT = 30_000L
    }

    // =====================================================================================
    // API pubblica
    // =====================================================================================

    fun start(initial: Settings, intentMode: Boolean) = handler.post {
        settings = initial
        forIntent = intentMode
        started = true
        useSafePlan = false
        replan()
    }

    fun stop() = handler.post {
        started = false
        if (recording) stopRecordingInternal()
        closeCamera()
    }

    fun release() {
        handler.post {
            closeCamera()
            recorder.release()
            sound.release()
            thread.quitSafely()
        }
    }

    fun attachTexture(st: SurfaceTexture) = handler.post {
        if (texture !== st) {
            previewSurface?.release()
            previewSurface = null
            texture = st
        }
        if (started) replan(force = sessionPlan == null)
    }

    /** La TextureView ha cambiato dimensione: si rimette la risoluzione giusta e si riconfigura. */
    fun onTextureResized(st: SurfaceTexture) = handler.post {
        if (texture !== st) return@post
        val p = plan ?: return@post
        st.setDefaultBufferSize(p.preview.width, p.preview.height)
        if (session != null && !recording) createSession()
    }

    /** La TextureView è stata distrutta: si chiude la sessione e poi si libera la texture. */
    fun detachTexture(st: SurfaceTexture) = handler.post {
        if (texture === st) {
            closeSession()
            previewSurface?.release()
            previewSurface = null
            texture = null
        }
        // Rilascio ritardato: la fotocamera potrebbe consegnare ancora un fotogramma.
        handler.postDelayed({ runCatching { st.release() } }, 500)
    }

    fun update(s: Settings) = handler.post {
        val old = settings
        settings = s
        if (!started) return@post
        if (old.front != s.front || old.backCameraId != s.backCameraId || old.mode != s.mode) {
            clearFocusInternal(sendCancel = false)
        }
        if (planKey(old) != planKey(s)) useSafePlan = false
        if (old.manual.wb != s.manual.wb && s.manual.wb == WhiteBalance.KELVIN) ensureWbCalibration()
        replan()
    }

    fun setZoom(z: Float) = handler.post {
        val spec = currentSpec() ?: return@post
        // A 50 MP lo scatto usa tutto il sensore principale: lo zoom resta a 1×.
        val nz = if (plan?.maxRes == true) 1f else z.coerceIn(spec.zoomRange.lower, spec.zoomRange.upper)
        if (abs(nz - zoom) < 0.001f) return@post
        zoom = nz
        if (focusRegion != null) clearFocusInternal(sendCancel = true)
        scheduleRepeating()
    }

    fun setPhotoEv(ev: Int) = handler.post {
        photoEv = ev
        scheduleRepeating()
    }

    /** Tocco sull'anteprima: [nx],[ny] tra 0 e 1. [lock] = blocco AE/AF (pressione prolungata). */
    fun focusAt(nx: Float, ny: Float, lock: Boolean) = handler.post { focusAtInternal(nx, ny, lock) }

    fun clearFocus() = handler.post { clearFocusInternal(sendCancel = true) }

    fun takePhoto() = handler.post { startShot() }

    fun startRecording() = handler.post { startRecordingInternal() }
    fun stopRecording() = handler.post { stopRecordingInternal() }
    fun pauseRecording() = handler.post {
        if (!recording) return@post
        recorder.pause()
        _status.update { it.copy(paused = true) }
    }
    fun resumeRecording() = handler.post {
        if (!recording) return@post
        recorder.resume()
        _status.update { it.copy(paused = false) }
    }

    /** Luminosità media dell'anteprima (0..1, codifica sRGB), per l'esposizione semi-automatica. */
    fun onPreviewLuma(mean: Float) = handler.post { semiAutoStep(mean) }

    // =====================================================================================
    // Piano della sessione
    // =====================================================================================

    private fun planKey(s: Settings) = listOf(
        s.front, s.backCameraId, s.mode, s.aspect, s.resolution, s.format, s.videoSize, s.videoFps,
        s.videoCodec, s.videoHighBitrate,
    )

    private fun currentSpec(): CameraSpec? = plan?.let { catalog.spec(it.cameraId) } ?: activeSpec()

    private fun activeSpec(): CameraSpec? {
        val s = settings
        return if (s.front) catalog.mainFront ?: catalog.mainBack
        else catalog.spec(s.backCameraId) ?: catalog.mainBack ?: catalog.mainFront
    }

    private fun manualActive(s: Settings = settings) =
        s.mode == CaptureMode.PRO || (s.mode == CaptureMode.VIDEO && s.videoManual)

    private fun makePlan(s: Settings, spec: CameraSpec, safe: Boolean): Plan {
        if (s.mode == CaptureMode.VIDEO) {
            val wanted = parseSize(s.videoSize)
            val sizes = spec.videoSizes
            var vs = sizes.firstOrNull { it == wanted }
                ?: sizes.firstOrNull { it.width == 1920 && it.height == 1080 }
                ?: sizes.firstOrNull { it.width <= 1920 && it.matches(16f / 9f) }
                ?: sizes.firstOrNull { it.width <= 1920 }
                ?: Size(1280, 720)
            if (safe && vs.width > 1920) vs = sizes.firstOrNull { it.width == 1920 && it.height == 1080 } ?: vs
            var codec = s.videoCodec
            if (!VideoCaps.supports(codec, vs, 30)) codec = com.afam.pura.settings.VideoCodec.H264
            val fps = if (!safe && s.videoFps >= 60 && spec.maxVideoFps(vs) >= 59 &&
                spec.fpsRanges.any { it.upper >= 60 } && VideoCaps.supports(codec, vs, 60)
            ) 60 else 30
            val preview = choosePreview(spec, vs.width.toFloat() / vs.height, fps)
            return Plan(
                cameraId = spec.id, video = true, preview = preview, still = null, stillFormat = ImageFormat.JPEG,
                maxRes = false, crop = null, raw = null, videoSize = vs, fps = fps,
                videoParams = VideoRecorder.Params(vs, fps, codec, s.videoHighBitrate),
                fancy = fps > 30 || vs.width > 1920,
            )
        }
        val aspect = s.aspect.ratio
        var format = ImageFormat.JPEG
        if (!safe && !forIntent && s.format == PhotoFormat.ULTRA_HDR && spec.ultraHdrSizes.isNotEmpty() &&
            Build.VERSION.SDK_INT >= 34
        ) format = ImageFormat.JPEG_R
        var maxRes = false
        var still: Size? = null
        if (format != ImageFormat.JPEG) {
            still = spec.ultraHdrSizes.filter { it.matches(aspect) }.maxByOrNull { it.area }
            if (still == null) format = ImageFormat.JPEG
        }
        if (still == null && !safe && s.resolution == Resolution.MAXIMUM) {
            still = spec.maxResJpegSizes.filter { it.matches(aspect) }.maxByOrNull { it.area }
            if (still != null) maxRes = true
            else still = (spec.highResJpegSizes + spec.jpegSizes).filter { it.matches(aspect) }.maxByOrNull { it.area }
        }
        if (still == null) still = spec.jpegSizes.filter { it.matches(aspect) }.maxByOrNull { it.area }
        var crop: Float? = null
        if (still == null) {
            still = spec.jpegSizes.filter { it.matches(4f / 3f) }.maxByOrNull { it.area }
                ?: spec.jpegSizes.maxByOrNull { it.area } ?: Size(4000, 3000)
            crop = aspect
        }
        val raw = if (!safe && !forIntent && s.format == PhotoFormat.RAW_JPEG && !maxRes) spec.rawSize else null
        val previewAspect = if (crop != null) still.width.toFloat() / still.height else aspect
        val preview = choosePreview(spec, previewAspect, 30)
        return Plan(
            cameraId = spec.id, video = false, preview = preview, still = still, stillFormat = format,
            maxRes = maxRes, crop = crop, raw = raw, videoSize = null, fps = 30, videoParams = null,
            fancy = maxRes || raw != null || format != ImageFormat.JPEG,
        )
    }

    private fun choosePreview(spec: CameraSpec, aspect: Float, fps: Int): Size {
        val sizes = spec.previewSizes.filter { it.height <= 1080 && it.width <= 1920 }
            .filter { fps <= 30 || spec.minFrameDurationFor(it, SurfaceTexture::class.java) <= 1_000_000_000L / fps + 1000 }
        return sizes.filter { it.matches(aspect, 0.03f) }.maxByOrNull { it.area }
            ?: sizes.filter { it.matches(4f / 3f) }.maxByOrNull { it.area }
            ?: sizes.maxByOrNull { it.area }
            ?: spec.previewSizes.lastOrNull()
            ?: Size(1440, 1080)
    }

    private fun parseSize(s: String): Size? {
        val parts = s.split('x')
        if (parts.size != 2) return null
        val w = parts[0].toIntOrNull() ?: return null
        val h = parts[1].toIntOrNull() ?: return null
        return Size(w, h)
    }

    /** Ricalcola il piano: se cambia, riconfigura; altrimenti aggiorna solo la richiesta ripetuta. */
    private fun replan(force: Boolean = false) {
        val spec = activeSpec()
        if (spec == null) {
            _status.update { it.copy(error = "Nessuna fotocamera disponibile") }
            return
        }
        if (recording) {
            // Durante la registrazione non si riconfigura nulla: solo i parametri della richiesta.
            scheduleRepeating()
            return
        }
        val newPlan = makePlan(settings, spec, useSafePlan)
        val changed = newPlan != plan
        plan = newPlan
        if (changed && plan?.cameraId != sessionPlan?.cameraId) {
            zoom = 1f
            loadWbCalibration(spec)
            continuousAfBroken = false
            afInactiveFrames = 0
        }
        if (newPlan.maxRes) zoom = 1f
        publishPlanStatus(newPlan)
        val dev = device
        when {
            dev == null || dev.id != newPlan.cameraId -> openCamera(newPlan.cameraId)
            changed || force || session == null -> createSession()
            else -> scheduleRepeating()
        }
        if (settings.manual.wb == WhiteBalance.KELVIN && manualActive()) ensureWbCalibration()
    }

    private fun publishPlanStatus(p: Plan) {
        _status.update {
            it.copy(
                cameraId = p.cameraId,
                previewSize = p.preview,
                stillSize = p.still,
                raw = p.raw != null,
                ultraHdr = p.stillFormat != ImageFormat.JPEG,
                maxRes = p.maxRes,
                videoSize = p.videoSize,
                videoFps = p.fps,
                needsLuma = semiAutoActive(),
            )
        }
    }

    // =====================================================================================
    // Apertura e sessione
    // =====================================================================================

    @SuppressLint("MissingPermission")
    private fun openCamera(id: String) {
        if (opening == id) return
        closeCamera()
        opening = id
        _status.update { it.copy(ready = false, error = null) }
        val gen = ++generation
        try {
            manager.openCamera(id, executor, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (gen != generation || !started) {
                        camera.close()
                        return
                    }
                    opening = null
                    openRetries = 0
                    device = camera
                    createSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    if (device === camera) {
                        device = null
                        session = null
                        sessionPlan = null
                        _status.update { it.copy(ready = false, error = "Fotocamera in uso da un'altra app") }
                    }
                    if (opening == id) opening = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    if (opening == id) opening = null
                    if (device === camera || device == null) {
                        device = null
                        session = null
                        sessionPlan = null
                        val msg = when (error) {
                            ERROR_CAMERA_IN_USE, ERROR_MAX_CAMERAS_IN_USE -> "Fotocamera in uso da un'altra app"
                            ERROR_CAMERA_DISABLED -> "Fotocamera disattivata dal sistema"
                            else -> "Errore della fotocamera ($error)"
                        }
                        _status.update { it.copy(ready = false, error = msg) }
                        // Un nuovo tentativo dopo un attimo risolve gli errori temporanei.
                        if (error != ERROR_CAMERA_DISABLED && openRetries < 3) {
                            openRetries++
                            handler.postDelayed({ if (started && device == null) replan() }, 1200L * openRetries)
                        }
                    }
                }
            })
        } catch (e: CameraAccessException) {
            opening = null
            _status.update { it.copy(error = "Impossibile aprire la fotocamera") }
        } catch (e: SecurityException) {
            opening = null
            _status.update { it.copy(error = "Permesso fotocamera mancante") }
        } catch (e: IllegalArgumentException) {
            opening = null
            _status.update { it.copy(error = "Fotocamera non trovata") }
        }
    }

    private fun closeSession() {
        shot = Shot.IDLE
        pending = null
        runCatching { session?.close() }
        session = null
        sessionPlan = null
        val readers = listOfNotNull(stillReader, rawReader)
        stillReader = null
        rawReader = null
        // Le ImageReader si chiudono dopo un attimo, quando la sessione ha smesso di usarle.
        if (readers.isNotEmpty()) handler.postDelayed({ readers.forEach { runCatching { it.close() } } }, 400)
        _status.update { it.copy(ready = false, busy = false) }
    }

    private fun closeCamera() {
        generation++
        closeSession()
        runCatching { device?.close() }
        device = null
        opening = null
    }

    private fun createSession() {
        val dev = device ?: return
        val st = texture ?: return
        val p = plan ?: return
        val spec = catalog.spec(p.cameraId) ?: return
        closeSessionKeepDevice()
        val gen = ++generation

        st.setDefaultBufferSize(p.preview.width, p.preview.height)
        val preview = previewSurface ?: Surface(st).also { previewSurface = it }
        val outputs = mutableListOf(OutputConfiguration(preview))

        if (!p.video) {
            val still = p.still!!
            val reader = ImageReader.newInstance(still.width, still.height, p.stillFormat, 3)
            reader.setOnImageAvailableListener({ r -> onStillImage(r) }, handler)
            stillReader = reader
            outputs += OutputConfiguration(reader.surface).apply {
                if (p.maxRes) addSensorPixelModeUsed(CameraMetadata.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION)
            }
            p.raw?.let { size ->
                val rr = ImageReader.newInstance(size.width, size.height, ImageFormat.RAW_SENSOR, 2)
                rr.setOnImageAvailableListener({ r -> onRawImage(r) }, handler)
                rawReader = rr
                outputs += OutputConfiguration(rr.surface)
            }
            recorderSurface = null
        } else {
            val s = runCatching { recorder.surfaceFor(p.videoParams!!) }.getOrNull()
            if (s == null) {
                fallback("Video non supportato con queste impostazioni")
                return
            }
            recorderSurface = s
            outputs += OutputConfiguration(s)
        }

        val callback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                if (gen != generation) {
                    s.close()
                    return
                }
                session = s
                sessionPlan = p
                _status.update { it.copy(ready = true, error = null, needsLuma = semiAutoActive()) }
                startRepeating()
            }

            override fun onConfigureFailed(s: CameraCaptureSession) {
                if (gen != generation) return
                fallback("Configurazione non supportata: uso le impostazioni standard")
            }
        }
        val config = SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, executor, callback)
        runCatching {
            val b = dev.createCaptureRequest(if (p.video) CameraDevice.TEMPLATE_RECORD else CameraDevice.TEMPLATE_PREVIEW)
            applySettings(b, spec, p, still = false)
            config.sessionParameters = b.build()
        }
        if (p.fancy) {
            val supported = try {
                dev.isSessionConfigurationSupported(config)
            } catch (e: Exception) {
                true // il driver non sa rispondere: si prova comunque
            }
            if (!supported) {
                fallback("Combinazione non supportata dal sensore: uso le impostazioni standard")
                return
            }
        }
        try {
            dev.createCaptureSession(config)
        } catch (e: Exception) {
            fallback("Impossibile avviare la fotocamera con queste impostazioni")
        }
    }

    private fun closeSessionKeepDevice() {
        if (session != null || stillReader != null || rawReader != null) closeSession()
    }

    private fun fallback(message: String) {
        if (useSafePlan) {
            _status.update { it.copy(error = "Impossibile avviare l'anteprima") }
            return
        }
        useSafePlan = true
        _events.tryEmit(CamEvent.Message(message))
        val spec = activeSpec() ?: return
        plan = makePlan(settings, spec, safe = true)
        publishPlanStatus(plan!!)
        createSession()
    }

    // =====================================================================================
    // Richieste
    // =====================================================================================

    private var repeatingScheduled = false

    /** Accorpa gli aggiornamenti (es. pizzico per lo zoom) in un'unica richiesta. */
    private fun scheduleRepeating() {
        if (repeatingScheduled) return
        repeatingScheduled = true
        handler.post {
            repeatingScheduled = false
            startRepeating()
        }
    }

    private fun previewBuilder(): CaptureRequest.Builder? {
        val dev = device ?: return null
        val p = sessionPlan ?: return null
        val spec = catalog.spec(p.cameraId) ?: return null
        val b = dev.createCaptureRequest(if (p.video) CameraDevice.TEMPLATE_RECORD else CameraDevice.TEMPLATE_PREVIEW)
        b.addTarget(previewSurface ?: return null)
        if (recording) recorderSurface?.let { b.addTarget(it) }
        applySettings(b, spec, p, still = false)
        return b
    }

    private fun startRepeating() {
        val s = session ?: return
        val b = previewBuilder() ?: return
        try {
            s.setRepeatingRequest(b.build(), previewCallback, handler)
        } catch (e: Exception) {
            // Sessione chiusa nel frattempo: verrà ricreata.
        }
        _status.update { it.copy(needsLuma = semiAutoActive(), wbCalibrating = wbCalibrating) }
    }

    private fun capturePreviewOnce(configure: (CaptureRequest.Builder) -> Unit) {
        val s = session ?: return
        val b = previewBuilder() ?: return
        configure(b)
        runCatching { s.capture(b.build(), previewCallback, handler) }
    }

    private fun semiAutoActive(): Boolean {
        if (!manualActive()) return false
        val spec = currentSpec() ?: return false
        if (!spec.manualSensor) return false
        val m = settings.manual
        return (m.iso == null) != (m.exposureNs == null)
    }

    private fun manualExposure(spec: CameraSpec): Pair<Int, Long>? {
        if (!manualActive() || !spec.manualSensor) return null
        val m = settings.manual
        if (m.iso == null && m.exposureNs == null) return null
        val isoRange = spec.isoRange ?: Range(100, 3200)
        val expRange = spec.exposureRange ?: Range(100_000L, 250_000_000L)
        val iso = (m.iso ?: semiIso).coerceIn(isoRange.lower, isoRange.upper)
        // In video il tempo non può superare la durata di un fotogramma.
        val maxExp = sessionPlan?.takeIf { it.video }?.let { min(expRange.upper, 1_000_000_000L / it.fps) } ?: expRange.upper
        val exp = (m.exposureNs ?: semiExposure).coerceIn(expRange.lower, max(expRange.lower, maxExp))
        return iso to exp
    }

    private fun evSteps(spec: CameraSpec): Int {
        val ev = if (manualActive()) settings.manual.ev else photoEv
        return ev.coerceIn(spec.evRange.lower, spec.evRange.upper)
    }

    /** Applica tutte le impostazioni a una richiesta (anteprima o scatto). */
    private fun applySettings(b: CaptureRequest.Builder, spec: CameraSpec, p: Plan, still: Boolean) {
        val s = settings
        val manual = manualActive()
        val m: Manual = s.manual
        b.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        b.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_DISABLED)
        b.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CameraMetadata.STATISTICS_FACE_DETECT_MODE_OFF)

        // Lo scatto a 50 MP usa la modalità "massima risoluzione" del sensore: niente zoom né aree
        // (sono nelle coordinate della modalità normale) ed esposizione bloccata su quella dell'anteprima.
        val maxResStill = still && p.maxRes
        val region = if (maxResStill) null else focusRegion

        // Zoom
        if (!maxResStill) {
            if (spec.usesZoomRatio) b.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom)
            else if (zoom > 1.001f) b.set(CaptureRequest.SCALER_CROP_REGION, Metering.cropForZoom(spec, zoom))
        }

        // Esposizione e flash
        val manualExp = manualExposure(spec)
        val torch = s.flash == FlashMode.TORCH || (p.video && s.flash == FlashMode.ON)
        if (manualExp != null) {
            b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            b.set(CaptureRequest.SENSOR_SENSITIVITY, manualExp.first)
            b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, manualExp.second)
            val minFrame = if (p.video) 1_000_000_000L / p.fps else 0L
            b.set(CaptureRequest.SENSOR_FRAME_DURATION, max(manualExp.second, minFrame).coerceAtMost(spec.maxFrameDuration))
            b.set(
                CaptureRequest.FLASH_MODE,
                when {
                    !spec.hasFlash -> CameraMetadata.FLASH_MODE_OFF
                    torch -> CameraMetadata.FLASH_MODE_TORCH
                    still && !p.video && (s.flash == FlashMode.ON || s.flash == FlashMode.AUTO) -> CameraMetadata.FLASH_MODE_SINGLE
                    else -> CameraMetadata.FLASH_MODE_OFF
                }
            )
        } else {
            val aeMode = when {
                !spec.hasFlash || p.video || torch || s.flash == FlashMode.OFF -> CameraMetadata.CONTROL_AE_MODE_ON
                s.flash == FlashMode.AUTO && spec.has(spec.aeModes, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH) ->
                    CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH
                s.flash == FlashMode.ON && spec.has(spec.aeModes, CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH) ->
                    CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                else -> CameraMetadata.CONTROL_AE_MODE_ON
            }
            b.set(CaptureRequest.CONTROL_AE_MODE, aeMode)
            b.set(CaptureRequest.FLASH_MODE, if (torch && spec.hasFlash) CameraMetadata.FLASH_MODE_TORCH else CameraMetadata.FLASH_MODE_OFF)
            b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, evSteps(spec))
            b.set(CaptureRequest.CONTROL_AE_LOCK, aeAfLocked || maxResStill)
            if (region != null && spec.maxRegionsAe > 0) b.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(region))
            if (p.video) {
                val range = spec.fpsRanges.firstOrNull { it.lower == p.fps && it.upper == p.fps }
                    ?: spec.fpsRanges.filter { it.upper == p.fps }.maxByOrNull { it.lower }
                range?.let { b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
            }
        }
        b.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_AUTO)

        // Messa a fuoco
        val manualFocus = if (manual) m.focus else null
        when {
            !spec.hasAutoFocus -> {
                if (spec.has(spec.afModes, CameraMetadata.CONTROL_AF_MODE_OFF)) {
                    b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                }
            }
            manualFocus != null -> {
                b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                b.set(CaptureRequest.LENS_FOCUS_DISTANCE, manualFocus.coerceIn(0f, spec.minFocusDiopters))
            }
            focusRegion != null && spec.has(spec.afModes, CameraMetadata.CONTROL_AF_MODE_AUTO) -> {
                b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO)
                if (region != null && spec.maxRegionsAf > 0) b.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(region))
            }
            else -> {
                val wanted = if (p.video) CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO else CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                val mode = if (spec.has(spec.afModes, wanted) && !continuousAfBroken) wanted else CameraMetadata.CONTROL_AF_MODE_AUTO
                b.set(CaptureRequest.CONTROL_AF_MODE, mode)
            }
        }

        // Bilanciamento del bianco
        applyWhiteBalance(b, spec, manual, m, region, maxResStill)

        // Elaborazione d'immagine (nessuna "AI": solo i blocchi standard dell'ISP)
        applyProcessing(b, spec, still)

        // Stabilizzazione
        if (p.video && s.videoStabilization) {
            val mode = if (Build.VERSION.SDK_INT >= 33 &&
                spec.has(spec.videoStabModes, CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION)
            ) CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
            else if (spec.has(spec.videoStabModes, CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON))
                CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
            else CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF
            b.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, mode)
        } else {
            b.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
        }
        val oisOn = s.ois
        val oisMode = if (oisOn) CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON else CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF
        if (spec.has(spec.oisModes, oisMode)) b.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, oisMode)

        if (still) {
            b.set(CaptureRequest.JPEG_QUALITY, s.jpegQuality.coerceIn(50, 100).toByte())
            b.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation(spec))
            currentLocation()?.let { b.set(CaptureRequest.JPEG_GPS_LOCATION, it) }
            if (p.maxRes) b.set(CaptureRequest.SENSOR_PIXEL_MODE, CameraMetadata.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION)
        }
    }

    private fun applyWhiteBalance(
        b: CaptureRequest.Builder,
        spec: CameraSpec,
        manual: Boolean,
        m: Manual,
        region: MeteringRectangle?,
        lock: Boolean,
    ) {
        val wb = if (manual) m.wb else WhiteBalance.AUTO
        fun awb(mode: Int) {
            b.set(CaptureRequest.CONTROL_AWB_MODE, if (spec.has(spec.awbModes, mode)) mode else CameraMetadata.CONTROL_AWB_MODE_AUTO)
        }
        when (wb) {
            WhiteBalance.AUTO -> {
                awb(CameraMetadata.CONTROL_AWB_MODE_AUTO)
                b.set(CaptureRequest.CONTROL_AWB_LOCK, aeAfLocked || lock)
                if (region != null && spec.maxRegionsAwb > 0) b.set(CaptureRequest.CONTROL_AWB_REGIONS, arrayOf(region))
            }
            WhiteBalance.DAYLIGHT -> awb(CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT)
            WhiteBalance.CLOUDY -> awb(CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT)
            WhiteBalance.SHADE -> awb(CameraMetadata.CONTROL_AWB_MODE_SHADE)
            WhiteBalance.INCANDESCENT -> awb(CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT)
            WhiteBalance.FLUORESCENT -> awb(CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT)
            WhiteBalance.KELVIN -> {
                val cal = wbDaylight
                if (cal == null || !spec.manualPostProcessing) {
                    awb(CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT)
                } else {
                    b.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
                    b.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                    b.set(CaptureRequest.COLOR_CORRECTION_GAINS, Kelvin.gains(m.kelvin, cal.first))
                    b.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, cal.second)
                }
            }
        }
    }

    private fun applyProcessing(b: CaptureRequest.Builder, spec: CameraSpec, still: Boolean) {
        val s = settings
        val nr = when (s.profile) {
            ImageProfile.NATURAL -> NoiseReduction.MINIMAL
            ImageProfile.STANDARD -> NoiseReduction.HIGH_QUALITY
            ImageProfile.CUSTOM -> s.noiseReduction
        }
        val edge = when (s.profile) {
            // Senza alcuna nitidezza l'ISP Qualcomm produce immagini che sembrano sfocate.
            ImageProfile.NATURAL -> Sharpening.FAST
            ImageProfile.STANDARD -> Sharpening.HIGH_QUALITY
            ImageProfile.CUSTOM -> s.sharpening
        }
        val tone = if (s.profile == ImageProfile.CUSTOM) s.tone else ToneCurve.STANDARD

        val nrWanted = when (nr) {
            NoiseReduction.OFF -> listOf(CameraMetadata.NOISE_REDUCTION_MODE_OFF, CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL, CameraMetadata.NOISE_REDUCTION_MODE_FAST)
            NoiseReduction.MINIMAL -> listOf(CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL, CameraMetadata.NOISE_REDUCTION_MODE_FAST)
            NoiseReduction.FAST -> listOf(CameraMetadata.NOISE_REDUCTION_MODE_FAST)
            NoiseReduction.HIGH_QUALITY ->
                if (still) listOf(CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY, CameraMetadata.NOISE_REDUCTION_MODE_FAST)
                else listOf(CameraMetadata.NOISE_REDUCTION_MODE_FAST)
        }
        nrWanted.firstOrNull { spec.has(spec.nrModes, it) }?.let { b.set(CaptureRequest.NOISE_REDUCTION_MODE, it) }

        val edgeWanted = when (edge) {
            Sharpening.OFF -> listOf(CameraMetadata.EDGE_MODE_OFF)
            Sharpening.FAST -> listOf(CameraMetadata.EDGE_MODE_FAST)
            Sharpening.HIGH_QUALITY ->
                if (still) listOf(CameraMetadata.EDGE_MODE_HIGH_QUALITY, CameraMetadata.EDGE_MODE_FAST)
                else listOf(CameraMetadata.EDGE_MODE_FAST)
        }
        edgeWanted.firstOrNull { spec.has(spec.edgeModes, it) }?.let { b.set(CaptureRequest.EDGE_MODE, it) }

        when (tone) {
            ToneCurve.STANDARD -> Unit
            ToneCurve.SRGB -> if (spec.has(spec.toneModes, CameraMetadata.TONEMAP_MODE_PRESET_CURVE)) {
                b.set(CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_PRESET_CURVE)
                b.set(CaptureRequest.TONEMAP_PRESET_CURVE, CameraMetadata.TONEMAP_PRESET_CURVE_SRGB)
            }
            ToneCurve.FLAT -> if (spec.has(spec.toneModes, CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE) && spec.maxCurvePoints >= 2) {
                b.set(CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE)
                b.set(CaptureRequest.TONEMAP_CURVE, Curves.flat(min(spec.maxCurvePoints, 32)))
            }
        }

        if (!s.distortionCorrection && spec.has(spec.distortionModes, CameraMetadata.DISTORTION_CORRECTION_MODE_OFF)) {
            b.set(CaptureRequest.DISTORTION_CORRECTION_MODE, CameraMetadata.DISTORTION_CORRECTION_MODE_OFF)
        }
    }

    private fun jpegOrientation(spec: CameraSpec): Int {
        val dev = deviceOrientation
        return if (spec.isFront) (spec.sensorOrientation - dev + 360) % 360
        else (spec.sensorOrientation + dev) % 360
    }

    private fun currentLocation(): Location? = if (settings.geotag) runCatching { locationProvider?.invoke() }.getOrNull() else null

    // =====================================================================================
    // Risultati dell'anteprima: 3A, valori dal vivo, esposizione semi-automatica
    // =====================================================================================

    private val previewCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(s: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            onPreviewResult(request, result)
        }
    }

    private fun onPreviewResult(request: CaptureRequest, result: TotalCaptureResult) {
        afState = result.get(CaptureResult.CONTROL_AF_STATE)
        aeState = result.get(CaptureResult.CONTROL_AE_STATE)
        val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: 0
        val exp = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L
        if (result.get(CaptureResult.CONTROL_AE_MODE) != CameraMetadata.CONTROL_AE_MODE_OFF && iso > 0 && exp > 0) {
            lastAutoIso = iso
            lastAutoExposure = exp
        }

        // Calibrazione Kelvin: legge guadagni e matrice usati con "Luce diurna".
        if (wbCalibrating && result.get(CaptureResult.CONTROL_AWB_MODE) == CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT) {
            wbFrames++
            if (wbFrames >= 5) {
                val gains = result.get(CaptureResult.COLOR_CORRECTION_GAINS)
                val transform = result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM)
                wbCalibrating = false
                if (gains != null && transform != null) {
                    wbDaylight = gains to transform
                    currentSpec()?.let { store.saveWbCalibration(it.id, Kelvin.encode(gains, transform)) }
                } else {
                    wbDaylight = null
                }
                scheduleRepeating()
            }
        }

        if (pendingLock) {
            val st = afState
            if (st == null || st == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                st == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED || st == CaptureResult.CONTROL_AF_STATE_INACTIVE &&
                request.get(CaptureRequest.CONTROL_AF_MODE) == CameraMetadata.CONTROL_AF_MODE_OFF
            ) {
                pendingLock = false
                aeAfLocked = true
                _status.update { it.copy(aeAfLocked = true) }
                scheduleRepeating()
            }
        }

        watchContinuousAf(request)
        processShotState(request, result)

        val now = SystemClock.uptimeMillis()
        if (now - lastLivePost >= 100) {
            lastLivePost = now
            _live.value = LiveInfo(
                iso = iso,
                exposureNs = exp,
                focus = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: 0f,
                afState = afState ?: CaptureResult.CONTROL_AF_STATE_INACTIVE,
                zoom = result.get(CaptureResult.CONTROL_ZOOM_RATIO) ?: zoom,
                flashRequired = aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED,
            )
        }
    }

    private fun watchContinuousAf(request: CaptureRequest) {
        if (continuousAfBroken) return
        val mode = request.get(CaptureRequest.CONTROL_AF_MODE)
        if (mode != CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE && mode != CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO) {
            afInactiveFrames = 0
            return
        }
        if (afState == CaptureResult.CONTROL_AF_STATE_INACTIVE) afInactiveFrames++ else afInactiveFrames = 0
        if (afInactiveFrames >= 60 && shot == Shot.IDLE) {
            // Dopo ~2 s senza alcuna ricerca: modalità AUTO con una messa a fuoco al centro.
            continuousAfBroken = true
            startRepeating()
            capturePreviewOnce { it.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START) }
        }
    }

    private fun semiAutoStep(mean: Float) {
        if (!semiAutoActive()) return
        val spec = currentSpec() ?: return
        val m = settings.manual
        val ev = m.ev * spec.evStep.toFloat()
        val linear = max(mean.toDouble(), 0.004).pow(2.2)
        val target = 0.18 * 2.0.pow(ev.toDouble())
        val ratio = (target / linear).coerceIn(1.0 / 16, 16.0)
        if (abs(ln(ratio) / ln(2.0)) < 0.12) return
        val step = ratio.pow(0.6)
        if (m.iso != null) {
            val range = spec.exposureRange ?: return
            val video = sessionPlan?.takeIf { it.video }
            val cap = if (video != null) min(range.upper, 1_000_000_000L / video.fps) else min(range.upper, 500_000_000L)
            semiExposure = (semiExposure * step).toLong().coerceIn(range.lower, max(range.lower, cap))
        } else {
            val range = spec.isoRange ?: return
            semiIso = (semiIso * step).toInt().coerceIn(range.lower, range.upper)
        }
        scheduleRepeating()
    }

    /** Quando si passa a ISO o tempo manuale, si parte dall'esposizione automatica attuale. */
    private fun seedSemiAuto(old: Manual, new: Manual) {
        val spec = currentSpec() ?: return
        if (lastAutoIso <= 0 || lastAutoExposure <= 0) return
        val product = lastAutoIso.toDouble() * lastAutoExposure
        if (new.iso != null && new.exposureNs == null && (old.iso == null || old.exposureNs != null)) {
            val range = spec.exposureRange ?: return
            semiExposure = (product / new.iso).toLong().coerceIn(range.lower, min(range.upper, 500_000_000L))
        }
        if (new.exposureNs != null && new.iso == null && (old.exposureNs == null || old.iso != null)) {
            val range = spec.isoRange ?: return
            semiIso = (product / new.exposureNs).toInt().coerceIn(range.lower, range.upper)
        }
    }

    fun onManualChanged(old: Manual, new: Manual) = handler.post { seedSemiAuto(old, new) }

    private fun loadWbCalibration(spec: CameraSpec) {
        wbDaylight = store.wbCalibration(spec.id)?.let { Kelvin.decode(it) }
        wbCalibrating = false
    }

    private fun ensureWbCalibration() {
        val spec = currentSpec() ?: return
        if (wbDaylight != null || wbCalibrating || !spec.manualPostProcessing) return
        wbCalibrating = true
        wbFrames = 0
        _status.update { it.copy(wbCalibrating = true) }
        scheduleRepeating()
    }

    // =====================================================================================
    // Messa a fuoco al tocco
    // =====================================================================================

    private val resetFocus = Runnable { clearFocusInternal(sendCancel = true) }

    private fun focusAtInternal(nx: Float, ny: Float, lock: Boolean) {
        val p = sessionPlan ?: return
        val spec = catalog.spec(p.cameraId) ?: return
        if (spec.maxRegionsAf == 0 && spec.maxRegionsAe == 0) return
        if (shot != Shot.IDLE) return // non si cambia fuoco a metà scatto
        handler.removeCallbacks(resetFocus)
        val crop = if (!spec.usesZoomRatio && zoom > 1.001f) Metering.cropForZoom(spec, zoom) else null
        focusRegion = Metering.region(nx, ny, spec, p.preview.width.toFloat() / p.preview.height, crop)
        focusPoint = PointF(nx, ny)
        aeAfLocked = false
        pendingLock = lock
        _status.update { it.copy(focusPoint = PointF(nx, ny), aeAfLocked = false) }
        val manualFocus = manualActive() && settings.manual.focus != null
        if (spec.hasAutoFocus && !manualFocus) {
            capturePreviewOnce { it.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL) }
            startRepeating()
            capturePreviewOnce { it.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START) }
        } else {
            startRepeating()
        }
        if (!lock && !recording) handler.postDelayed(resetFocus, TAP_FOCUS_TIMEOUT)
    }

    private fun clearFocusInternal(sendCancel: Boolean) {
        handler.removeCallbacks(resetFocus)
        if (focusRegion == null && !aeAfLocked && !pendingLock) return
        focusRegion = null
        focusPoint = null
        aeAfLocked = false
        pendingLock = false
        _status.update { it.copy(focusPoint = null, aeAfLocked = false) }
        if (sendCancel && session != null && shot == Shot.IDLE) {
            capturePreviewOnce { it.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL) }
            startRepeating()
        }
    }

    // =====================================================================================
    // Scatto
    // =====================================================================================

    private val shotTimeout = Runnable {
        if (shot == Shot.WAIT_AF || shot == Shot.WAIT_PRECAPTURE) captureStill()
    }

    private fun startShot() {
        val p = sessionPlan ?: return
        if (p.video || shot != Shot.IDLE || session == null) return
        val spec = catalog.spec(p.cameraId) ?: return
        val s = settings
        val manualExp = manualExposure(spec) != null
        val needPrecapture = spec.hasFlash && !manualExp && (s.flash == FlashMode.AUTO || s.flash == FlashMode.ON)
        val manualFocus = manualActive() && s.manual.focus != null
        val af = afState
        // In continuo si blocca sempre il fuoco prima dello scatto: se l'obiettivo è già a fuoco
        // il blocco è immediato, altrimenti si aspetta la fine della ricerca (max 1,5 s).
        val needAf = spec.hasAutoFocus && !manualFocus && when {
            focusRegion != null -> af == CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN ||
                af == CaptureResult.CONTROL_AF_STATE_INACTIVE
            else -> af != CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
        }
        handler.removeCallbacks(resetFocus)
        _status.update { it.copy(busy = true) }
        afTriggered = false
        afTriggerSeen = false
        when {
            needPrecapture -> {
                shot = Shot.WAIT_PRECAPTURE
                precaptureFrames = 0
                sawPrecapture = false
                val triggerAf = needAf && focusRegion == null
                afTriggered = triggerAf
                capturePreviewOnce {
                    it.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START)
                    if (triggerAf) it.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
                }
                handler.postDelayed(shotTimeout, 3000)
            }
            needAf -> {
                shot = Shot.WAIT_AF
                if (focusRegion == null || af == CaptureResult.CONTROL_AF_STATE_INACTIVE) {
                    afTriggered = true
                    capturePreviewOnce { it.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START) }
                } else {
                    afTriggerSeen = true
                }
                handler.postDelayed(shotTimeout, 1500)
            }
            else -> captureStill()
        }
    }

    private fun afDone(): Boolean {
        val af = afState ?: return true
        return af == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
            af == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED ||
            af == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED && !afTriggered
    }

    private fun processShotState(request: CaptureRequest, result: TotalCaptureResult) {
        if (request.get(CaptureRequest.CONTROL_AF_TRIGGER) == CameraMetadata.CONTROL_AF_TRIGGER_START) afTriggerSeen = true
        when (shot) {
            // Si considerano solo i fotogrammi successivi al comando di messa a fuoco.
            Shot.WAIT_AF -> if (afTriggerSeen && afDone()) captureStill()
            Shot.WAIT_PRECAPTURE -> {
                if (request.get(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER) == CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START) {
                    precaptureFrames = 1
                } else if (precaptureFrames > 0) precaptureFrames++
                val ae = aeState
                if (ae == CaptureResult.CONTROL_AE_STATE_PRECAPTURE) sawPrecapture = true
                val aeDone = ae == null || (sawPrecapture && ae != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) ||
                    (!sawPrecapture && precaptureFrames >= 4 && ae != CaptureResult.CONTROL_AE_STATE_SEARCHING)
                if (precaptureFrames > 0 && aeDone && (!afTriggered || (afTriggerSeen && afDone()))) captureStill()
            }
            else -> Unit
        }
    }

    private fun captureStill() {
        handler.removeCallbacks(shotTimeout)
        val s = session
        val dev = device
        val p = sessionPlan
        val spec = p?.let { catalog.spec(it.cameraId) }
        if (s == null || dev == null || p == null || spec == null || p.video) {
            shot = Shot.IDLE
            _status.update { it.copy(busy = false) }
            return
        }
        shot = Shot.CAPTURING
        val orientation = jpegOrientation(spec)
        val shotInfo = PendingShot(
            baseName = saver.newBaseName(),
            wantJpeg = stillReader != null,
            wantRaw = rawReader != null,
            jpegOrientation = orientation,
            location = currentLocation(),
            crop = p.crop,
            mirror = spec.isFront && settings.mirrorFront,
            quality = settings.jpegQuality,
            ultraHdr = p.stillFormat != ImageFormat.JPEG,
            forIntent = forIntent,
            spec = spec,
        )
        pending = shotInfo
        handler.postDelayed({
            if (pending === shotInfo) {
                pending = null
                finishShot()
                _events.tryEmit(CamEvent.Message("Scatto non riuscito"))
            }
        }, 8000)
        try {
            val b = dev.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            stillReader?.let { b.addTarget(it.surface) }
            rawReader?.let { b.addTarget(it.surface) }
            applySettings(b, spec, p, still = true)
            if (p.raw != null) {
                b.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON)
            }
            s.capture(b.build(), stillCallback, handler)
        } catch (e: Exception) {
            pending = null
            finishShot()
            _events.tryEmit(CamEvent.Message("Scatto non riuscito"))
        }
    }

    private val stillCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureStarted(session: CameraCaptureSession, request: CaptureRequest, timestamp: Long, frameNumber: Long) {
            if (settings.shutterSound) runCatching { sound.play(MediaActionSound.SHUTTER_CLICK) }
            _events.tryEmit(CamEvent.Shutter)
        }

        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            val p = pending ?: return
            p.result = result
            tryCompleteShot()
        }

        override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
            val p = pending ?: return
            p.failed = true
            pending = null
            finishShot()
            _events.tryEmit(CamEvent.Message("Scatto non riuscito"))
        }
    }

    private fun onStillImage(reader: ImageReader) {
        val image = runCatching { reader.acquireNextImage() }.getOrNull() ?: return
        try {
            val p = pending ?: return
            if (p.jpeg != null) return
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            p.jpeg = bytes
        } finally {
            image.close()
        }
        tryCompleteShot()
    }

    private fun onRawImage(reader: ImageReader) {
        val image = runCatching { reader.acquireNextImage() }.getOrNull() ?: return
        try {
            val p = pending ?: return
            if (p.raw != null) return
            // Copia compatta dei pixel a 16 bit: l'immagine si libera subito per lo scatto successivo.
            val plane = image.planes[0]
            val w = image.width
            val h = image.height
            val rowBytes = w * 2
            val out = ByteBuffer.allocateDirect(rowBytes * h)
            val src = plane.buffer
            val stride = plane.rowStride
            val row = ByteArray(rowBytes)
            for (y in 0 until h) {
                src.position(y * stride)
                src.get(row, 0, rowBytes)
                out.put(row)
            }
            out.rewind()
            p.raw = out
            p.rawSize = Size(w, h)
        } catch (e: Exception) {
            pending?.wantRawFailed()
        } finally {
            image.close()
        }
        tryCompleteShot()
    }

    private fun PendingShot.wantRawFailed() {
        raw = null
        rawSize = null
    }

    private fun tryCompleteShot() {
        val p = pending ?: return
        if (p.wantJpeg && p.jpeg == null) return
        if (p.wantRaw && (p.raw == null || p.result == null)) return
        if (!p.wantRaw && p.result == null) return
        pending = null
        finishShot()
        saveShot(p)
    }

    private fun finishShot() {
        val p = sessionPlan
        shot = Shot.IDLE
        _status.update { it.copy(busy = false) }
        if (afTriggered && focusRegion == null && !continuousAfBroken) {
            afTriggered = false
            capturePreviewOnce { it.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL) }
            startRepeating()
        }
        afTriggered = false
        if (focusRegion != null && !aeAfLocked && !pendingLock && p != null) handler.postDelayed(resetFocus, TAP_FOCUS_TIMEOUT)
    }

    private fun saveShot(p: PendingShot) {
        io.launch {
            val jpeg = p.jpeg
            if (p.forIntent && jpeg != null) {
                _events.tryEmit(CamEvent.IntentPhoto(jpeg, MediaSaver.thumbnail(jpeg, 1024)))
                return@launch
            }
            var uri: Uri? = null
            var thumb: Bitmap? = null
            if (jpeg != null) {
                uri = saver.saveJpeg(
                    bytes = jpeg,
                    baseName = p.baseName,
                    cropAspect = p.crop,
                    mirror = p.mirror,
                    quality = p.quality,
                )
                thumb = MediaSaver.thumbnail(jpeg)?.let { if (p.mirror) mirrorBitmap(it) else it }
            }
            val raw = p.raw
            val result = p.result
            val rawSize = p.rawSize
            if (raw != null && result != null && rawSize != null) {
                val rawUri = saver.saveDng(
                    chars = p.spec.chars,
                    result = result,
                    raw = raw,
                    size = rawSize,
                    exifOrientation = MediaSaver.exifOrientation(p.jpegOrientation),
                    location = p.location,
                    baseName = p.baseName,
                )
                if (uri == null) uri = rawUri
            }
            if (uri != null) _events.tryEmit(CamEvent.Saved(uri, thumb, video = false))
            else _events.tryEmit(CamEvent.Message("Impossibile salvare la foto"))
        }
    }

    private fun mirrorBitmap(b: Bitmap): Bitmap {
        val m = android.graphics.Matrix().apply { postScale(-1f, 1f) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

    // =====================================================================================
    // Video
    // =====================================================================================

    private fun startRecordingInternal() {
        val p = sessionPlan ?: return
        if (!p.video || recording || session == null) return
        val spec = catalog.spec(p.cameraId) ?: return
        val params = p.videoParams ?: return
        val out = saver.createVideo(saver.newBaseName("VID"))
        if (out == null) {
            _events.tryEmit(CamEvent.Message("Impossibile creare il file video"))
            return
        }
        val (uri, fd) = out
        val orientation = jpegOrientation(spec)
        try {
            recorder.start(params, fd, orientation, currentLocation(), settings.videoAudio && audioAllowed())
        } catch (e: Exception) {
            runCatching { fd.close() }
            saver.delete(uri)
            _events.tryEmit(CamEvent.Message("Impossibile avviare la registrazione"))
            return
        }
        recording = true
        recordingUri = uri
        recordingFd = fd
        handler.removeCallbacks(resetFocus)
        if (settings.shutterSound) runCatching { sound.play(MediaActionSound.START_VIDEO_RECORDING) }
        startRepeating()
        _status.update { it.copy(recording = true, paused = false) }
    }

    private fun stopRecordingInternal() {
        if (!recording) return
        recording = false
        // Prima si toglie il registratore dalla richiesta, poi si chiude il file.
        startRepeating()
        val ok = recorder.stop()
        runCatching { recordingFd?.close() }
        recordingFd = null
        val uri = recordingUri
        recordingUri = null
        if (settings.shutterSound) runCatching { sound.play(MediaActionSound.STOP_VIDEO_RECORDING) }
        _status.update { it.copy(recording = false, paused = false) }
        if (uri != null) {
            if (ok) {
                saver.publish(uri)
                io.launch {
                    val thumb = runCatching { context.contentResolver.loadThumbnail(uri, Size(256, 256), null) }.getOrNull()
                    _events.tryEmit(CamEvent.Saved(uri, thumb, video = true))
                }
            } else {
                saver.delete(uri)
                _events.tryEmit(CamEvent.Message("Video troppo breve"))
            }
        }
        // Le impostazioni cambiate durante la registrazione si applicano ora.
        replan()
    }
}
