package com.afam.pura

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.afam.pura.camera.CamEvent
import com.afam.pura.camera.CamStatus
import com.afam.pura.camera.CameraCatalog
import com.afam.pura.camera.CameraController
import com.afam.pura.camera.CameraSpec
import com.afam.pura.camera.LensStop
import com.afam.pura.camera.LiveInfo
import com.afam.pura.settings.CaptureMode
import com.afam.pura.settings.FlashMode
import com.afam.pura.settings.Manual
import com.afam.pura.settings.Settings
import com.afam.pura.settings.SettingsStore
import com.afam.pura.settings.VolumeKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Foto scattata per un'altra app, in attesa di conferma. */
class IntentShot(val bytes: ByteArray, val preview: Bitmap?)

class CameraViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val _settings = MutableStateFlow(store.load())
    private val storedMode = _settings.value.mode
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _controller = MutableStateFlow<CameraController?>(null)
    val controller: StateFlow<CameraController?> = _controller.asStateFlow()

    private val _status = MutableStateFlow(CamStatus())
    val status: StateFlow<CamStatus> = _status.asStateFlow()
    private val _live = MutableStateFlow(LiveInfo())
    val live: StateFlow<LiveInfo> = _live.asStateFlow()

    private val _zoom = MutableStateFlow(1f)
    val zoom: StateFlow<Float> = _zoom.asStateFlow()
    private val _photoEv = MutableStateFlow(0)
    val photoEv: StateFlow<Int> = _photoEv.asStateFlow()

    private val _thumb = MutableStateFlow<Bitmap?>(null)
    val thumb: StateFlow<Bitmap?> = _thumb.asStateFlow()
    var lastUri: Uri? = null
        private set
    var lastIsVideo = false
        private set

    private val _countdown = MutableStateFlow(0)
    val countdown: StateFlow<Int> = _countdown.asStateFlow()
    private val _shutterFlash = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val shutterFlash: SharedFlow<Unit> = _shutterFlash
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages
    private val _histogram = MutableStateFlow<IntArray?>(null)
    val histogram: StateFlow<IntArray?> = _histogram.asStateFlow()
    private val _intentShot = MutableStateFlow<IntentShot?>(null)
    val intentShot: StateFlow<IntentShot?> = _intentShot.asStateFlow()
    private val _savedForIntent = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    /** Video registrato per un'altra app (VIDEO_CAPTURE). */
    val savedForIntent: SharedFlow<Uri> = _savedForIntent

    private val _orientation = MutableStateFlow(0)
    /** Orientamento del telefono (0, 90, 180, 270) per ruotare le icone. */
    val orientation: StateFlow<Int> = _orientation.asStateFlow()

    var intentMode = false
        private set
    var videoIntent = false
        private set
    private var resumed = false
    private var countdownJob: Job? = null
    private val tone by lazy { runCatching { ToneGenerator(AudioManager.STREAM_SYSTEM, 70) }.getOrNull() }

    init {
        // Il catalogo delle fotocamere si legge fuori dal thread principale: l'interfaccia
        // compare subito e la fotocamera si apre appena pronto.
        viewModelScope.launch {
            val catalog = withContext(Dispatchers.Default) {
                CameraCatalog(app.getSystemService(CameraManager::class.java))
            }
            val c = CameraController(app, catalog, store)
            c.locationProvider = { lastLocation }
            c.audioAllowed = { hasPermission(Manifest.permission.RECORD_AUDIO) }
            c.deviceOrientation = _orientation.value
            _controller.value = c
            launch { c.status.collect { _status.value = it } }
            // Il fuoco al tocco resta finché il telefono non si sposta (come nelle fotocamere di sistema).
            launch {
                c.status
                    .map { st -> st.focusPoint?.takeIf { !st.aeAfLocked && !st.recording } }
                    .distinctUntilChanged()
                    .collect { point -> if (point != null) startMotionWatch() else stopMotionWatch() }
            }
            launch { c.live.collect { _live.value = it } }
            launch { c.events.collect { onEvent(it) } }
            if (resumed) startCamera()
            loadLastThumbnail()
        }
    }

    fun configureIntent(capture: Boolean, video: Boolean) {
        intentMode = capture
        videoIntent = video
        if (video) _settings.update { it.copy(mode = CaptureMode.VIDEO) }
        else if (capture && _settings.value.mode == CaptureMode.VIDEO) _settings.update { it.copy(mode = CaptureMode.PHOTO) }
    }

    // ---- Ciclo di vita ----

    fun onResume() {
        resumed = true
        startCamera()
        updateLocationUpdates()
    }

    fun onPause() {
        resumed = false
        countdownJob?.cancel()
        _countdown.value = 0
        _controller.value?.stop()
        stopLocation()
        stopMotionWatch()
    }

    private fun startCamera() {
        val c = _controller.value ?: return
        if (!hasPermission(Manifest.permission.CAMERA)) return
        c.start(_settings.value, intentMode && !videoIntent)
        c.setZoom(_zoom.value)
    }

    fun onPermissionsChanged() {
        if (resumed) startCamera()
        updateLocationUpdates()
    }

    override fun onCleared() {
        stopMotionWatch()
        _controller.value?.release()
        stopLocation()
        runCatching { tone?.release() }
    }

    // ---- Impostazioni ----

    fun updateSettings(transform: (Settings) -> Settings) {
        val old = _settings.value
        val new = transform(old)
        if (new == old) return
        if (old.manual != new.manual) _controller.value?.onManualChanged(old.manual, new.manual)
        _settings.value = new
        // Le richieste di altre app non cambiano la modalità salvata per l'uso normale.
        store.save(if (intentMode) new.copy(mode = storedMode) else new)
        _controller.value?.update(new)
        if (old.geotag != new.geotag) updateLocationUpdates()
        if (old.front != new.front || old.backCameraId != new.backCameraId || old.resolution != new.resolution) {
            _zoom.value = 1f
            _controller.value?.setZoom(1f)
        }
        if (old.mode != new.mode) {
            _photoEv.value = 0
            _controller.value?.setPhotoEv(0)
        }
    }

    fun updateManual(transform: (Manual) -> Manual) = updateSettings { it.copy(manual = transform(it.manual)) }

    fun setMode(mode: CaptureMode) {
        if (_status.value.recording) return
        if (videoIntent && mode != CaptureMode.VIDEO) return
        if (intentMode && !videoIntent && mode == CaptureMode.VIDEO) return
        updateSettings { s ->
            val flash = if (mode == CaptureMode.VIDEO && s.flash != FlashMode.OFF && s.flash != FlashMode.TORCH) FlashMode.OFF else s.flash
            s.copy(mode = mode, flash = flash)
        }
    }

    fun toggleFront() {
        if (_status.value.recording) return
        updateSettings { it.copy(front = !it.front) }
    }

    fun cycleFlash() {
        val s = _settings.value
        val order = if (s.mode == CaptureMode.VIDEO) listOf(FlashMode.OFF, FlashMode.TORCH)
        else listOf(FlashMode.OFF, FlashMode.AUTO, FlashMode.ON, FlashMode.TORCH)
        val i = order.indexOf(s.flash)
        updateSettings { it.copy(flash = order[(i + 1) % order.size]) }
    }

    fun currentSpec(): CameraSpec? {
        val c = _controller.value ?: return null
        val id = _status.value.cameraId
        return c.catalog.spec(id) ?: run {
            val s = _settings.value
            if (s.front) c.catalog.mainFront else c.catalog.spec(s.backCameraId) ?: c.catalog.mainBack
        }
    }

    fun lensStops(): List<LensStop> = _controller.value?.catalog?.lensStops(_settings.value.front).orEmpty()

    fun selectLens(stop: LensStop) {
        val catalog = _controller.value?.catalog ?: return
        val main = catalog.mainBack
        val s = _settings.value
        if (!s.front) {
            val targetId = if (stop.cameraId == main?.id) null else stop.cameraId
            if (targetId != s.backCameraId) {
                if (_status.value.recording) return
                updateSettings { it.copy(backCameraId = targetId) }
            }
        }
        setZoom(stop.zoom)
    }

    fun setZoom(z: Float) {
        if (_status.value.maxRes) {
            _zoom.value = 1f
            return
        }
        val spec = currentSpec()
        val range = spec?.zoomRange
        val v = if (range != null) z.coerceIn(range.lower, range.upper) else z
        _zoom.value = v
        _controller.value?.setZoom(v)
    }

    fun setPhotoEv(ev: Int) {
        if (_settings.value.mode == CaptureMode.PHOTO) {
            _photoEv.value = ev
            _controller.value?.setPhotoEv(ev)
        } else {
            updateManual { it.copy(ev = ev) }
        }
    }

    fun focusAt(nx: Float, ny: Float, lock: Boolean) = _controller.value?.focusAt(nx, ny, lock)
    fun clearFocus() = _controller.value?.clearFocus()

    fun setOrientation(deg: Int) {
        if (_orientation.value == deg) return
        _orientation.value = deg
        _controller.value?.deviceOrientation = deg
    }

    fun onPreviewLuma(mean: Float, histogram: IntArray?) {
        _controller.value?.onPreviewLuma(mean)
        if (histogram != null) _histogram.value = histogram
    }

    // ---- Scatto ----

    fun shutter() {
        val c = _controller.value ?: return
        val s = _settings.value
        val st = _status.value
        if (s.mode == CaptureMode.VIDEO) {
            if (st.recording) c.stopRecording() else c.startRecording()
            return
        }
        if (countdownJob?.isActive == true) {
            countdownJob?.cancel()
            _countdown.value = 0
            return
        }
        if (st.busy || !st.ready) return
        if (s.timer.seconds > 0) {
            countdownJob = viewModelScope.launch {
                for (i in s.timer.seconds downTo 1) {
                    _countdown.value = i
                    if (s.shutterSound) runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, if (i <= 2) 150 else 80) }
                    delay(1000)
                }
                _countdown.value = 0
                c.takePhoto()
            }
        } else {
            c.takePhoto()
        }
    }

    fun togglePause() {
        val c = _controller.value ?: return
        if (!_status.value.recording) return
        if (_status.value.paused) c.resumeRecording() else c.pauseRecording()
    }

    /** @return true se il tasto è stato usato dall'app. */
    fun onVolumeKey(up: Boolean): Boolean {
        return when (_settings.value.volumeKeys) {
            VolumeKeys.VOLUME -> false
            VolumeKeys.SHUTTER -> {
                shutter()
                true
            }
            VolumeKeys.ZOOM -> {
                val spec = currentSpec() ?: return true
                val factor = if (up) 1.25f else 0.8f
                setZoom((_zoom.value * factor).coerceIn(spec.zoomRange.lower, spec.zoomRange.upper))
                true
            }
        }
    }

    fun clearIntentShot() {
        _intentShot.value = null
    }

    private fun onEvent(e: CamEvent) {
        when (e) {
            CamEvent.Shutter -> _shutterFlash.tryEmit(Unit)
            is CamEvent.Saved -> {
                lastUri = e.uri
                lastIsVideo = e.video
                if (e.thumb != null) _thumb.value = e.thumb
                if (e.video && videoIntent) _savedForIntent.tryEmit(e.uri)
            }
            is CamEvent.IntentPhoto -> _intentShot.value = IntentShot(e.bytes, e.thumb)
            is CamEvent.Message -> _messages.tryEmit(e.text)
        }
    }

    /** Miniatura dell'ultima foto fatta con l'app (visibile senza permessi di lettura). */
    private fun loadLastThumbnail() {
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE)
            val selection = "${MediaStore.Files.FileColumns.OWNER_PACKAGE_NAME}=? AND " +
                "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} OR " +
                "${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
            val pkg = getApplication<Application>().packageName
            runCatching {
                resolver.query(collection, projection, selection, arrayOf(pkg), "${MediaStore.Files.FileColumns.DATE_ADDED} DESC")
                    ?.use { c ->
                        if (c.moveToFirst()) {
                            val id = c.getLong(0)
                            val video = c.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                            val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            val uri = ContentUris.withAppendedId(base, id)
                            val bmp = runCatching { resolver.loadThumbnail(uri, Size(256, 256), null) }.getOrNull()
                            if (lastUri == null && bmp != null) {
                                lastUri = uri
                                lastIsVideo = video
                                _thumb.value = bmp
                            }
                        }
                    }
            }
        }
    }

    // ---- Movimento del telefono (per sbloccare il fuoco al tocco) ----

    private var motionListener: SensorEventListener? = null

    private fun startMotionWatch() {
        stopMotionWatch()
        if (!resumed) return
        val sm = getApplication<Application>().getSystemService(SensorManager::class.java) ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return
        val anchor = FloatArray(9)
        val current = FloatArray(9)
        val angles = FloatArray(3)
        var hasAnchor = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (!hasAnchor) {
                    SensorManager.getRotationMatrixFromVector(anchor, e.values)
                    hasAnchor = true
                    return
                }
                SensorManager.getRotationMatrixFromVector(current, e.values)
                SensorManager.getAngleChange(angles, current, anchor)
                if (angles.any { abs(it) > 0.15f }) {
                    stopMotionWatch()
                    clearFocus()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        motionListener = listener
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    private fun stopMotionWatch() {
        val l = motionListener ?: return
        motionListener = null
        getApplication<Application>().getSystemService(SensorManager::class.java)?.unregisterListener(l)
    }

    // ---- Posizione (solo se attivata) ----

    @Volatile private var lastLocation: Location? = null
    private var locationListener: LocationListener? = null

    @SuppressLint("MissingPermission")
    private fun updateLocationUpdates() {
        val want = _settings.value.geotag && resumed &&
            (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION))
        if (!want) {
            stopLocation()
            return
        }
        if (locationListener != null) return
        val lm = getApplication<Application>().getSystemService(LocationManager::class.java) ?: return
        val providers = lm.getProviders(true)
        lastLocation = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        val listener = LocationListener { lastLocation = it }
        locationListener = listener
        for (p in listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
            if (p in providers) {
                runCatching { lm.requestLocationUpdates(p, 10_000L, 10f, listener, Looper.getMainLooper()) }
                break
            }
        }
    }

    private fun stopLocation() {
        val l = locationListener ?: return
        locationListener = null
        val lm = getApplication<Application>().getSystemService(LocationManager::class.java)
        runCatching { lm?.removeUpdates(l) }
    }

    private fun hasPermission(p: String) =
        ContextCompat.checkSelfPermission(getApplication(), p) == PackageManager.PERMISSION_GRANTED
}
