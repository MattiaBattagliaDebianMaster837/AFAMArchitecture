package com.afam.pura.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afam.pura.camera.Stops
import com.afam.pura.settings.WhiteBalance
import com.afam.pura.ui.theme.Pura
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ProParam(val label: String) { ISO("ISO"), SHUTTER("TEMPO"), FOCUS("FUOCO"), WB("WB"), EV("EV") }

/** Un valore della ghiera: etichetta + azione che lo applica. */
private class DialModel(
    val count: Int,
    val index: Int,
    val label: (Int) -> String,
    val major: (Int) -> Boolean,
    val apply: (Int) -> Unit,
    val autoIndex: Int?,
)

@Composable
fun ProPanel(
    state: UiState,
    selected: ProParam?,
    onSelect: (ProParam) -> Unit,
    actions: CameraActions,
    rotation: Float,
) {
    val cam = state.camera
    val m = state.manual
    val live = state.live
    val evStep = android.util.Rational((cam.evStep * 600).roundToInt(), 600)

    val isoValues = remember(cam.isoMin, cam.isoMax) { Stops.isoValues(cam.isoMin, cam.isoMax) }
    val shutterValues = remember(cam.expMin, cam.expMax) { Stops.shutterValues(cam.expMin, cam.expMax) }
    val wbValues = remember(cam.wbPresets) {
        val presets = listOf(
            WhiteBalance.AUTO, WhiteBalance.DAYLIGHT, WhiteBalance.CLOUDY, WhiteBalance.SHADE,
            WhiteBalance.INCANDESCENT, WhiteBalance.FLUORESCENT,
        ).filter { it in cam.wbPresets }.map { it to 0 }
        val kelvin = if (WhiteBalance.KELVIN in cam.wbPresets) (20..100).map { WhiteBalance.KELVIN to it * 100 } else emptyList()
        presets + kelvin
    }

    fun wbLabel(p: Pair<WhiteBalance, Int>) = if (p.first == WhiteBalance.KELVIN) "${p.second}K" else p.first.label

    val dial: DialModel? = when (selected) {
        ProParam.ISO -> if (!cam.manualSensor) null else DialModel(
            count = isoValues.size + 1,
            index = m.iso?.let { iso -> 1 + isoValues.indices.minBy { abs(isoValues[it] - iso) } } ?: 0,
            label = { if (it == 0) "AUTO" else "${isoValues[it - 1]}" },
            major = { it == 0 || isoValues[it - 1].let { v -> v == 100 || v == 200 || v == 400 || v == 800 || v == 1600 || v == 3200 || v == 6400 || v == 50 } },
            apply = { i -> actions.updateManual { it.copy(iso = if (i == 0) null else isoValues[i - 1]) } },
            autoIndex = 0,
        )
        ProParam.SHUTTER -> if (!cam.manualSensor) null else DialModel(
            count = shutterValues.size + 1,
            index = m.exposureNs?.let { e -> 1 + shutterValues.indices.minBy { abs(shutterValues[it] - e) } } ?: 0,
            label = { if (it == 0) "AUTO" else Stops.formatShutter(shutterValues[it - 1]) },
            major = { it == 0 || it % 3 == 0 },
            apply = { i -> actions.updateManual { it.copy(exposureNs = if (i == 0) null else shutterValues[i - 1]) } },
            autoIndex = 0,
        )
        ProParam.FOCUS -> if (!cam.hasAutoFocus) null else DialModel(
            count = 101,
            index = m.focus?.let { f -> (1 + ((1f - f / cam.minFocus) * 99f).roundToInt()).coerceIn(1, 100) } ?: 0,
            label = { if (it == 0) "AF" else Stops.formatFocus(cam.minFocus * (1f - (it - 1) / 99f)) },
            major = { it == 0 || it % 10 == 0 },
            apply = { i -> actions.updateManual { it.copy(focus = if (i == 0) null else cam.minFocus * (1f - (i - 1) / 99f)) } },
            autoIndex = 0,
        )
        ProParam.WB -> DialModel(
            count = wbValues.size,
            index = wbValues.indexOfFirst {
                it.first == m.wb && (m.wb != WhiteBalance.KELVIN || it.second == (m.kelvin / 100) * 100)
            }.coerceAtLeast(0),
            label = { wbLabel(wbValues[it]) },
            major = { wbValues[it].first != WhiteBalance.KELVIN || wbValues[it].second % 1000 == 0 },
            apply = { i -> val v = wbValues[i]; actions.updateManual { it.copy(wb = v.first, kelvin = if (v.second > 0) v.second else it.kelvin) } },
            autoIndex = 0,
        )
        ProParam.EV -> DialModel(
            count = cam.evMax - cam.evMin + 1,
            index = m.ev - cam.evMin,
            label = { Stops.formatEv(it + cam.evMin, evStep) },
            major = { v -> val ev = v + cam.evMin; ev == 0 || (ev * cam.evStep) % 1f == 0f },
            apply = { i -> actions.updateManual { it.copy(ev = i + cam.evMin) } },
            autoIndex = -cam.evMin,
        )
        null -> null
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (dial != null) {
            Dial(dial, rotation)
        } else if (selected == ProParam.ISO || selected == ProParam.SHUTTER) {
            Text("Esposizione manuale non disponibile su questa fotocamera", color = Pura.TextDim, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
        } else if (selected == ProParam.FOCUS) {
            Text("Questa fotocamera ha il fuoco fisso", color = Pura.TextDim, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ProParam.entries.forEach { p ->
                val manual = when (p) {
                    ProParam.ISO -> m.iso != null
                    ProParam.SHUTTER -> m.exposureNs != null
                    ProParam.FOCUS -> m.focus != null
                    ProParam.WB -> m.wb != WhiteBalance.AUTO
                    ProParam.EV -> m.ev != 0
                }
                val value = when (p) {
                    ProParam.ISO -> m.iso?.toString() ?: if (live.iso > 0) "A ${live.iso}" else "AUTO"
                    ProParam.SHUTTER -> m.exposureNs?.let { Stops.formatShutter(it) } ?: if (live.exposureNs > 0) "A ${Stops.formatShutter(live.exposureNs)}" else "AUTO"
                    ProParam.FOCUS -> m.focus?.let { Stops.formatFocus(it) } ?: "AF"
                    ProParam.WB -> if (m.wb == WhiteBalance.KELVIN) "${m.kelvin}K" else m.wb.label
                    ProParam.EV -> Stops.formatEv(m.ev, evStep)
                }
                val isSel = p == selected
                Column(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSel) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { onSelect(p) }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(p.label, color = Pura.TextDim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
                    Text(
                        value,
                        color = if (manual) Pura.Yellow else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Ghiera orizzontale: trascina per scorrere i valori, con un "clic" aptico a ogni passo. */
@Composable
private fun Dial(model: DialModel, rotation: Float) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val stepPx = with(density) { 11.dp.toPx() }
    val current by rememberUpdatedState(model)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(34.dp)) {
            val auto = model.autoIndex
            if (auto != null) {
                Text(
                    "A",
                    color = if (model.index == auto) Color.Black else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (model.index == auto) Pura.Yellow else Color.White.copy(alpha = 0.18f))
                        .clickable { model.apply(auto) }
                        .padding(horizontal = 11.dp, vertical = 4.dp),
                )
            }
            Text(
                model.label(model.index),
                color = Pura.Yellow,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center).rotate(rotation),
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .pointerInput(model.count) {
                    var startIndex = 0
                    var acc = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            startIndex = current.index
                            acc = 0f
                        },
                    ) { change, dx ->
                        change.consume()
                        acc += dx
                        val ni = (startIndex - (acc / stepPx).roundToInt()).coerceIn(0, current.count - 1)
                        if (ni != current.index) {
                            current.apply(ni)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
                .pointerInput(model.count) {
                    detectTapGestures { o ->
                        val delta = ((o.x - size.width / 2f) / stepPx).roundToInt()
                        val ni = (current.index + delta).coerceIn(0, current.count - 1)
                        if (ni != current.index) current.apply(ni)
                    }
                },
        ) {
            val cx = size.width / 2
            val visible = (size.width / 2 / stepPx).toInt() + 1
            for (d in -visible..visible) {
                val i = model.index + d
                if (i < 0 || i >= model.count) continue
                val x = cx + d * stepPx
                val major = model.major(i)
                val fade = 1f - abs(d) / (visible + 1f)
                val h = if (major) size.height * 0.55f else size.height * 0.3f
                drawLine(
                    Color.White.copy(alpha = 0.25f + 0.6f * fade),
                    Offset(x, size.height - h),
                    Offset(x, size.height),
                    strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                )
            }
            drawLine(Pura.Yellow, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 2.5.dp.toPx())
        }
    }
}
