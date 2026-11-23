package com.afam.pura.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Salva le impostazioni nelle SharedPreferences (lettura sincrona, rapidissima all'avvio). */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pura_settings", Context.MODE_PRIVATE)

    fun load(): Settings {
        val d = Settings()
        val p = prefs
        return Settings(
            mode = p.enum("mode", d.mode),
            front = p.getBoolean("front", d.front),
            backCameraId = p.getString("backCameraId", null),
            flash = p.enum("flash", d.flash),
            aspect = p.enum("aspect", d.aspect),
            resolution = p.enum("resolution", d.resolution),
            format = p.enum("format", d.format),
            jpegQuality = p.getInt("jpegQuality", d.jpegQuality),
            timer = p.enum("timer", d.timer),
            manual = Manual(
                iso = p.getInt("m_iso", -1).takeIf { it > 0 },
                exposureNs = p.getLong("m_exp", -1L).takeIf { it > 0 },
                focus = p.getFloat("m_focus", -1f).takeIf { it >= 0f },
                wb = p.enum("m_wb", d.manual.wb),
                kelvin = p.getInt("m_kelvin", d.manual.kelvin),
                ev = p.getInt("m_ev", 0),
            ),
            videoManual = p.getBoolean("videoManual", d.videoManual),
            profile = p.enum("profile", d.profile),
            noiseReduction = p.enum("nr", d.noiseReduction),
            sharpening = p.enum("sharp", d.sharpening),
            tone = p.enum("tone", d.tone),
            distortionCorrection = p.getBoolean("distortion", d.distortionCorrection),
            ois = p.getBoolean("ois", d.ois),
            videoSize = p.getString("videoSize", d.videoSize) ?: d.videoSize,
            videoFps = p.getInt("videoFps", d.videoFps),
            videoCodec = p.enum("videoCodec", d.videoCodec),
            videoHighBitrate = p.getBoolean("videoHighBitrate", d.videoHighBitrate),
            videoStabilization = p.getBoolean("videoStab", d.videoStabilization),
            videoAudio = p.getBoolean("videoAudio", d.videoAudio),
            grid = p.enum("grid", d.grid),
            level = p.getBoolean("level", d.level),
            histogram = p.getBoolean("histogram", d.histogram),
            peaking = p.getBoolean("peaking", d.peaking),
            zebra = p.getBoolean("zebra", d.zebra),
            shutterSound = p.getBoolean("shutterSound", d.shutterSound),
            volumeKeys = p.enum("volumeKeys", d.volumeKeys),
            geotag = p.getBoolean("geotag", d.geotag),
            mirrorFront = p.getBoolean("mirrorFront", d.mirrorFront),
        )
    }

    fun save(s: Settings) {
        prefs.edit {
            putString("mode", s.mode.name)
            putBoolean("front", s.front)
            putString("backCameraId", s.backCameraId)
            putString("flash", s.flash.name)
            putString("aspect", s.aspect.name)
            putString("resolution", s.resolution.name)
            putString("format", s.format.name)
            putInt("jpegQuality", s.jpegQuality)
            putString("timer", s.timer.name)
            putInt("m_iso", s.manual.iso ?: -1)
            putLong("m_exp", s.manual.exposureNs ?: -1L)
            putFloat("m_focus", s.manual.focus ?: -1f)
            putString("m_wb", s.manual.wb.name)
            putInt("m_kelvin", s.manual.kelvin)
            putInt("m_ev", s.manual.ev)
            putBoolean("videoManual", s.videoManual)
            putString("profile", s.profile.name)
            putString("nr", s.noiseReduction.name)
            putString("sharp", s.sharpening.name)
            putString("tone", s.tone.name)
            putBoolean("distortion", s.distortionCorrection)
            putBoolean("ois", s.ois)
            putString("videoSize", s.videoSize)
            putInt("videoFps", s.videoFps)
            putString("videoCodec", s.videoCodec.name)
            putBoolean("videoHighBitrate", s.videoHighBitrate)
            putBoolean("videoStab", s.videoStabilization)
            putBoolean("videoAudio", s.videoAudio)
            putString("grid", s.grid.name)
            putBoolean("level", s.level)
            putBoolean("histogram", s.histogram)
            putBoolean("peaking", s.peaking)
            putBoolean("zebra", s.zebra)
            putBoolean("shutterSound", s.shutterSound)
            putString("volumeKeys", s.volumeKeys.name)
            putBoolean("geotag", s.geotag)
            putBoolean("mirrorFront", s.mirrorFront)
        }
    }

    /** Dati di calibrazione del bilanciamento del bianco in Kelvin, per fotocamera. */
    fun wbCalibration(cameraId: String): FloatArray? {
        val raw = prefs.getString("wbcal_$cameraId", null) ?: return null
        val values = raw.split(',').mapNotNull { it.toFloatOrNull() }
        return if (values.size == 13) values.toFloatArray() else null
    }

    fun saveWbCalibration(cameraId: String, values: FloatArray) {
        prefs.edit { putString("wbcal_$cameraId", values.joinToString(",")) }
    }

    private inline fun <reified T : Enum<T>> SharedPreferences.enum(key: String, default: T): T {
        val name = getString(key, null) ?: return default
        return enumValues<T>().firstOrNull { it.name == name } ?: default
    }
}
