package com.afam.pura.ui

import android.hardware.camera2.CameraMetadata
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import com.afam.pura.camera.CamStatus
import com.afam.pura.camera.CameraCatalog
import com.afam.pura.camera.CameraSpec
import com.afam.pura.camera.LensStop
import com.afam.pura.camera.LiveInfo
import com.afam.pura.camera.VideoCaps
import com.afam.pura.camera.matches
import com.afam.pura.settings.Manual
import com.afam.pura.settings.NoiseReduction
import com.afam.pura.settings.Settings
import com.afam.pura.settings.Sharpening
import com.afam.pura.settings.ToneCurve
import com.afam.pura.settings.VideoCodec
import com.afam.pura.settings.WhiteBalance
import kotlin.math.roundToInt

/** Una risoluzione video proposta nelle impostazioni. */
data class VideoOption(val size: String, val label: String, val fps60: Boolean)

/** Quello che l'interfaccia deve sapere della fotocamera attiva (costruibile anche nei test). */
data class CameraUi(
    val isFront: Boolean = false,
    val hasFlash: Boolean = true,
    val hasAutoFocus: Boolean = true,
    val minFocus: Float = 10f,
    val manualSensor: Boolean = true,
    val manualPostProcessing: Boolean = true,
    val isoMin: Int = 50,
    val isoMax: Int = 6400,
    val expMin: Long = 100_000L,
    val expMax: Long = 1_000_000_000L,
    val evMin: Int = -12,
    val evMax: Int = 12,
    val evStep: Float = 1f / 6f,
    val zoomMin: Float = 1f,
    val zoomMax: Float = 10f,
    val rawCapable: Boolean = true,
    val ultraHdr: Boolean = false,
    val standardMp: Float = 12.5f,
    val maxMp: Float = 50f,
    val noiseModes: Set<NoiseReduction> = NoiseReduction.entries.toSet(),
    val edgeModes: Set<Sharpening> = Sharpening.entries.toSet(),
    val tones: Set<ToneCurve> = ToneCurve.entries.toSet(),
    val distortionOff: Boolean = true,
    val ois: Boolean = true,
    val videoStab: Boolean = true,
    val wbPresets: Set<WhiteBalance> = WhiteBalance.entries.toSet(),
    val videoOptions: List<VideoOption> = listOf(
        VideoOption("3840x2160", "4K", false),
        VideoOption("1920x1080", "1080p", true),
        VideoOption("1280x720", "720p", true),
    ),
    val aperture: Float? = 1.88f,
    val equivalentFocal: Int = 24,
) {
    val hasHigherRes: Boolean get() = maxMp > standardMp * 1.4f

    companion object {
        fun from(spec: CameraSpec): CameraUi {
            val nr = buildSet {
                if (CameraMetadata.NOISE_REDUCTION_MODE_OFF in spec.nrModes) add(NoiseReduction.OFF)
                if (CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL in spec.nrModes) add(NoiseReduction.MINIMAL)
                if (CameraMetadata.NOISE_REDUCTION_MODE_FAST in spec.nrModes) add(NoiseReduction.FAST)
                if (CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY in spec.nrModes) add(NoiseReduction.HIGH_QUALITY)
            }
            val edge = buildSet {
                if (CameraMetadata.EDGE_MODE_OFF in spec.edgeModes) add(Sharpening.OFF)
                if (CameraMetadata.EDGE_MODE_FAST in spec.edgeModes) add(Sharpening.FAST)
                if (CameraMetadata.EDGE_MODE_HIGH_QUALITY in spec.edgeModes) add(Sharpening.HIGH_QUALITY)
            }
            val tones = buildSet {
                add(ToneCurve.STANDARD)
                if (CameraMetadata.TONEMAP_MODE_PRESET_CURVE in spec.toneModes) add(ToneCurve.SRGB)
                if (CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE in spec.toneModes && spec.maxCurvePoints >= 2) add(ToneCurve.FLAT)
            }
            val wb = buildSet {
                add(WhiteBalance.AUTO)
                val m = spec.awbModes
                if (CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT in m) add(WhiteBalance.DAYLIGHT)
                if (CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT in m) add(WhiteBalance.CLOUDY)
                if (CameraMetadata.CONTROL_AWB_MODE_SHADE in m) add(WhiteBalance.SHADE)
                if (CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT in m) add(WhiteBalance.INCANDESCENT)
                if (CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT in m) add(WhiteBalance.FLUORESCENT)
                if (spec.manualPostProcessing && CameraMetadata.CONTROL_AWB_MODE_OFF in m) add(WhiteBalance.KELVIN)
            }
            val video = listOf(Size(3840, 2160) to "4K", Size(1920, 1080) to "1080p", Size(1280, 720) to "720p")
                .filter { (size, _) -> spec.videoSizes.any { it == size } }
                .filter { (size, _) -> VideoCaps.supports(VideoCodec.H264, size, 30) || VideoCaps.supports(VideoCodec.HEVC, size, 30) }
                .map { (size, label) ->
                    val fps60 = spec.maxVideoFps(size) >= 59 && spec.fpsRanges.any { it.upper >= 60 } &&
                        (VideoCaps.supports(VideoCodec.H264, size, 60) || VideoCaps.supports(VideoCodec.HEVC, size, 60))
                    VideoOption("${size.width}x${size.height}", label, fps60)
                }
                .ifEmpty {
                    spec.videoSizes.filter { it.matches(16f / 9f) }.take(2)
                        .map { VideoOption("${it.width}x${it.height}", "${it.height}p", false) }
                }
            return CameraUi(
                isFront = spec.isFront,
                hasFlash = spec.hasFlash,
                hasAutoFocus = spec.hasAutoFocus,
                minFocus = spec.minFocusDiopters,
                manualSensor = spec.manualSensor && spec.isoRange != null && spec.exposureRange != null,
                manualPostProcessing = spec.manualPostProcessing,
                isoMin = spec.isoRange?.lower ?: 100,
                isoMax = spec.isoRange?.upper ?: 3200,
                expMin = spec.exposureRange?.lower ?: 100_000L,
                expMax = spec.exposureRange?.upper ?: 100_000_000L,
                evMin = spec.evRange.lower,
                evMax = spec.evRange.upper,
                evStep = spec.evStep.toFloat(),
                zoomMin = spec.zoomRange.lower,
                zoomMax = spec.zoomRange.upper,
                rawCapable = spec.rawSize != null,
                ultraHdr = spec.ultraHdrSizes.isNotEmpty(),
                standardMp = spec.standardResolutionMp,
                maxMp = spec.maxResolutionMp,
                noiseModes = nr,
                edgeModes = edge,
                tones = tones,
                distortionOff = CameraMetadata.DISTORTION_CORRECTION_MODE_OFF in spec.distortionModes,
                ois = spec.oisModes.size > 1,
                videoStab = spec.videoStabModes.size > 1,
                wbPresets = wb,
                videoOptions = video,
                aperture = spec.aperture,
                equivalentFocal = spec.equivalentFocal.roundToInt(),
            )
        }

        /** Righe diagnostiche: cosa espone il telefono alle app di terze parti. */
        fun describe(catalog: CameraCatalog): List<String> = catalog.allIds.map { id ->
            val s = catalog.spec(id) ?: return@map "Camera $id: non leggibile"
            val side = if (s.isFront) "frontale" else "posteriore"
            val mp = "%.1f".format(s.standardResolutionMp) + if (s.hasHigherResolution) " / %.0f MP".format(s.maxResolutionMp) else " MP"
            val caps = buildList {
                if (s.logical) add("logica (${s.physicalIds.joinToString()})")
                if (s.manualSensor) add("manuale")
                if (s.rawCapable) add("RAW")
                if (s.ultraHighRes) add("alta risoluzione")
                if (s.ultraHdrSizes.isNotEmpty()) add("Ultra HDR")
                if (s.oisModes.size > 1) add("OIS")
            }
            "Camera $id · $side · ${s.equivalentFocal.roundToInt()} mm · $mp · ${s.hardwareLevelLabel}" +
                " · zoom ${"%.1f".format(s.zoomRange.lower)}–${"%.0f".format(s.zoomRange.upper)}×" +
                if (caps.isNotEmpty()) " · ${caps.joinToString()}" else ""
        }
    }
}

/** Stato completo dello schermo della fotocamera. */
data class UiState(
    val settings: Settings = Settings(),
    val status: CamStatus = CamStatus(),
    val live: LiveInfo = LiveInfo(),
    val camera: CameraUi = CameraUi(),
    val stops: List<LensStop> = emptyList(),
    val zoom: Float = 1f,
    val photoEv: Int = 0,
    val thumb: ImageBitmap? = null,
    val countdown: Int = 0,
    val orientation: Int = 0,
    val histogram: IntArray? = null,
    val intentPreview: ImageBitmap? = null,
    val intentMode: Boolean = false,
    val cameraInfo: List<String> = emptyList(),
) {
    val manualActive: Boolean
        get() = settings.mode == com.afam.pura.settings.CaptureMode.PRO ||
            (settings.mode == com.afam.pura.settings.CaptureMode.VIDEO && settings.videoManual)
    val manual: Manual get() = settings.manual
}

interface CameraActions {
    fun shutter()
    fun setMode(mode: com.afam.pura.settings.CaptureMode)
    fun toggleFront()
    fun cycleFlash()
    fun selectLens(stop: LensStop)
    fun setZoom(z: Float)
    fun setEv(ev: Int)
    fun focusAt(x: Float, y: Float, lock: Boolean)
    fun clearFocus()
    fun openGallery()
    fun togglePause()
    fun update(transform: (Settings) -> Settings)
    fun updateManual(transform: (Manual) -> Manual)
    fun requestLocation()
    fun intentAccept()
    fun intentRetake()
    fun close()
}
