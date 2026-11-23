package com.afam.galleria.ui.albums

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afam.galleria.GalleryViewModel
import com.afam.galleria.data.Keys
import com.afam.galleria.data.MediaItem
import com.afam.galleria.ui.components.BarIconButton
import com.afam.galleria.ui.components.BarTextButton
import com.afam.galleria.ui.components.BottomToolbar
import com.afam.galleria.ui.components.EmptyState
import com.afam.galleria.ui.components.IosNavBar
import com.afam.galleria.ui.components.LocalOverlay
import com.afam.galleria.ui.components.MediaGrid
import com.afam.galleria.ui.components.RegisteredThumbnail
import com.afam.galleria.ui.components.SelectionState
import com.afam.galleria.ui.components.SelectionToolbar
import com.afam.galleria.ui.components.SheetAction
import com.afam.galleria.ui.components.iosClickable
import com.afam.galleria.ui.nav.LocalNavigator
import com.afam.galleria.ui.nav.LocalThumbRegistry
import com.afam.galleria.ui.nav.Route
import com.afam.galleria.ui.glass.GlassScreen
import com.afam.galleria.ui.glass.GlassTextButton
import com.afam.galleria.ui.theme.Ios
import com.afam.galleria.ui.theme.IosType
import com.afam.galleria.util.Dates

/** Intestazione grande con titolo e conteggio, mostrata in cima alla griglia. */
@Composable
private fun AlbumHeader(title: String, items: List<MediaItem>, note: String? = null) {
    val c = Ios.colors
    Column(Modifier.padding(top = 8.dp, bottom = 12.dp)) {
        Text(
            title,
            style = IosType.largeTitle,
            color = c.label,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            Dates.count(items.count { !it.isVideo }, items.count { it.isVideo }),
            style = IosType.subhead,
            color = c.secondaryLabel,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (note != null) {
            Text(
                note,
                style = IosType.footnote,
                color = c.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
    }
}

@Composable
fun AlbumDetailScreen(vm: GalleryViewModel, key: String, isTop: Boolean) {
    val library by vm.library.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val overlay = LocalOverlay.current
    val c = Ios.colors
    val selection = remember { SelectionState() }
    val gridState = rememberLazyGridState()
    val newestFirst = library.local.newestFirst
    val items = remember(library, key) {
        val list = library.itemsFor(key)
        if (newestFirst) list else list.asReversed()
    }
    val title = remember(library, key) { library.titleFor(key) }
    val userAlbumId = if (key.startsWith("album:")) key.substringAfter(':') else null
    val hiddenMode = key == Keys.HIDDEN
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var positioned by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(items.size) {
        if (!positioned && items.isNotEmpty()) {
            if (!newestFirst) gridState.scrollToItem(items.lastIndex + 1)
            positioned = true
        }
    }
    val titleVisible by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 120 } }

    BackHandler(enabled = isTop && selection.active) { selection.exit() }

    GlassScreen(
        background = c.background,
        content = {
        if (items.isEmpty() && library.loaded) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                EmptyState(
                    Icons.Outlined.PhotoLibrary,
                    if (hiddenMode) "Nessun elemento nascosto" else "Nessuna foto o video",
                    when {
                        hiddenMode -> "Gli elementi che nascondi appariranno qui."
                        userAlbumId != null -> "Aggiungi foto e video a questo album."
                        else -> "Non ci sono elementi da mostrare."
                    },
                )
                if (userAlbumId != null) {
                    BarTextButton("Aggiungi foto", { nav.push(Route.AddToAlbum(userAlbumId)) }, bold = true)
                }
            }
        } else {
            MediaGrid(
                items = items,
                state = gridState,
                columns = library.local.gridColumns,
                selection = selection,
                favorites = library.favorites,
                onOpen = { item, rect -> nav.push(Route.Viewer(key, item.id, origin = rect)) },
                onColumnsChange = { vm.setGridColumns(it) },
                contentPadding = PaddingValues(top = statusTop + 44.dp, bottom = navBottom + 70.dp),
                header = {
                    AlbumHeader(
                        title,
                        items,
                        note = if (hiddenMode) "Gli elementi nascosti non compaiono nella Libreria, negli album o nei ricordi." else null,
                    )
                },
                fastScrollTop = statusTop + 44.dp,
                fastScrollBottom = navBottom + 70.dp,
                modifier = Modifier.fillMaxSize(),
            )
        }

        },
        overlay = {
        IosNavBar(
            title = title,
            backLabel = "Indietro",
            onBack = { nav.pop() },
            titleVisible = titleVisible,
            showDivider = titleVisible,
        ) {
            if (userAlbumId != null && !selection.active) {
                BarIconButton(Icons.Rounded.Add, "Aggiungi foto", { nav.push(Route.AddToAlbum(userAlbumId)) })
                BarIconButton(Icons.Rounded.MoreHoriz, "Opzioni album", {
                    library.userAlbums.firstOrNull { it.userAlbumId == userAlbumId }?.let { info ->
                        showAlbumOptions(info, vm, overlay, onDeleted = { nav.pop() })
                    }
                })
            }
            if (items.isNotEmpty()) {
                BarTextButton(
                    if (selection.active) "Annulla" else "Seleziona",
                    { if (selection.active) selection.exit() else selection.start() },
                    bold = selection.active,
                )
            }
        }

        if (selection.active) {
            SelectionToolbar(
                selection = selection,
                source = items,
                vm = vm,
                favorites = library.favorites,
                albumId = userAlbumId,
                hiddenMode = hiddenMode,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        },
    )
}

/** Selezione di foto dalla libreria da aggiungere a un album. */
@Composable
fun AddToAlbumScreen(vm: GalleryViewModel, albumId: String) {
    val library by vm.library.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val c = Ios.colors
    val selection = remember { SelectionState().apply { start() } }
    val gridState = rememberLazyGridState()
    val items = remember(library) { library.all }
    val albumName = library.local.albums.firstOrNull { it.id == albumId }?.name ?: "Album"
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    GlassScreen(
        background = c.background,
        content = {
        MediaGrid(
            items = items,
            state = gridState,
            columns = 4,
            selection = selection,
            favorites = library.favorites,
            onOpen = { item, _ -> selection.toggle(item.id) },
            contentPadding = PaddingValues(top = statusTop + 45.dp, bottom = navBottom + 16.dp),
            fastScrollTop = statusTop + 45.dp,
            modifier = Modifier.fillMaxSize(),
        )
        },
        overlay = {
        IosNavBar(
            title = if (selection.count == 0) "Aggiungi a «$albumName»" else "${selection.count} selezionati",
            backLabel = null,
            onBack = null,
        ) {
            BarTextButton(
                "Aggiungi",
                {
                    vm.addToAlbum(albumId, selection.items(items))
                    nav.pop()
                },
                bold = true,
                enabled = selection.count > 0,
            )
        }
        GlassTextButton(
            "Annulla",
            { nav.pop() },
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp),
        )
        },
    )
}

/** Eliminati di recente: elementi nel cestino di sistema, con i giorni rimanenti. */
@Composable
fun TrashScreen(vm: GalleryViewModel, isTop: Boolean) {
    val library by vm.library.collectAsStateWithLifecycle()
    val trash by vm.trash.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val overlay = LocalOverlay.current
    val c = Ios.colors
    val selection = remember { SelectionState() }
    val gridState = rememberLazyGridState()
    val items = trash ?: emptyList()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(Unit) { vm.loadTrash() }
    BackHandler(enabled = isTop && selection.active) { selection.exit() }

    GlassScreen(
        background = c.background,
        content = {
        if (trash != null && items.isEmpty()) {
            EmptyState(
                Icons.Outlined.Delete,
                "Nessun elemento",
                "Le foto e i video eliminati restano qui per 30 giorni prima di essere rimossi definitivamente.",
                Modifier.align(Alignment.Center),
            )
        } else {
            MediaGrid(
                items = items,
                state = gridState,
                columns = library.local.gridColumns,
                selection = selection,
                favorites = emptySet(),
                onOpen = { item, rect -> nav.push(Route.Viewer(Keys.TRASH, item.id, origin = rect)) },
                onColumnsChange = { vm.setGridColumns(it) },
                contentPadding = PaddingValues(top = statusTop + 44.dp, bottom = navBottom + 70.dp),
                header = {
                    AlbumHeader(
                        "Eliminati di recente",
                        items,
                        note = "Gli elementi mostrano i giorni rimanenti prima dell'eliminazione definitiva.",
                    )
                },
                badge = { item -> daysLeft(item) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        },
        overlay = {
        IosNavBar(
            title = "Eliminati di recente",
            backLabel = "Album",
            onBack = { nav.pop() },
        ) {
            if (items.isNotEmpty()) {
                BarTextButton(
                    if (selection.active) "Annulla" else "Seleziona",
                    { if (selection.active) selection.exit() else selection.start() },
                    bold = selection.active,
                )
            }
        }
        if (items.isNotEmpty()) {
            val targets = if (selection.active) selection.items(items) else items
            val enabled = targets.isNotEmpty()
            BottomToolbar(Modifier.align(Alignment.BottomCenter)) {
                BarTextButton(
                    if (selection.active) "Elimina" else "Elimina tutto",
                    {
                        overlay.actionSheet(
                            message = "Questa azione non può essere annullata.",
                            actions = listOf(SheetAction(
                                if (targets.size == 1) "Elimina definitivamente" else "Elimina ${targets.size} elementi",
                                destructive = true,
                            ) { vm.deleteForever(targets) { selection.exit() } }),
                        )
                    },
                    color = c.red,
                    enabled = enabled,
                )
                BarTextButton(
                    if (selection.active) "Recupera" else "Recupera tutto",
                    {
                        vm.restore(targets)
                        selection.exit()
                    },
                    enabled = enabled,
                )
            }
        }
        },
    )
}

fun daysLeft(item: MediaItem): String? {
    if (item.expires <= 0) return null
    val ms = item.expires * 1000 - System.currentTimeMillis()
    val days = (ms / 86_400_000L).toInt()
    return when {
        days <= 0 -> "Oggi"
        days == 1 -> "1 giorno"
        else -> "$days giorni"
    }
}

/** Duplicati: gruppi di elementi identici, con il pulsante "Unisci". */
@Composable
fun DuplicatesScreen(vm: GalleryViewModel) {
    val library by vm.library.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val overlay = LocalOverlay.current
    val registry = LocalThumbRegistry.current
    val c = Ios.colors
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val groups = library.duplicates

    GlassScreen(
        background = c.background,
        content = {
        if (groups.isEmpty()) {
            EmptyState(
                Icons.Outlined.FileCopy,
                "Nessun duplicato",
                "Non ci sono foto o video duplicati nella libreria.",
                Modifier.align(Alignment.Center),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = statusTop + 52.dp, bottom = navBottom + 24.dp),
            ) {
                item {
                    Text(
                        "Unisci i duplicati per tenere una sola copia: le altre verranno spostate in Eliminati di recente.",
                        style = IosType.footnote,
                        color = c.secondaryLabel,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(groups, key = { g -> g.first().id }) { group ->
                    Column(Modifier.padding(vertical = 10.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    Dates.capitalize(Dates.shortDate(group.first().dateTaken)),
                                    style = IosType.headline,
                                    color = c.label,
                                )
                                Text(
                                    "${group.size} elementi · ${Dates.fileSize(group.first().size)}",
                                    style = IosType.footnote,
                                    color = c.secondaryLabel,
                                )
                            }
                            Text(
                                "Unisci",
                                style = IosType.subhead.copy(fontWeight = FontWeight.SemiBold),
                                color = c.accent,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(c.fill)
                                    .iosClickable {
                                        overlay.actionSheet(
                                            message = "Verrà mantenuta una sola copia; ${group.size - 1} elementi verranno spostati in Eliminati di recente.",
                                            actions = listOf(SheetAction("Unisci ${group.size} elementi", destructive = true) {
                                                vm.trash(group.drop(1))
                                            }),
                                        )
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(group, key = { it.id }) { item ->
                                RegisteredThumbnail(
                                    item,
                                    Modifier
                                        .size(110.dp)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .iosClickable {
                                            nav.push(
                                                Route.Viewer(
                                                    Keys.IDS,
                                                    item.id,
                                                    ids = group.map { it.id },
                                                    origin = registry[item.id],
                                                )
                                            )
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
        },
        overlay = {
        IosNavBar(title = "Duplicati", backLabel = "Album", onBack = { nav.pop() })
        },
    )
}
