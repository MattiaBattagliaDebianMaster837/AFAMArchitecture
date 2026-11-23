package com.afam.galleria.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afam.galleria.GalleryViewModel
import com.afam.galleria.data.AlbumInfo
import com.afam.galleria.data.MediaItem
import com.afam.galleria.ui.glass.glass
import com.afam.galleria.ui.theme.Ios
import com.afam.galleria.ui.theme.IosType
import kotlinx.coroutines.delay

data class SheetAction(
    val title: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

class ActionSheetSpec(
    val title: String?,
    val message: String?,
    val actions: List<SheetAction>,
)

class PromptSpec(
    val title: String,
    val message: String?,
    val initial: String,
    val placeholder: String,
    val confirm: String,
    val onConfirm: (String) -> Unit,
)

/** Stato dei pannelli sovrapposti: menu di azioni, richieste di testo, avvisi brevi. */
@Stable
class OverlayState {
    var sheet by mutableStateOf<ActionSheetSpec?>(null)
        private set
    var sheetVisible by mutableStateOf(false)

    var prompt by mutableStateOf<PromptSpec?>(null)
        private set
    var promptVisible by mutableStateOf(false)

    var pickerItems by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var pickerVisible by mutableStateOf(false)

    var toast by mutableStateOf<String?>(null)
    var toastSeq by mutableIntStateOf(0)
        private set

    val isModal: Boolean get() = sheetVisible || promptVisible || pickerVisible

    fun actionSheet(title: String? = null, message: String? = null, actions: List<SheetAction>) {
        sheet = ActionSheetSpec(title, message, actions)
        sheetVisible = true
    }

    fun prompt(
        title: String,
        message: String? = null,
        initial: String = "",
        placeholder: String = "Titolo",
        confirm: String = "Salva",
        onConfirm: (String) -> Unit,
    ) {
        prompt = PromptSpec(title, message, initial, placeholder, confirm, onConfirm)
        promptVisible = true
    }

    fun pickAlbum(items: List<MediaItem>) {
        if (items.isEmpty()) return
        pickerItems = items
        pickerVisible = true
    }

    fun toast(text: String) {
        toast = text
        toastSeq++
    }

    fun dismissAll() {
        sheetVisible = false
        promptVisible = false
        pickerVisible = false
    }
}

val LocalOverlay = staticCompositionLocalOf<OverlayState> { error("OverlayState mancante") }

@Composable
fun OverlayHost(state: OverlayState, vm: GalleryViewModel) {
    val c = Ios.colors

    LaunchedEffect(state.toastSeq) {
        if (state.toast != null) {
            delay(1900)
            state.toast = null
        }
    }

    BackHandler(enabled = state.isModal) { state.dismissAll() }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.isModal,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .plainClickable {
                        state.sheetVisible = false
                        state.pickerVisible = false
                    }
            )
        }

        AnimatedVisibility(
            visible = state.sheetVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(280)) { it },
            exit = slideOutVertically(tween(220)) { it },
        ) {
            state.sheet?.let { spec -> ActionSheet(spec) { state.sheetVisible = false } }
        }

        AnimatedVisibility(
            visible = state.pickerVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(300)) { it },
            exit = slideOutVertically(tween(240)) { it },
        ) {
            AlbumPickerSheet(
                vm = vm,
                items = state.pickerItems,
                onClose = { state.pickerVisible = false },
                onNewAlbum = {
                    val items = state.pickerItems
                    state.pickerVisible = false
                    state.prompt(
                        title = "Nuovo album",
                        message = "Inserisci un nome per questo album.",
                    ) { name -> vm.createAlbum(name, items) }
                },
            )
        }

        if (state.promptVisible) {
            state.prompt?.let { spec ->
                PromptDialog(spec, onDismiss = { state.promptVisible = false })
            }
        }

        AnimatedVisibility(
            visible = state.toast != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 120.dp),
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut(),
        ) {
            var last by remember { mutableStateOf("") }
            state.toast?.let { last = it }
            Box(
                Modifier
                    .glass(RoundedCornerShape(50), tint = Color(0xC71C1C1E))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(last, style = IosType.subhead.copy(fontWeight = FontWeight.Medium), color = Color.White)
            }
        }
    }
}

@Composable
private fun ActionSheet(spec: ActionSheetSpec, onDismiss: () -> Unit) {
    val c = Ios.colors
    val groupBg = if (c.isDark) Color(0xC8232326) else Color(0xD6F9F9FB)
    val cancelBg = if (c.isDark) Color(0xE02C2C2E) else Color(0xEFFFFFFF)
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .plainClickable { }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(28.dp), tint = groupBg, blurRadius = 14.dp)
        ) {
            if (spec.title != null || spec.message != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    spec.title?.let {
                        Text(it, style = IosType.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.secondaryLabel, textAlign = TextAlign.Center)
                    }
                    spec.message?.let {
                        Text(it, style = IosType.footnote, color = c.secondaryLabel, textAlign = TextAlign.Center)
                    }
                }
                Hairline()
            }
            spec.actions.forEachIndexed { i, action ->
                if (i > 0) Hairline()
                Text(
                    action.title,
                    style = IosType.body.copy(fontSize = 20.sp),
                    color = if (action.destructive) c.red else c.accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            action.onClick()
                        }
                        .padding(vertical = 16.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Annulla",
            style = IosType.body.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            color = c.accent,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(28.dp), tint = cancelBg, blurRadius = 14.dp)
                .clickable(onClick = onDismiss)
                .padding(vertical = 16.dp),
        )
    }
}

@Composable
private fun PromptDialog(spec: PromptSpec, onDismiss: () -> Unit) {
    val c = Ios.colors
    var value by remember(spec) {
        mutableStateOf(TextFieldValue(spec.initial, TextRange(0, spec.initial.length)))
    }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(spec) {
        delay(120)
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    Box(
        Modifier
            .fillMaxSize()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(300.dp)
                .glass(
                    RoundedCornerShape(30.dp),
                    tint = if (c.isDark) Color(0xD92A2A2D) else Color(0xE6F6F6F8),
                    blurRadius = 14.dp,
                )
                .plainClickable { }
        ) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(spec.title, style = IosType.headline, color = c.label, textAlign = TextAlign.Center)
                if (spec.message != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(spec.message, style = IosType.footnote, color = c.label, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(14.dp))
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    textStyle = IosType.body.copy(color = c.label),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (c.isDark) Color(0xFF1C1C1E) else Color.White)
                        .border(0.5.dp, c.separator, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    decorationBox = { inner ->
                        Box {
                            if (value.text.isEmpty()) {
                                Text(spec.placeholder, style = IosType.body, color = c.tertiaryLabel)
                            }
                            inner()
                        }
                    },
                )
            }
            Hairline()
            Row(Modifier.fillMaxWidth().height(46.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            keyboard?.hide()
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Annulla", style = IosType.body, color = c.accent)
                }
                Box(
                    Modifier
                        .width(0.5.dp)
                        .fillMaxHeight()
                        .background(c.separator)
                )
                val enabled = value.text.isNotBlank()
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(enabled = enabled) {
                            keyboard?.hide()
                            onDismiss()
                            spec.onConfirm(value.text.trim())
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        spec.confirm,
                        style = IosType.headline,
                        color = if (enabled) c.accent else c.accent.copy(alpha = 0.35f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumPickerSheet(
    vm: GalleryViewModel,
    items: List<MediaItem>,
    onClose: () -> Unit,
    onNewAlbum: () -> Unit,
) {
    val c = Ios.colors
    val library by vm.library.collectAsStateWithLifecycle()
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.75f).dp
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .glass(
                RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                tint = if (c.isDark) Color(0xD91C1C1E) else Color(0xE6F2F2F7),
                blurRadius = 16.dp,
            )
            .plainClickable { }
            .navigationBarsPadding()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            BarTextButton("Annulla", onClose, Modifier.align(Alignment.CenterStart).padding(start = 8.dp))
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Aggiungi ad album", style = IosType.headline, color = c.label)
                Text(
                    if (items.size == 1) "1 elemento" else "${items.size} elementi",
                    style = IosType.caption,
                    color = c.secondaryLabel,
                )
            }
        }
        Hairline()
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNewAlbum)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.card),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = c.accent, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text("Nuovo album…", style = IosType.body, color = c.accent)
                }
            }
            items(library.userAlbums, key = { it.key }) { album ->
                AlbumPickerRow(album) {
                    album.userAlbumId?.let { vm.addToAlbum(it, items) }
                    onClose()
                }
            }
            if (library.userAlbums.isEmpty()) {
                item {
                    Text(
                        "Non hai ancora creato album. Gli album sono raccolte personali: una foto può stare in più album senza essere duplicata.",
                        style = IosType.footnote,
                        color = c.secondaryLabel,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun AlbumPickerRow(album: AlbumInfo, onClick: () -> Unit) {
    val c = Ios.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (album.cover != null) {
            MediaThumbnail(
                album.cover,
                Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.placeholder)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(album.title, style = IosType.body, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(album.count.toString(), style = IosType.subhead, color = c.secondaryLabel)
        }
    }
}
