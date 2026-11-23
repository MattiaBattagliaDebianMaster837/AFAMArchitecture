package com.afam.pura.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CaptureResult
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afam.pura.camera.Stops
import com.afam.pura.settings.GridType
import com.afam.pura.ui.theme.Pura
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

@Composable
fun GridOverlay(type: GridType, modifier: Modifier) {
    if (type == GridType.NONE) return
    Canvas(modifier) {
        val c = Color.White.copy(alpha = 0.45f)
        val sw = 1.dp.toPx()
        fun v(f: Float) = drawLine(c, Offset(size.width * f, 0f), Offset(size.width * f, size.height), sw)
        fun h(f: Float) = drawLine(c, Offset(0f, size.height * f), Offset(size.width, size.height * f), sw)
        when (type) {
            GridType.THIRDS -> listOf(1f / 3, 2f / 3).forEach { v(it); h(it) }
            GridType.GOLDEN -> listOf(0.382f, 0.618f).forEach { v(it); h(it) }
            GridType.SQUARE -> listOf(0.25f, 0.5f, 0.75f).forEach { v(it); h(it) }
            GridType.NONE -> Unit
        }
    }
}

@Composable
fun FocusOverlay(state: UiState, modifier: Modifier) {
    val point = state.status.focusPoint ?: return
    val scale = remember { Animatable(1.4f) }
    LaunchedEffect(point) {
        scale.snapTo(1.4f)
        scale.animateTo(1f, tween(220))
    }
    val af = state.live.afState
    val color = when {
        state.status.aeAfLocked -> Pura.Yellow
        af == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED -> Pura.Yellow
        af == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> Pura.Accent
        else -> Color.White
    }
    val ev = if (state.manualActive) state.manual.ev else state.photoEv
    BoxWithConstraints(modifier) {
        val x = maxWidth * point.x
        val y = maxHeight * point.y
        val ring = 72.dp
        Canvas(Modifier.offset(x - ring / 2, y - ring / 2).size(ring)) {
            val r = size.minDimension / 2 * scale.value
            drawCircle(color, radius = r - 2.dp.toPx(), style = Stroke(1.6.dp.toPx()))
            drawCircle(color, radius = 2.dp.toPx())
        }
        // Indicatore della compensazione: trascina in verticale per cambiarla.
        Text(
            "☀ " + Stops.formatEv(ev, android.util.Rational((state.camera.evStep * 600).roundToInt(), 600)),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.offset(x + ring / 2 + 2.dp, y - 9.dp),
        )
        if (state.status.aeAfLocked) {
            Text(
                "BLOCCO AE/AF",
                color = Color.Black,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .offset(x - 44.dp, y - ring / 2 - 26.dp)
                    .background(Pura.Yellow, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** Livella: la linea diventa gialla quando il telefono è dritto (entro 1°). */
@Composable
fun LevelOverlay(modifier: Modifier) {
    var roll by remember { mutableFloatStateOf(0f) }
    if (!LocalInspectionMode.current) {
        val context = LocalContext.current
        DisposableEffect(Unit) {
            val sm = context.getSystemService(SensorManager::class.java)
            val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            var smooth = 0f
            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    val angle = Math.toDegrees(atan2(e.values[0].toDouble(), e.values[1].toDouble())).toFloat()
                    // Filtro passa-basso sull'angolo (gestendo il salto ±180°).
                    var d = angle - smooth
                    if (d > 180f) d -= 360f
                    if (d < -180f) d += 360f
                    smooth += d * 0.2f
                    roll = smooth
                }

                override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
            }
            if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { sm?.unregisterListener(listener) }
        }
    }
    val nearest = (roll / 90f).roundToInt() * 90f
    val error = roll - nearest
    val level = abs(error) < 1f
    Canvas(modifier.size(160.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val len = size.width / 2
        val color = if (level) Pura.Yellow else Color.White
        // Riferimenti fissi del telefono.
        rotate(nearest, c) {
            drawLine(Color.White.copy(alpha = 0.6f), Offset(c.x - len, c.y), Offset(c.x - len * 0.62f, c.y), 2.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.6f), Offset(c.x + len * 0.62f, c.y), Offset(c.x + len, c.y), 2.dp.toPx())
        }
        // Orizzonte reale.
        rotate(roll, c) {
            drawLine(color, Offset(c.x - len * 0.55f, c.y), Offset(c.x + len * 0.55f, c.y), 2.dp.toPx())
        }
    }
}

@Composable
fun Histogram(data: IntArray, modifier: Modifier) {
    Box(modifier.size(124.dp, 62.dp).background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp)).padding(6.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val max = (data.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
            // Scala con radice: si vedono anche le zone con pochi pixel.
            val path = Path().apply {
                moveTo(0f, size.height)
                data.forEachIndexed { i, v ->
                    val x = size.width * i / (data.size - 1)
                    val y = size.height * (1f - kotlin.math.sqrt(v / max))
                    lineTo(x, y)
                }
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path, Color.White.copy(alpha = 0.8f))
            // Avviso di alte luci bruciate.
            if (data.last() > data.sum() * 0.02f) {
                drawRect(Pura.Accent, Offset(size.width - 3.dp.toPx(), 0f), androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
            }
        }
    }
}

