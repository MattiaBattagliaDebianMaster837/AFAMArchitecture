package com.afam.pura.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.SurfaceTexture
import android.os.Build
import android.view.TextureView
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import com.afam.pura.camera.CameraController
import kotlinx.coroutines.delay

/**
 * Anteprima della fotocamera su TextureView. Focus peaking e zebra sono calcolati dalla GPU
 * con uno shader AGSL (Android 13+): nessun costo per la CPU né per la batteria del sensore.
 */
@Composable
fun Viewfinder(
    controller: CameraController?,
    geometry: PreviewGeometry,
    peaking: Boolean,
    zebra: Boolean,
    sampleLuma: Boolean,
    onLuma: (mean: Float, histogram: IntArray?) -> Unit,
    wantHistogram: Boolean,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) return
    val holder = remember { TextureHolder() }
    val density = LocalDensity.current
    val topPx = with(density) { geometry.top.toPx() }
    val heightPx = with(density) { geometry.height.toPx() }
    val shape = remember(topPx, heightPx, geometry.corner) {
        val r = with(density) { geometry.corner.toPx() }
        GenericShape { size, _ ->
            addRoundRect(RoundRect(0f, topPx, size.width, topPx + heightPx, CornerRadius(r, r)))
        }
    }

    DisposableEffect(controller) {
        holder.controller = controller
        holder.texture?.let { controller?.attachTexture(it) }
        onDispose { holder.controller = null }
    }

    AndroidView(
        modifier = modifier.clip(shape),
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = false
                surfaceTextureListener = holder
                holder.view = this
                addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> holder.applyTransform(v as TextureView) }
            }
        },
        update = { view ->
            holder.top = topPx
            holder.height = heightPx
            holder.applyTransform(view)
            if (Build.VERSION.SDK_INT >= 33) {
                val key = (if (peaking) 1 else 0) or (if (zebra) 2 else 0)
                if (holder.effectKey != key) {
                    holder.effectKey = key
                    view.setRenderEffect(if (key == 0) null else Effects.create(peaking, zebra))
                }
            }
        },
    )

    // Lettura a bassa risoluzione dell'anteprima: istogramma ed esposizione semi-automatica.
    LaunchedEffect(sampleLuma, wantHistogram) {
        if (!sampleLuma && !wantHistogram) return@LaunchedEffect
        val bmp = Bitmap.createBitmap(96, 128, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(96 * 128)
        try {
            while (true) {
                delay(if (sampleLuma) 160 else 250)
                val view = holder.view ?: continue
                if (!view.isAvailable) continue
                runCatching { view.getBitmap(bmp) }.getOrNull() ?: continue
                bmp.getPixels(pixels, 0, 96, 0, 0, 96, 128)
                val hist = IntArray(64)
                var sum = 0.0
                var weights = 0.0
                for (y in 0 until 128) {
                    val center = y in 32 until 96
                    for (x in 0 until 96) {
                        val c = pixels[y * 96 + x]
                        val r = (c shr 16) and 0xFF
                        val g = (c shr 8) and 0xFF
                        val b = c and 0xFF
                        val l = (r * 77 + g * 150 + b * 29) shr 8
                        hist[l shr 2]++
                        val w = if (center && x in 24 until 72) 2.0 else 1.0
                        sum += l * w
                        weights += w
                    }
                }
                onLuma((sum / weights / 255.0).toFloat(), if (wantHistogram) hist else null)
            }
        } finally {
            bmp.recycle()
        }
    }
}

private class TextureHolder : TextureView.SurfaceTextureListener {
    var controller: CameraController? = null
    var texture: SurfaceTexture? = null
    var view: TextureView? = null
    var effectKey = 0
    var top = 0f
    var height = 0f
    private val matrix = Matrix()
    private var applied = ""

    /**
     * La TextureView stira i fotogrammi su tutta la sua area: li riportiamo nel rettangolo
     * dell'anteprima, con le proporzioni esatte del flusso della fotocamera.
     */
    fun applyTransform(v: TextureView) {
        val vh = v.height
        if (vh <= 0 || height <= 0f) return
        val key = "$top/$height/$vh"
        if (key == applied) return
        applied = key
        matrix.reset()
        matrix.setScale(1f, height / vh)
        matrix.postTranslate(0f, top)
        v.setTransform(matrix)
        v.invalidate()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        texture = surface
        controller?.attachTexture(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        // La TextureView ha reimpostato la dimensione del buffer: si ripristina quella del sensore.
        controller?.onTextureResized(surface)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        texture = null
        val c = controller
        // La SurfaceTexture si rilascia solo dopo che la fotocamera ha smesso di usarla.
        if (c != null) {
            c.detachTexture(surface)
            return false
        }
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
}

@RequiresApi(33)
private object Effects {
    private const val SHADER = """
        uniform shader image;
        uniform float peaking;
        uniform float zebra;

        float luma(half4 c) { return dot(float3(c.rgb), float3(0.299, 0.587, 0.114)); }

        half4 main(float2 p) {
            half4 c = image.eval(p);
            if (zebra > 0.5) {
                if (luma(c) > 0.96) {
                    if (mod(p.x + p.y, 16.0) < 7.0) {
                        c = half4(mix(float3(c.rgb), float3(0.0), 0.6), 1.0);
                    }
                }
            }
            if (peaking > 0.5) {
                float d = 1.5;
                float tl = luma(image.eval(p + float2(-d, -d)));
                float t  = luma(image.eval(p + float2(0.0, -d)));
                float tr = luma(image.eval(p + float2(d, -d)));
                float l  = luma(image.eval(p + float2(-d, 0.0)));
                float r  = luma(image.eval(p + float2(d, 0.0)));
                float bl = luma(image.eval(p + float2(-d, d)));
                float b  = luma(image.eval(p + float2(0.0, d)));
                float br = luma(image.eval(p + float2(d, d)));
                float gx = -tl - 2.0 * l - bl + tr + 2.0 * r + br;
                float gy = -tl - 2.0 * t - tr + bl + 2.0 * b + br;
                float m = sqrt(gx * gx + gy * gy);
                if (m > 0.55) {
                    c = half4(1.0, 0.12, 0.16, 1.0);
                }
            }
            return c;
        }
    """

    fun create(peaking: Boolean, zebra: Boolean): RenderEffect {
        val shader = RuntimeShader(SHADER)
        shader.setFloatUniform("peaking", if (peaking) 1f else 0f)
        shader.setFloatUniform("zebra", if (zebra) 1f else 0f)
        return RenderEffect.createRuntimeShaderEffect(shader, "image")
    }
}
