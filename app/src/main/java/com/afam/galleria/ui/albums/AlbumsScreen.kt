package com.afam.galleria.ui.albums

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.Gif
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Panorama
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afam.galleria.GalleryViewModel
import com.afam.galleria.data.AlbumInfo
import com.afam.galleria.data.AlbumKind
import com.afam.galleria.data.Keys
import com.afam.galleria.ui.components.BarIconButton
import com.afam.galleria.ui.components.GroupedSection
import com.afam.galleria.ui.components.LargeTitle
import com.afam.galleria.ui.components.ListRow
import com.afam.galleria.ui.components.LocalOverlay
import com.afam.galleria.ui.components.MediaThumbnail
import com.afam.galleria.ui.components.OverlayState
import com.afam.galleria.ui.components.SectionHeader
import com.afam.galleria.ui.components.SheetAction
import com.afam.galleria.ui.components.SystemGray
import com.afam.galleria.ui.nav.LocalNavigator
import com.afam.galleria.ui.nav.Navigator
import com.afam.galleria.ui.nav.Route
import com.afam.galleria.ui.glass.GlassScreen
import com.afam.galleria.ui.theme.Ios
import com.afam.galleria.ui.theme.IosType
import com.afam.galleria.util.Auth
import com.afam.galleria.ui.glass.GlassBox
import com.afam.galleria.ui.glass.GlassIconButton

fun typeIcon(key: String): ImageVector = when (key.substringAfter(':')) {
    "video" -> Icons.Outlined.Videocam
    "selfie" -> Icons.Outlined.Face
    "screenshot" -> Icons.Outlined.Screenshot
    "screenrec" -> Icons.Outlined.Smartphone
    "pano" -> Icons.Outlined.Panorama
    "gif" -> Icons.Outlined.Gif
    "raw" -> Icons.Outlined.Camera
    else -> Icons.Outlined.PhotoLibrary
}

/** Mostra le opzioni per un album creato dall'utente (rinomina / elimina). */
fun showAlbumOptions(album: AlbumInfo, vm: GalleryViewModel, overlay: OverlayState, onDeleted: () -> Unit = {}) {
    val id = album.userAlbumId ?: return
    overlay.actionSheet(
        title = album.title,
        actions = listOf(
            SheetAction("Rinomina album") {
                overlay.prompt(
                    title = "Rinomina album",
                    initial = album.title,
                    confirm = "Salva",
                ) { vm.renameAlbum(id, it) }
            },
            SheetAction("Elimina album", destructive = true) {
                overlay.actionSheet(
                    message = "Vuoi eliminare l'album «${album.title}»? Le foto non verranno eliminate.",
                    actions = listOf(SheetAction("Elimina album", destructive = true) {
                        vm.deleteAlbum(id)
                        onDeleted()
                    }),
                )
            },
        ),
    )
}

fun openPrivate(context: android.content.Context, vm: GalleryViewModel, nav: Navigator, route: Route, title: String) {
    if (vm.store.value.lockPrivate) {
        Auth.authenticate(context, title, onSuccess = { nav.push(route) })
    } else {
        nav.push(route)
    }
}

@Composable
fun AlbumsScreen(vm: GalleryViewModel, bottomBarHeight: Dp) {
    val library by vm.library.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val overlay = LocalOverlay.current
    val context = LocalContext.current
    val c = Ios.colors
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val tileWidth = ((screenWidth - 32.dp - 12.dp) / 2.15f)

    val locations by vm.locations.collectAsStateWithLifecycle()
    val geotagged = locations.points.count { (id, _) -> library.byId[id]?.let { !library.isHidden(it) } == true }

    GlassBox(
        modifier = Modifier.fillMaxSize(),
        content = {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(c.background),
        contentPadding = PaddingValues(top = statusTop + 56.dp, bottom = bottomBarHeight + 24.dp),
    ) {
        item { LargeTitle("Album") }
        item {
            SectionHeader("I miei album", action = "Vedi tutti", onAction = { nav.push(Route.AllAlbums) })
        }
        item {
            val albums = library.myAlbums
            LazyHorizontalGrid(
                rows = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height((tileWidth + 48.dp) * 2 + 12.dp),
            ) {
                items(albums, key = { it.key }) { album ->
                    AlbumTile(
                        album = album,
                        width = tileWidth,
                        onClick = { nav.push(Route.Album(album.key)) },
                        onLongClick = if (album.kind == AlbumKind.USER) {
                            { showAlbumOptions(album, vm, overlay) }
                        } else null,
                    )
                }
            }
        }
        if (library.mediaTypes.isNotEmpty()) {
            item { SectionHeader("Tipologie di media") }
            item {
                GroupedSection {
                    library.mediaTypes.forEachIndexed { i, type ->
                        ListRow(
                            title = type.title,
                            icon = typeIcon(type.key),
                            value = type.count.toString(),
                            showDivider = i < library.mediaTypes.lastIndex,
                            onClick = { nav.push(Route.Album(type.key)) },
                        )
                    }
                }
            }
        }
        item { SectionHeader("Luoghi") }
        item {
            GroupedSection {
                ListRow(
                    title = "Mappa",
                    icon = Icons.Outlined.Map,
                    value = if (geotagged > 0) geotagged.toString() else null,
                    showDivider = false,
                    onClick = { vm.openMap() },
                )
            }
        }
        item { SectionHeader("Altro") }
        item {
            val lock = library.local.lockPrivate
            GroupedSection {
                ListRow(
                    title = "Duplicati",
                    icon = Icons.Outlined.FileCopy,
                    value = library.duplicates.size.toString(),
                    onClick = { nav.push(Route.Duplicates) },
                )
                if (library.local.showHiddenAlbum) {
                    ListRow(
                        title = "Nascosti",
                        icon = Icons.Outlined.VisibilityOff,
                        value = if (lock) null else library.hidden.size.toString(),
                        trailing = if (lock) {
                            { Icon(Icons.Rounded.Lock, null, tint = SystemGray, modifier = Modifier.size(16.dp)) }
                        } else null,
                        onClick = {
                            openPrivate(context, vm, nav, Route.Album(Keys.HIDDEN), "Sblocca l'album Nascosti")
                        },
                    )
                }
                ListRow(
                    title = "Eliminati di recente",
                    icon = Icons.Outlined.Delete,
                    trailing = if (lock) {
                        { Icon(Icons.Rounded.Lock, null, tint = SystemGray, modifier = Modifier.size(16.dp)) }
                    } else null,
                    showDivider = false,
                    onClick = {
                        openPrivate(context, vm, nav, Route.Trash, "Sblocca Eliminati di recente")
                    },
                )
            }
        }
    }
        },
        overlay = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(Icons.Rounded.Add, "Nuovo album", {
                    overlay.prompt(
                        title = "Nuovo album",
                        message = "Inserisci un nome per questo album.",
                    ) { name ->
                        val id = vm.createAlbum(name)
                        nav.push(Route.AddToAlbum(id))
                    }
                }, tint = c.accent)
                Spacer(Modifier.weight(1f))
                GlassIconButton(Icons.Rounded.Settings, "Impostazioni", { nav.push(Route.Settings) })
            }
        },
    )
}

@Composable
fun AlbumTile(
    album: AlbumInfo,
    width: Dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val c = Ios.colors
    Column(
        Modifier
            .width(width)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
    ) {
        Box(
            Modifier
                .size(width)
                .clip(RoundedCornerShape(10.dp))
                .background(c.placeholder),
            contentAlignment = Alignment.Center,
        ) {
            if (album.cover != null) {
                MediaThumbnail(album.cover, Modifier.fillMaxSize())
            } else {
                Icon(Icons.Outlined.PhotoLibrary, null, tint = c.tertiaryLabel, modifier = Modifier.size(44.dp))
            }
            if (album.key == Keys.FAV) {
                Icon(
                    Icons.Rounded.Favorite, null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(album.title, style = IosType.subhead, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(album.count.toString(), style = IosType.subhead, color = c.secondaryLabel, maxLines = 1)
    }
}

/** Tutti gli album, in griglia a due colonne. */
@Composable
fun AllAlbumsScreen(vm: GalleryViewModel) {
    val library by vm.library.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val overlay = LocalOverlay.current
    val c = Ios.colors
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val tileWidth = (screenWidth - 32.dp - 16.dp) / 2
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val albums = library.myAlbums
    GlassScreen(
        background = c.background,
        content = {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = statusTop + 52.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(albums, key = { it.key }) { album ->
                AlbumTile(
                    album = album,
                    width = tileWidth,
                    onClick = { nav.push(Route.Album(album.key)) },
                    onLongClick = if (album.kind == AlbumKind.USER) {
                        { showAlbumOptions(album, vm, overlay) }
                    } else null,
                )
            }
        }
        },
        overlay = {
        com.afam.galleria.ui.components.IosNavBar(
            title = "I miei album",
            backLabel = "Album",
            onBack = { nav.pop() },
        ) {
            BarIconButton(Icons.Rounded.Add, "Nuovo album", {
                overlay.prompt(title = "Nuovo album", message = "Inserisci un nome per questo album.") { name ->
                    val id = vm.createAlbum(name)
                    nav.push(Route.AddToAlbum(id))
                }
            })
        }
        },
    )
}
