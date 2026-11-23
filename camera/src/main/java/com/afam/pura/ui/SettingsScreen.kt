package com.afam.pura.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afam.pura.settings.Aspect
import com.afam.pura.settings.GridType
import com.afam.pura.settings.CaptureMode
import com.afam.pura.settings.ImageProfile
import com.afam.pura.settings.MultiFrame
import com.afam.pura.settings.NoiseReduction
import com.afam.pura.settings.PhotoFormat
import com.afam.pura.settings.Resolution
import com.afam.pura.settings.Sharpening
import com.afam.pura.settings.Timer
import com.afam.pura.settings.ToneCurve
import com.afam.pura.settings.VideoCodec
import com.afam.pura.settings.VolumeKeys
import com.afam.pura.ui.theme.Pura
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(state: UiState, actions: CameraActions, onClose: () -> Unit) {
    val s = state.settings
    val cam = state.camera
    val top = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).clickable(enabled = false) {}) {
        Row(
            Modifier.fillMaxWidth().padding(top = top).height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIcon(Icons.Filled.Close, "Chiudi", 0f, onClose)
            Spacer(Modifier.width(8.dp))
            Text("Impostazioni", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = bottom + 24.dp),
        ) {
            item { Section("Foto") }
            item {
                Choice("Proporzioni", Aspect.entries.map { it to it.label }, s.aspect) { v -> actions.update { it.copy(aspect = v) } }
            }
            if (cam.hasHigherRes) item {
                Choice(
                    "Risoluzione",
                    listOf(
                        Resolution.STANDARD to "${mp(cam.standardMp)} MP (consigliata)",
                        Resolution.MAXIMUM to "${mp(cam.maxMp)} MP",
                    ),
                    s.resolution,
                    note = "A 50 MP il sensore non unisce i pixel: più dettaglio con molta luce, più rumore al buio. " +
                        "Se il telefono non la concede alle app esterne si torna in automatico alla standard.",
                ) { v -> actions.update { it.copy(resolution = v) } }
            }
            item {
                val formats = buildList {
                    add(PhotoFormat.JPEG to PhotoFormat.JPEG.label)
                    if (cam.ultraHdr) add(PhotoFormat.ULTRA_HDR to PhotoFormat.ULTRA_HDR.label)
                    if (cam.rawCapable) add(PhotoFormat.RAW_JPEG to PhotoFormat.RAW_JPEG.label)
                }
                Choice(
                    "Formato",
                    formats,
                    s.format,
                    note = "RAW (DNG) salva i dati grezzi del sensore, senza alcuna elaborazione: ideale per Lightroom o Snapseed.",
                ) { v -> actions.update { it.copy(format = v) } }
            }
            item {
                SliderRow("Qualità JPEG", s.jpegQuality, 70, 100, suffix = "") { v -> actions.update { it.copy(jpegQuality = v) } }
            }
            item {
                Choice("Timer", Timer.entries.map { it to it.label }, s.timer) { v -> actions.update { it.copy(timer = v) } }
            }
            item {
                Toggle("Specchia i selfie", "Salva la foto frontale come la vedi nell'anteprima", s.mirrorFront) { v ->
                    actions.update { it.copy(mirrorFront = v) }
                }
            }

            item { Section("Elaborazione (senza AI)") }
            item {
                Choice(
                    "Profilo immagine",
                    ImageProfile.entries.map { it to it.label },
                    s.profile,
                    note = s.profile.description,
                ) { v -> actions.update { it.copy(profile = v) } }
            }
            item {
                Choice(
                    "Riduzione rumore multi-scatto",
                    MultiFrame.entries.map { it to it.label },
                    s.multiFrame,
                    note = "Con poca luce scatta 4–8 fotogrammi identici, li allinea e ne fa la media: il rumore " +
                        "(i puntini colorati) sparisce senza inventare dettagli. Niente AI. Tieni fermo il telefono per un attimo." +
                        if (!state.status.multiFrameReady && s.multiFrame != MultiFrame.OFF && s.mode != CaptureMode.VIDEO)
                            "\nNon attivo ora: non funziona con RAW, Ultra HDR o 50 MP." else "",
                ) { v -> actions.update { it.copy(multiFrame = v) } }
            }
            if (s.profile == ImageProfile.CUSTOM) {
                item {
                    Choice(
                        "Riduzione rumore",
                        NoiseReduction.entries.filter { it in cam.noiseModes }.map { it to it.label },
                        s.noiseReduction,
                    ) { v -> actions.update { it.copy(noiseReduction = v) } }
                }
                item {
                    Choice(
                        "Nitidezza",
                        Sharpening.entries.filter { it in cam.edgeModes }.map { it to it.label },
                        s.sharpening,
                    ) { v -> actions.update { it.copy(sharpening = v) } }
                }
                item {
                    Choice(
                        "Curva tonale",
                        ToneCurve.entries.filter { it in cam.tones }.map { it to it.label },
                        s.tone,
                        note = "\"Piatta\" conserva più dettagli in ombre e luci, da correggere poi in editing.",
                    ) { v -> actions.update { it.copy(tone = v) } }
                }
            }
            if (cam.distortionOff) item {
                Toggle("Correzione distorsione", "Raddrizza le linee curve del grandangolo", s.distortionCorrection) { v ->
                    actions.update { it.copy(distortionCorrection = v) }
                }
            }
            if (cam.ois) item {
                Toggle("Stabilizzazione ottica (OIS)", null, s.ois) { v -> actions.update { it.copy(ois = v) } }
            }

            item { Section("Video") }
            if (cam.videoOptions.isNotEmpty()) item {
                Choice("Risoluzione", cam.videoOptions.map { it.size to it.label }, s.videoSize) { v ->
                    actions.update { it.copy(videoSize = v) }
                }
            }
            item {
                val fps60 = cam.videoOptions.firstOrNull { it.size == s.videoSize }?.fps60 == true
                Choice(
                    "Fotogrammi al secondo",
                    if (fps60) listOf(30 to "30 fps", 60 to "60 fps") else listOf(30 to "30 fps"),
                    if (fps60) s.videoFps else 30,
                ) { v -> actions.update { it.copy(videoFps = v) } }
            }
            item {
                Choice("Codec", VideoCodec.entries.map { it to it.label }, s.videoCodec,
                    note = "H.265 occupa circa metà spazio a parità di qualità.") { v -> actions.update { it.copy(videoCodec = v) } }
            }
            item {
                Toggle("Bitrate alto", "Più dettaglio, file più grandi", s.videoHighBitrate) { v -> actions.update { it.copy(videoHighBitrate = v) } }
            }
            if (cam.videoStab) item {
                Toggle("Stabilizzazione elettronica", "Riduce i tremolii (ritaglia leggermente l'immagine)", s.videoStabilization) { v ->
                    actions.update { it.copy(videoStabilization = v) }
                }
            }
            item {
                Toggle("Registra audio", null, s.videoAudio) { v -> actions.update { it.copy(videoAudio = v) } }
            }
            item {
                Toggle("Comandi manuali in video", "ISO, tempo, fuoco e bilanciamento del bianco anche nei video", s.videoManual) { v ->
                    actions.update { it.copy(videoManual = v) }
                }
            }

            item { Section("Aiuti di ripresa") }
            item {
                Choice("Griglia", GridType.entries.map { it to it.label }, s.grid) { v -> actions.update { it.copy(grid = v) } }
            }
            item { Toggle("Livella", "Linea dell'orizzonte", s.level) { v -> actions.update { it.copy(level = v) } } }
            item { Toggle("Istogramma", null, s.histogram) { v -> actions.update { it.copy(histogram = v) } } }
            item {
                Toggle("Focus peaking", "Evidenzia in rosso i bordi a fuoco quando metti a fuoco a mano", s.peaking) { v ->
                    actions.update { it.copy(peaking = v) }
                }
            }
            item {
                Toggle("Zebra", "Strisce sulle zone sovraesposte nei modi manuali", s.zebra) { v -> actions.update { it.copy(zebra = v) } }
            }

            item { Section("Generali") }
            item { Toggle("Suono di scatto", null, s.shutterSound) { v -> actions.update { it.copy(shutterSound = v) } } }
            item {
                Choice("Tasti del volume", VolumeKeys.entries.map { it to it.label }, s.volumeKeys) { v ->
                    actions.update { it.copy(volumeKeys = v) }
                }
            }
            item {
                Toggle("Salva posizione", "Aggiunge le coordinate GPS alle foto", s.geotag) { v ->
                    if (v) actions.requestLocation() else actions.update { it.copy(geotag = false) }
                }
            }

            if (state.cameraInfo.isNotEmpty()) {
                item { Section("Fotocamere del telefono") }
                item {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(
                            "Ciò che il sistema rende disponibile alle app esterne:",
                            color = Pura.TextDim,
                            fontSize = 13.sp,
                        )
                        state.cameraInfo.forEach {
                            Text(it, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
            item {
                Text(
                    "Pura non usa intelligenza artificiale: nessun riconoscimento di scena, nessun HDR multi-scatto, " +
                        "nessun ritocco dei volti. Solo il sensore e l'elaborazione di base dell'ISP che scegli tu.",
                    color = Pura.TextDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
            item {
                Text(
                    "Pura versione ${com.afam.pura.BuildConfig.VERSION_NAME}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

private fun mp(v: Float): String = if (v >= 20f) "${v.roundToInt()}" else "%.1f".format(v).removeSuffix(",0").removeSuffix(".0")

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        color = Pura.Accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 26.dp, bottom = 6.dp),
    )
}

@Composable
private fun Toggle(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            subtitle?.let { Text(it, color = Pura.TextDim, fontSize = 13.sp) }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Pura.Accent, checkedThumbColor = Color.White),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choice(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    note: String? = null,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, color = Color.White, fontSize = 16.sp)
        FlowRow(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (value, label) ->
                val isSel = value == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isSel) Color.White else Color.White.copy(alpha = 0.1f))
                        .clickable { onSelect(value) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(label, color = if (isSel) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        note?.let { Text(it, color = Pura.TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun SliderRow(title: String, value: Int, min: Int, max: Int, suffix: String, onChange: (Int) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row {
            Text(title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("${local.roundToInt()}$suffix", color = Pura.TextDim, fontSize = 15.sp)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange((local / 5f).roundToInt() * 5) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min) / 5 - 1,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Pura.Accent),
        )
    }
}

@Composable
fun PermissionScreen(onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color.Black).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Pura", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Per scattare serve l'accesso alla fotocamera. Il microfono serve solo per l'audio dei video.",
            color = Pura.TextDim,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            "Consenti l'accesso",
            color = Color.Black,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable(onClick = onRequest)
                .padding(horizontal = 22.dp, vertical = 12.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Apri le impostazioni dell'app",
            color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onOpenSettings).padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
