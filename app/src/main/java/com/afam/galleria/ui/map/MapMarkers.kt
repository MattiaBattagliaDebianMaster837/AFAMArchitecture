package com.afam.galleria.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Disegna i segnaposto della mappa: miniatura quadrata arrotondata con bordo bianco,
 * ombra morbida e, per i gruppi, il numero di foto in un badge.
 */
class MarkerFactory(context: Context, private val accent: Int) {

    private val density = context.resources.displayMetrics.density
    private val thumb = 52f * density
    private val border = 2.5f * density
    private val radius = 12f * density
    private val pad = 12f * density
    val sizePx: Int = (thumb + pad * 2).roundToInt()

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(5f * density, 0f, 1.5f * density, 0x55000000)
    }
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD1D1D6.toInt() }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val badgeBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11.5f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    fun create(photo: Bitmap?, count: Int): Bitmap {
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val outer = RectF(pad, pad, pad + thumb, pad + thumb)
        canvas.drawRoundRect(outer, radius, radius, shadowPaint)
        val inner = RectF(outer.left + border, outer.top + border, outer.right - border, outer.bottom - border)
        val innerRadius = radius - border
        if (photo != null && !photo.isRecycled) {
            val src = if (photo.config == Bitmap.Config.HARDWARE) photo.copy(Bitmap.Config.ARGB_8888, false) else photo
            val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = max(inner.width() / src.width, inner.height() / src.height)
            val dx = inner.left + (inner.width() - src.width * scale) / 2f
            val dy = inner.top + (inner.height() - src.height * scale) / 2f
            shader.setLocalMatrix(Matrix().apply {
                setScale(scale, scale)
                postTranslate(dx, dy)
            })
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
            canvas.drawRoundRect(inner, innerRadius, innerRadius, paint)
        } else {
            canvas.drawRoundRect(inner, innerRadius, innerRadius, placeholderPaint)
        }
        if (count > 1) {
            val text = abbreviate(count)
            val h = 20f * density
            val w = max(h, textPaint.measureText(text) + 12f * density)
            val cx = outer.right - 2f * density
            val cy = outer.top + 2f * density
            val badge = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
            // Il badge non deve uscire dall'immagine.
            if (badge.right > sizePx) badge.offset(sizePx - badge.right, 0f)
            if (badge.top < 0) badge.offset(0f, -badge.top)
            canvas.drawRoundRect(badge, h / 2f, h / 2f, badgePaint)
            canvas.drawRoundRect(badge, h / 2f, h / 2f, badgeBorder)
            val baseline = badge.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(text, badge.centerX(), baseline, textPaint)
        }
        return out
    }

    private fun abbreviate(n: Int): String = when {
        n < 1000 -> n.toString()
        n < 10_000 -> String.format(java.util.Locale.ITALIAN, "%.1fk", n / 1000f)
        else -> "${n / 1000}k"
    }
}
