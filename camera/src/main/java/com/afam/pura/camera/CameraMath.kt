package com.afam.pura.camera

import android.graphics.Rect
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.TonemapCurve
import android.util.Rational
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Valori "fotografici" per le ghiere manuali. */
object Stops {

    private val isoStops = listOf(
        25, 32, 40, 50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250,
        1600, 2000, 2500, 3200, 4000, 5000, 6400, 8000, 10000, 12800, 16000, 20000, 25600,
    )

    fun isoValues(min: Int, max: Int): List<Int> {
        val list = isoStops.filter { it in min..max }.toMutableList()
        if (list.isEmpty() || list.first() > min) list.add(0, min)
        if (list.last() < max) list.add(max)
        return list.distinct()
    }

    /** Tempi in secondi come frazioni: 1/16000 ... 30". */
    private val shutterStops: List<Long> = buildList {
        val denominators = listOf(
            16000, 12800, 10000, 8000, 6400, 5000, 4000, 3200, 2500, 2000, 1600, 1250, 1000, 800,
            640, 500, 400, 320, 250, 200, 160, 125, 100, 80, 60, 50, 40, 30, 25, 20, 15, 13, 10, 8, 6, 5, 4, 3,
        )
        denominators.forEach { add(1_000_000_000L / it) }
        listOf(0.4, 0.5, 0.6, 0.8, 1.0, 1.3, 1.6, 2.0, 2.5, 3.2, 4.0, 5.0, 6.0, 8.0, 10.0, 13.0, 15.0, 20.0, 25.0, 30.0)
            .forEach { add((it * 1_000_000_000L).roundToLong()) }
    }

    fun shutterValues(minNs: Long, maxNs: Long): List<Long> {
        val list = shutterStops.filter { it in minNs..maxNs }.toMutableList()
        if (list.isEmpty()) return listOf(minNs, maxNs).distinct()
        if (list.last() < maxNs && maxNs - list.last() > list.last() / 10) list.add(maxNs)
        return list
    }

    fun formatShutter(ns: Long): String {
        if (ns <= 0) return "--"
        val s = ns / 1e9
        return if (s >= 0.35) {
            val r = (s * 10).roundToInt() / 10.0
            if (r == r.toInt().toDouble()) "${r.toInt()}\"" else "$r\""
        } else {
            val d = (1.0 / s)
            val den = if (d >= 100) (d / 10).roundToInt() * 10 else d.roundToInt()
            "1/$den"
        }
    }

    fun formatFocus(diopters: Float): String {
        if (diopters <= 0.01f) return "∞"
        val m = 1f / diopters
        return when {
            m >= 10f -> "${m.roundToInt()} m"
            m >= 1f -> "${(m * 10).roundToInt() / 10f} m"
            else -> "${(m * 100).roundToInt()} cm"
        }
    }

    fun formatEv(steps: Int, step: Rational): String {
        val v = steps * step.toFloat()
        if (steps == 0) return "0"
        val r = (v * 10).roundToInt() / 10f
        return (if (r > 0) "+" else "") + r
    }
}

/** Bilanciamento del bianco in gradi Kelvin, calibrato sulla luce diurna della fotocamera. */
object Kelvin {

    /** Colore approssimato del corpo nero (algoritmo di Tanner Helland), canali 0..1. */
    private fun blackbody(k: Int): FloatArray {
        val t = k / 100.0
        val r = if (t <= 66) 255.0 else 329.698727446 * (t - 60).pow(-0.1332047592)
        val g = if (t <= 66) 99.4708025861 * ln(t) - 161.1195681661 else 288.1221695283 * (t - 60).pow(-0.0755148492)
        val b = when {
            t >= 66 -> 255.0
            t <= 19 -> 0.0
            else -> 138.5177312231 * ln(t - 10) - 305.0447927307
        }
        fun c(v: Double) = (v.coerceIn(1.0, 255.0) / 255.0).toFloat()
        return floatArrayOf(c(r), c(g), c(b))
    }

    /**
     * Guadagni RGGB per la temperatura [k], partendo dai guadagni che l'ISP usa con la luce
     * diurna (≈5500 K). Così la scala è corretta per il sensore reale e non solo teorica.
     */
    fun gains(k: Int, daylight: RggbChannelVector): RggbChannelVector {
        val ref = blackbody(5500)
        val cur = blackbody(k)
        val mr = (cur[1] / cur[0]) / (ref[1] / ref[0])
        val mb = (cur[1] / cur[2]) / (ref[1] / ref[2])
        return RggbChannelVector(
            (daylight.red * mr).coerceIn(0.5f, 8f),
            daylight.greenEven,
            daylight.greenOdd,
            (daylight.blue * mb).coerceIn(0.5f, 8f),
        )
    }

    /** Guadagni teorici quando la calibrazione non è ancora disponibile. */
    val fallbackDaylight = RggbChannelVector(2.0f, 1f, 1f, 1.6f)

    fun encode(g: RggbChannelVector, t: ColorSpaceTransform): FloatArray {
        val values = FloatArray(13)
        values[0] = g.red; values[1] = g.greenEven; values[2] = g.greenOdd; values[3] = g.blue
        for (i in 0 until 9) values[4 + i] = t.getElement(i % 3, i / 3).toFloat()
        return values
    }

    fun decode(v: FloatArray): Pair<RggbChannelVector, ColorSpaceTransform> {
        val g = RggbChannelVector(v[0], v[1], v[2], v[3])
        val elems = IntArray(18)
        for (i in 0 until 9) {
            elems[i * 2] = (v[4 + i] * 10000).roundToInt()
            elems[i * 2 + 1] = 10000
        }
        return g to ColorSpaceTransform(elems)
    }
}

/** Curve tonali personalizzate. */
object Curves {
    /** Curva logaritmica: ombre meno spinte e alte luci più morbide, ideale da correggere dopo. */
    fun flat(points: Int): TonemapCurve {
        val n = points.coerceIn(2, 64)
        val k = 20.0
        val curve = FloatArray(n * 2)
        for (i in 0 until n) {
            val x = i / (n - 1f)
            curve[i * 2] = x
            curve[i * 2 + 1] = (ln(1 + k * x) / ln(1 + k)).toFloat().coerceIn(0f, 1f)
        }
        return TonemapCurve(curve, curve, curve)
    }
}

/**
 * Converte un tocco sull'anteprima (coordinate normalizzate 0..1 nella vista verticale)
 * in un'area di misurazione nelle coordinate del sensore.
 */
object Metering {

    fun region(
        nx: Float,
        ny: Float,
        spec: CameraSpec,
        previewAspect: Float,
        cropRegion: Rect?,
    ): MeteringRectangle {
        // 1) Dalla vista (verticale, eventualmente specchiata) al sensore orizzontale.
        var x = nx
        val y = ny
        if (spec.isFront) x = 1f - x
        val (sx, sy) = when (((spec.sensorOrientation % 360) + 360) % 360) {
            0 -> x to y
            90 -> y to 1f - x
            180 -> 1f - x to 1f - y
            else -> 1f - y to x
        }
        // 2) L'anteprima mostra solo la parte centrale del sensore con le sue proporzioni.
        val array = spec.activeArray
        val base = cropRegion ?: Rect(0, 0, array.width(), array.height())
        val baseAspect = base.width().toFloat() / base.height()
        var vw = 1f
        var vh = 1f
        if (previewAspect > baseAspect) vh = baseAspect / previewAspect else vw = previewAspect / baseAspect
        val px = (1f - vw) / 2f + sx * vw
        val py = (1f - vh) / 2f + sy * vh
        val cx = base.left + px * base.width()
        val cy = base.top + py * base.height()
        val half = min(base.width(), base.height()) * 0.08f
        val left = max(base.left.toFloat(), cx - half).toInt()
        val top = max(base.top.toFloat(), cy - half).toInt()
        val right = min(base.right.toFloat(), cx + half).toInt()
        val bottom = min(base.bottom.toFloat(), cy + half).toInt()
        return MeteringRectangle(left, top, max(1, right - left), max(1, bottom - top), MeteringRectangle.METERING_WEIGHT_MAX - 1)
    }

    /** Area di ritaglio per lo zoom digitale quando CONTROL_ZOOM_RATIO non è disponibile. */
    fun cropForZoom(spec: CameraSpec, zoom: Float): Rect {
        val a = spec.activeArray
        val w = (a.width() / zoom).toInt()
        val h = (a.height() / zoom).toInt()
        val l = (a.width() - w) / 2
        val t = (a.height() - h) / 2
        return Rect(l, t, l + w, t + h)
    }
}
