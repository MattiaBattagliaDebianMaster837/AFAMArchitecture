package com.afam.pura.camera

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
import android.hardware.camera2.CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING
import android.hardware.camera2.CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
import android.hardware.camera2.CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
import android.hardware.camera2.CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.media.MediaRecorder
import android.os.Build
import android.util.Range
import android.util.Rational
import android.util.Size
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** Tutto ciò che serve sapere di una fotocamera, letto una sola volta all'avvio. */
class CameraSpec(val id: String, val chars: CameraCharacteristics) {

    val facing: Int = chars.get(CameraCharacteristics.LENS_FACING) ?: CameraMetadata.LENS_FACING_BACK
    val isFront: Boolean get() = facing == CameraMetadata.LENS_FACING_FRONT
    val sensorOrientation: Int = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
    val activeArray: Rect = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        ?: Rect(0, 0, 4000, 3000)
    val hardwareLevel: Int = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        ?: CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
    private val capabilities: IntArray = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)

    val manualSensor = REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in capabilities
    val manualPostProcessing = REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING in capabilities
    val rawCapable = REQUEST_AVAILABLE_CAPABILITIES_RAW in capabilities
    val logical = REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA in capabilities
    val ultraHighRes = REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR in capabilities
    val physicalIds: Set<String> = if (logical) chars.physicalCameraIds else emptySet()

    val hasFlash: Boolean = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    /** 0 = fuoco fisso. */
    val minFocusDiopters: Float = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
    val hasAutoFocus: Boolean get() = minFocusDiopters > 0f && afModes.size > 1
    val focusCalibrated: Boolean = chars.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION) !=
        CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED

    val afModes: IntArray = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: IntArray(0)
    val aeModes: IntArray = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: IntArray(0)
    val awbModes: IntArray = chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: IntArray(0)
    val nrModes: IntArray = chars.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES) ?: IntArray(0)
    val edgeModes: IntArray = chars.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES) ?: IntArray(0)
    val toneModes: IntArray = chars.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES) ?: IntArray(0)
    val maxCurvePoints: Int = chars.get(CameraCharacteristics.TONEMAP_MAX_CURVE_POINTS) ?: 0
    val oisModes: IntArray = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: IntArray(0)
    val videoStabModes: IntArray = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: IntArray(0)
    val distortionModes: IntArray = chars.get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES) ?: IntArray(0)
    val fpsRanges: Array<Range<Int>> = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()

    val isoRange: Range<Int>? = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
    val exposureRange: Range<Long>? = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
    val maxFrameDuration: Long = chars.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION) ?: 1_000_000_000L
    val evRange: Range<Int> = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: Range(0, 0)
    val evStep: Rational = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP) ?: Rational(1, 3)

    val maxRegionsAf: Int = chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0
    val maxRegionsAe: Int = chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0
    val maxRegionsAwb: Int = chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB) ?: 0

    private val zoomRatioRange: Range<Float>? = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
    /** true: si usa CONTROL_ZOOM_RATIO (il sistema passa da solo tra grandangolo, principale e tele). */
    val usesZoomRatio: Boolean = zoomRatioRange != null && zoomRatioRange.upper > 1f
    val zoomRange: Range<Float> = when {
        usesZoomRatio -> zoomRatioRange!!
        else -> Range(1f, max(1f, chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f))
    }

    val focalLength: Float = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 4f
    val aperture: Float? = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.firstOrNull()
    private val physicalSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
    /** Focale equivalente 35 mm. */
    val equivalentFocal: Float = physicalSize?.let {
        val diag = hypot(it.width, it.height)
        if (diag > 0f) focalLength * 43.27f / diag else null
    } ?: (focalLength * 6f)

    private val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

    val jpegSizes: List<Size> = map?.getOutputSizes(ImageFormat.JPEG)?.sortedByArea().orEmpty()
    val highResJpegSizes: List<Size> = map?.getHighResolutionOutputSizes(ImageFormat.JPEG)?.sortedByArea().orEmpty()
    /** Taglie disponibili solo con SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION (sensori quad-bayer a 50 MP). */
    val maxResJpegSizes: List<Size> = if (ultraHighRes) {
        chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)
            ?.getOutputSizes(ImageFormat.JPEG)?.sortedByArea().orEmpty()
            .filter { s -> jpegSizes.none { it == s } && highResJpegSizes.none { it == s } }
    } else emptyList()
    val ultraHdrSizes: List<Size> = if (Build.VERSION.SDK_INT >= 34) {
        runCatching { map?.getOutputSizes(ImageFormat.JPEG_R)?.sortedByArea() }.getOrNull().orEmpty()
    } else emptyList()
    val rawSize: Size? = if (rawCapable) map?.getOutputSizes(ImageFormat.RAW_SENSOR)?.maxByOrNull { it.area } else null
    val previewSizes: List<Size> = map?.getOutputSizes(SurfaceTexture::class.java)?.sortedByArea().orEmpty()
    val yuvSizes: List<Size> = map?.getOutputSizes(ImageFormat.YUV_420_888)?.sortedByArea().orEmpty()
    val videoSizes: List<Size> = map?.getOutputSizes(MediaRecorder::class.java)?.sortedByArea().orEmpty()

    fun minFrameDurationFor(size: Size, klass: Class<*>): Long =
        runCatching { map?.getOutputMinFrameDuration(klass, size) ?: 0L }.getOrDefault(0L)

    fun maxVideoFps(size: Size): Int {
        val d = minFrameDurationFor(size, MediaRecorder::class.java)
        return if (d > 0) (1_000_000_000.0 / d).roundToInt() else 30
    }

    fun supportsFpsRange(fps: Int): Boolean = fpsRanges.any { it.lower == fps && it.upper == fps } ||
        fpsRanges.any { it.upper == fps }

    val maxResolutionMp: Float get() = (maxResJpegSizes + highResJpegSizes + jpegSizes).maxOfOrNull { it.area }
        ?.let { it / 1_000_000f } ?: 0f
    val standardResolutionMp: Float get() = (jpegSizes.maxOfOrNull { it.area } ?: 0) / 1_000_000f
    val hasHigherResolution: Boolean get() = maxResolutionMp > standardResolutionMp * 1.4f

    fun has(modes: IntArray, mode: Int) = mode in modes

    val hardwareLevelLabel: String
        get() = when (hardwareLevel) {
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL 3"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "?"
        }

    override fun toString() = "Camera $id"
}

val Size.area: Int get() = width * height
private fun Array<Size>.sortedByArea(): List<Size> = sortedByDescending { it.area }

fun Size.matches(aspect: Float, tolerance: Float = 0.02f): Boolean {
    val r = max(width, height).toFloat() / minOf(width, height)
    return abs(r - aspect) < tolerance
}

/** Un pulsante di zoom (0.6×, 1×, 2×...). */
data class LensStop(val label: String, val cameraId: String, val zoom: Float, val ratio: Float)

/** Le fotocamere del telefono e come presentarle. */
class CameraCatalog(manager: CameraManager) {

    val specs: Map<String, CameraSpec>
    val mainBack: CameraSpec?
    val mainFront: CameraSpec?
    /** Tutte le fotocamere elencate dal sistema, anche quelle non usate. */
    val allIds: List<String>
    /** Caratteristiche delle fotocamere fisiche dietro quella logica (solo per le focali). */
    private val physicalSpecs: Map<String, CameraSpec>

    init {
        val ids = runCatching { manager.cameraIdList.toList() }.getOrDefault(emptyList())
        allIds = ids
        val list = ids.mapNotNull { id ->
            runCatching { CameraSpec(id, manager.getCameraCharacteristics(id)) }.getOrNull()
        }
        specs = list.associateBy { it.id }
        mainBack = list.firstOrNull { it.facing == CameraMetadata.LENS_FACING_BACK }
        mainFront = list.firstOrNull { it.facing == CameraMetadata.LENS_FACING_FRONT }
        physicalSpecs = buildMap {
            for (spec in list) for (pid in spec.physicalIds) {
                if (pid !in specs) runCatching { put(pid, CameraSpec(pid, manager.getCameraCharacteristics(pid))) }
            }
        }
    }

    fun spec(id: String?): CameraSpec? = id?.let { specs[it] }

    /** Pulsanti dello zoom per il lato scelto. */
    fun lensStops(front: Boolean): List<LensStop> {
        val main = (if (front) mainFront else mainBack) ?: return emptyList()
        val stops = mutableListOf<LensStop>()
        val range = main.zoomRange
        if (range.lower < 0.95f) stops += stop(main.id, range.lower, range.lower)
        stops += stop(main.id, 1f, 1f)
        // Teleobiettivi e grandangoli fisici dietro la fotocamera logica.
        for (pid in main.physicalIds) {
            val phys = specs[pid] ?: physicalSpecs[pid] ?: continue
            val r = phys.equivalentFocal / main.equivalentFocal
            if (r > 1.3f && r <= range.upper + 0.01f && stops.none { abs(it.ratio - r) < 0.2f }) {
                stops += stop(main.id, r, r)
            }
        }
        // Fotocamere esposte separatamente (non coperte dallo zoom della principale).
        if (!front) {
            for (spec in specs.values) {
                if (spec === main || spec.facing != main.facing || spec.id in main.physicalIds) continue
                val r = spec.equivalentFocal / main.equivalentFocal
                val covered = (r < 0.95f && range.lower <= r + 0.05f) ||
                    (r >= 0.95f && r <= range.upper && stops.any { abs(it.ratio - r) < 0.2f })
                if (!covered && stops.none { abs(it.ratio - r) < 0.15f }) {
                    stops += stop(spec.id, 1f, r)
                }
            }
        }
        if (stops.none { it.ratio > 1.3f } && range.upper >= 2f) stops += stop(main.id, 2f, 2f)
        if (range.upper >= 10f && stops.none { it.ratio >= 5f }) {
            stops += stop(main.id, if (range.upper >= 20f) 10f else 5f, if (range.upper >= 20f) 10f else 5f)
        }
        return stops.sortedBy { it.ratio }
    }

    private fun stop(cameraId: String, zoom: Float, ratio: Float): LensStop {
        val label = formatZoom(ratio)
        return LensStop(label, cameraId, zoom, ratio)
    }

    companion object {
        fun formatZoom(z: Float): String {
            val r = (z * 10).roundToInt() / 10f
            return if (r == r.toInt().toFloat()) "${r.toInt()}" else "$r"
        }
    }
}
