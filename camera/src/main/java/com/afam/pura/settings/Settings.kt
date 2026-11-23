package com.afam.pura.settings

enum class CaptureMode(val label: String) { PRO("PRO"), PHOTO("FOTO"), VIDEO("VIDEO") }

enum class FlashMode { OFF, AUTO, ON, TORCH }

/** Proporzioni dell'immagine, espresse in orizzontale (lato lungo / lato corto). */
enum class Aspect(val label: String, val long: Int, val short: Int) {
    R4_3("4:3", 4, 3),
    R16_9("16:9", 16, 9),
    R1_1("1:1", 1, 1);

    val ratio: Float get() = long.toFloat() / short
}

enum class PhotoFormat(val label: String) {
    JPEG("JPEG"),
    ULTRA_HDR("JPEG Ultra HDR"),
    RAW_JPEG("RAW (DNG) + JPEG"),
}

/** Risoluzione delle foto: standard (pixel binning, 12 MP) o la massima del sensore (50 MP). */
enum class Resolution { STANDARD, MAXIMUM }

/** Preimpostazioni dell'elaborazione d'immagine. Nessuna usa l'AI: sono le pipeline dell'ISP. */
enum class ImageProfile(val label: String, val description: String) {
    NATURAL("Naturale", "Elaborazione minima: riduzione rumore leggera e nessuna nitidezza artificiale."),
    STANDARD("Standard", "Elaborazione classica dell'ISP in alta qualità, senza AI."),
    CUSTOM("Personalizzato", "Scegli tu riduzione rumore, nitidezza e curva tonale."),
}

enum class NoiseReduction(val label: String) {
    OFF("Disattivata"), MINIMAL("Minima"), FAST("Leggera"), HIGH_QUALITY("Alta qualità")
}

enum class Sharpening(val label: String) { OFF("Disattivata"), FAST("Leggera"), HIGH_QUALITY("Alta qualità") }

enum class ToneCurve(val label: String) {
    STANDARD("Standard del sensore"), SRGB("sRGB pura"), FLAT("Piatta (Log)")
}

enum class GridType(val label: String) { NONE("Nessuna"), THIRDS("Regola dei terzi"), GOLDEN("Sezione aurea"), SQUARE("Quadrati 4×4") }

enum class VolumeKeys(val label: String) { SHUTTER("Scatto"), ZOOM("Zoom"), VOLUME("Volume") }

enum class WhiteBalance(val label: String, val kelvin: Int) {
    AUTO("Auto", 0),
    DAYLIGHT("Sole", 5500),
    CLOUDY("Nuvoloso", 6500),
    SHADE("Ombra", 7500),
    INCANDESCENT("Lampadina", 2850),
    FLUORESCENT("Fluorescente", 4000),
    KELVIN("Kelvin", 0),
}

enum class VideoCodec(val label: String) { HEVC("H.265 (HEVC)"), H264("H.264 (AVC)") }

enum class Timer(val seconds: Int, val label: String) { OFF(0, "Off"), S3(3, "3 s"), S10(10, "10 s") }

/** Impostazioni manuali del modo PRO (null = automatico). */
data class Manual(
    val iso: Int? = null,
    val exposureNs: Long? = null,
    /** Distanza di messa a fuoco in diottrie (0 = infinito); null = autofocus. */
    val focus: Float? = null,
    val wb: WhiteBalance = WhiteBalance.AUTO,
    val kelvin: Int = 5500,
    /** Compensazione dell'esposizione in passi del sensore. */
    val ev: Int = 0,
)

data class Settings(
    val mode: CaptureMode = CaptureMode.PHOTO,
    val front: Boolean = false,
    /** Fotocamera posteriore scelta tra quelle esposte separatamente (null = principale). */
    val backCameraId: String? = null,
    val flash: FlashMode = FlashMode.OFF,
    val aspect: Aspect = Aspect.R4_3,
    val resolution: Resolution = Resolution.STANDARD,
    val format: PhotoFormat = PhotoFormat.JPEG,
    val jpegQuality: Int = 95,
    val timer: Timer = Timer.OFF,
    val manual: Manual = Manual(),
    /** Comandi manuali anche in video. */
    val videoManual: Boolean = false,

    // Elaborazione
    val profile: ImageProfile = ImageProfile.NATURAL,
    val noiseReduction: NoiseReduction = NoiseReduction.MINIMAL,
    val sharpening: Sharpening = Sharpening.OFF,
    val tone: ToneCurve = ToneCurve.STANDARD,
    val distortionCorrection: Boolean = true,
    val ois: Boolean = true,

    // Video
    val videoSize: String = "1920x1080",
    val videoFps: Int = 30,
    val videoCodec: VideoCodec = VideoCodec.HEVC,
    val videoHighBitrate: Boolean = false,
    val videoStabilization: Boolean = true,
    val videoAudio: Boolean = true,

    // Aiuti
    val grid: GridType = GridType.THIRDS,
    val level: Boolean = false,
    val histogram: Boolean = false,
    val peaking: Boolean = true,
    val zebra: Boolean = false,

    // Generali
    val shutterSound: Boolean = true,
    val volumeKeys: VolumeKeys = VolumeKeys.SHUTTER,
    val geotag: Boolean = false,
    val mirrorFront: Boolean = true,
)
