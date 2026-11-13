package com.afam.galleria.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.afam.galleria.data.MediaItem
import com.afam.galleria.ui.components.Thumbs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

enum class PhotoFilter(val title: String) {
    ORIGINAL("Originale"),
    VIVID("Vivido"),
    VIVID_WARM("Vivido caldo"),
    VIVID_COOL("Vivido freddo"),
    DRAMATIC("Drammatico"),
    DRAMATIC_WARM("Drammatico caldo"),
    DRAMATIC_COOL("Drammatico freddo"),
    MONO("Mono"),
    SILVERTONE("Argento"),
    NOIR("Noir"),
}

/** Ritaglio in coordinate normalizzate (0..1) sull'immagine già ruotata/riflessa. */
data class CropRect(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isFull: Boolean get() = left <= 0.001f && top <= 0.001f && right >= 0.999f && bottom >= 0.999f
}

enum class Adjust(val title: String, val min: Float, val max: Float) {
    EXPOSURE("Esposizione", -1f, 1f),
    BRIGHTNESS("Luminosità", -1f, 1f),
    CONTRAST("Contrasto", -1f, 1f),
    SATURATION("Saturazione", -1f, 1f),
    WARMTH("Calore", -1f, 1f),
    TINT("Tinta", -1f, 1f),
    VIGNETTE("Vignettatura", 0f, 1f),
}

data class EditParams(
    val values: Map<Adjust, Float> = emptyMap(),
    val filter: PhotoFilter = PhotoFilter.ORIGINAL,
    val rotation: Int = 0,
    val flipH: Boolean = false,
    val crop: CropRect = CropRect(),
) {
    operator fun get(a: Adjust): Float = values[a] ?: 0f

    fun with(a: Adjust, v: Float): EditParams =
        copy(values = values + (a to v.coerceIn(a.min, a.max)))

    val hasColorChanges: Boolean
        get() = filter != PhotoFilter.ORIGINAL || values.any { it.value != 0f }

    val isUnchanged: Boolean
        get() = !hasColorChanges && rotation == 0 && !flipH && crop.isFull
}

/** Matrici colore 4x5 (stesso formato di android.graphics.ColorMatrix). */
object ColorMath {
    private const val LR = 0.2126f
    private const val LG = 0.7152f
    private const val LB = 0.0722f

    fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Restituisce a·b (applica prima b, poi a). */
    fun concat(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (i in 0 until 4) {
            for (j in 0 until 5) {
                var sum = 0f
                for (k in 0 until 4) sum += a[i * 5 + k] * b[k * 5 + j]
                if (j == 4) sum += a[i * 5 + 4]
                out[i * 5 + j] = sum
            }
        }
        return out
    }

    private fun scale(r: Float, g: Float, b: Float) = floatArrayOf(
        r, 0f, 0f, 0f, 0f,
        0f, g, 0f, 0f, 0f,
        0f, 0f, b, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    private fun offset(r: Float, g: Float, b: Float) = floatArrayOf(
        1f, 0f, 0f, 0f, r,
        0f, 1f, 0f, 0f, g,
        0f, 0f, 1f, 0f, b,
        0f, 0f, 0f, 1f, 0f,
    )

    private fun saturation(s: Float): FloatArray {
        val i = 1f - s
        return floatArrayOf(
            LR * i + s, LG * i, LB * i, 0f, 0f,
            LR * i, LG * i + s, LB * i, 0f, 0f,
            LR * i, LG * i, LB * i + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    private fun contrast(amount: Float): FloatArray {
        val s = if (amount >= 0) 1f + amount * 0.8f else 1f + amount * 0.6f
        val t = 128f * (1f - s)
        return floatArrayOf(
            s, 0f, 0f, 0f, t,
            0f, s, 0f, 0f, t,
            0f, 0f, s, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    private fun exposure(e: Float): FloatArray {
        val f = 2f.pow(e * 1.2f)
        return scale(f, f, f)
    }

    private fun warmth(w: Float) = offset(26f * w, 4f * w, -26f * w)
    private fun tint(t: Float) = offset(10f * t, -18f * t, 10f * t)
    private fun brightness(b: Float) = offset(55f * b, 55f * b, 55f * b)

    private fun chain(vararg ms: FloatArray): FloatArray {
        var m = identity()
        for (x in ms) m = concat(x, m)
        return m
    }

    fun filter(f: PhotoFilter): FloatArray = when (f) {
        PhotoFilter.ORIGINAL -> identity()
        PhotoFilter.VIVID -> chain(contrast(0.08f), saturation(1.35f))
        PhotoFilter.VIVID_WARM -> chain(contrast(0.08f), saturation(1.3f), warmth(0.5f))
        PhotoFilter.VIVID_COOL -> chain(contrast(0.08f), saturation(1.3f), warmth(-0.5f))
        PhotoFilter.DRAMATIC -> chain(exposure(-0.15f), contrast(0.35f), saturation(0.8f))
        PhotoFilter.DRAMATIC_WARM -> chain(exposure(-0.15f), contrast(0.35f), saturation(0.85f), warmth(0.45f))
        PhotoFilter.DRAMATIC_COOL -> chain(exposure(-0.15f), contrast(0.35f), saturation(0.85f), warmth(-0.45f))
        PhotoFilter.MONO -> chain(saturation(0f), contrast(0.05f))
        PhotoFilter.SILVERTONE -> chain(saturation(0f), contrast(0.12f), brightness(0.04f), offset(-4f, 0f, 6f))
        PhotoFilter.NOIR -> chain(saturation(0f), exposure(-0.12f), contrast(0.65f))
    }

    fun build(p: EditParams): FloatArray {
        val adjust = chain(
            exposure(p[Adjust.EXPOSURE]),
            brightness(p[Adjust.BRIGHTNESS]),
            contrast(p[Adjust.CONTRAST]),
            saturation(1f + p[Adjust.SATURATION]),
            warmth(p[Adjust.WARMTH]),
            tint(p[Adjust.TINT]),
        )
        return concat(filter(p.filter), adjust)
    }
}

/** Applica le modifiche all'immagine a piena risoluzione. */
object EditRenderer {
    private const val MAX_PIXELS = 24_000_000.0

    fun orient(src: Bitmap, rotation: Int, flipH: Boolean): Bitmap {
        if (rotation % 360 == 0 && !flipH) return src
        val m = Matrix().apply {
            postRotate(rotation.toFloat())
            if (flipH) postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    fun cropRect(width: Int, height: Int, crop: CropRect): android.graphics.Rect {
        val l = (crop.left * width).roundToInt().coerceIn(0, width - 1)
        val t = (crop.top * height).roundToInt().coerceIn(0, height - 1)
        val r = (crop.right * width).roundToInt().coerceIn(l + 1, width)
        val b = (crop.bottom * height).roundToInt().coerceIn(t + 1, height)
        return android.graphics.Rect(l, t, r, b)
    }

    fun drawVignette(canvas: Canvas, width: Int, height: Int, amount: Float) {
        if (amount <= 0f) return
        val radius = hypot(width.toFloat(), height.toFloat()) / 2f
        val alpha = (amount * 0.8f * 255).roundToInt().coerceIn(0, 255)
        val shader = RadialGradient(
            width / 2f, height / 2f, radius,
            intArrayOf(0x00000000, 0x00000000, alpha shl 24),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { this.shader = shader })
    }

    suspend fun render(context: Context, item: MediaItem, p: EditParams): Bitmap? = withContext(Dispatchers.Default) {
        try {
            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, item.uri)
            val decoded = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val pixels = info.size.width.toDouble() * info.size.height
                var sample = 1
                while (pixels / (sample * sample) > MAX_PIXELS) sample *= 2
                if (sample > 1) decoder.setTargetSampleSize(sample)
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
            val oriented = orient(decoded, p.rotation, p.flipH)
            if (oriented !== decoded) decoded.recycle()
            val rect = cropRect(oriented.width, oriented.height, p.crop)
            val out = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                if (p.hasColorChanges) colorFilter = ColorMatrixColorFilter(ColorMath.build(p))
            }
            canvas.drawBitmap(
                oriented,
                rect,
                android.graphics.Rect(0, 0, rect.width(), rect.height()),
                paint,
            )
            oriented.recycle()
            drawVignette(canvas, out.width, out.height, p[Adjust.VIGNETTE])
            out
        } catch (e: Throwable) {
            null
        }
    }

    /** Anteprima di lavoro, ridotta per essere fluida. */
    suspend fun preview(context: Context, item: MediaItem): Bitmap? = withContext(Dispatchers.IO) {
        try {
            Thumbs.decodeSampled(context, item, 1600, software = true)
        } catch (e: Throwable) {
            null
        }
    }
}
