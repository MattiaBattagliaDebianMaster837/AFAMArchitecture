package com.afam.galleria

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afam.galleria.data.Keys
import com.afam.galleria.data.MediaItem
import com.afam.galleria.ui.albums.AddToAlbumScreen
import com.afam.galleria.ui.albums.AlbumDetailScreen
import com.afam.galleria.ui.albums.AlbumsScreen
import com.afam.galleria.ui.albums.AllAlbumsScreen
import com.afam.galleria.ui.albums.DuplicatesScreen
import com.afam.galleria.ui.albums.TrashScreen
import com.afam.galleria.ui.components.BarTextButton
import com.afam.galleria.ui.components.IosNavBar
import com.afam.galleria.ui.components.GlassTabBar
import com.afam.galleria.ui.components.TabBarHeight
import com.afam.galleria.ui.glass.GlassBox
import com.afam.galleria.ui.glass.LocalGlassEffects
import com.afam.galleria.ui.glass.glass
import com.afam.galleria.ui.map.MapScreen
import com.afam.galleria.ui.map.PhotoMapController
import com.afam.galleria.ui.components.LocalOverlay
import com.afam.galleria.ui.components.MainTab
import com.afam.galleria.ui.components.MediaGrid
import com.afam.galleria.ui.components.OverlayHost
import com.afam.galleria.ui.components.OverlayState
import com.afam.galleria.ui.components.SelectionState
import com.afam.galleria.ui.components.SelectionToolbar
import com.afam.galleria.ui.components.iosClickable
import com.afam.galleria.ui.editor.EditorScreen
import com.afam.galleria.ui.foryou.ForYouScreen
import com.afam.galleria.ui.foryou.MemoryPlayerScreen
import com.afam.galleria.ui.library.LibraryScreen
import com.afam.galleria.ui.nav.LocalNavigator
import com.afam.galleria.ui.nav.LocalThumbRegistry
import com.afam.galleria.ui.nav.NavEntry
import com.afam.galleria.ui.nav.Navigator
import com.afam.galleria.ui.nav.Route
import com.afam.galleria.ui.nav.ThumbRegistry
import com.afam.galleria.ui.search.SearchScreen
import com.afam.galleria.ui.settings.SettingsScreen
import com.afam.galleria.ui.glass.GlassScreen
import com.afam.galleria.ui.glass.GlassTextButton
import com.afam.galleria.ui.theme.Ios
import com.afam.galleria.ui.theme.IosType
import com.afam.galleria.ui.viewer.ViewerScreen
import com.afam.galleria.util.ShareUtil

// ---- Permessi ---------------------------------------------------------------------------------

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

fun hasMediaPermission(context: Context): Boolean = when {
    Build.VERSION.SDK_INT >= 34 ->
        granted(context, Manifest.permission.READ_MEDIA_IMAGES) ||
            granted(context, Manifest.permission.READ_MEDIA_VIDEO) ||
            granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 ->
        granted(context, Manifest.permission.READ_MEDIA_IMAGES) ||
            granted(context, Manifest.permission.READ_MEDIA_VIDEO)
    else -> granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
}

fun isPartialAccess(context: Context): Boolean =
    Build.VERSION.SDK_INT >= 34 &&
        !granted(context, Manifest.permission.READ_MEDIA_IMAGES) &&
        granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        Manifest.permission.ACCESS_MEDIA_LOCATION,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.ACCESS_MEDIA_LOCATION,
    )
    else -> arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.ACCESS_MEDIA_LOCATION,
    )
}

/** Rende un livello "opaco" ai tocchi, così non passano alle schermate sottostanti. */
private fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}

// ---- Radice ------------------------------------------------------------------------------------

@Composable
fun GalleryRoot(
    vm: GalleryViewModel,
    request: LaunchRequest,
    onPicked: (List<MediaItem>) -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val nav = remember { Navigator() }
    val overlay = remember { OverlayState() }
    val registry = remember { ThumbRegistry() }

    var hasPermission by remember { mutableStateOf(hasMediaPermission(context)) }
    var partial by remember { mutableStateOf(isPartialAccess(context)) }
    var requestCount by rememberSaveable { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = hasMediaPermission(context)
        partial = isPartialAccess(context)
        requestCount++
        if (hasPermission) vm.start()
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) vm.start()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = hasMediaPermission(context)
        partial = isPartialAccess(context)
        if (now != hasPermission) hasPermission = now
        else if (now) vm.refresh()
    }

    // Richieste di sistema (eliminazione, recupero...).
    var pending by remember { mutableStateOf<PendingRequest?>(null) }
    val senderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        pending?.onResult(result.resultCode == Activity.RESULT_OK)
        pending = null
    }
    LaunchedEffect(Unit) {
        vm.requests.collect { req ->
            pending = req
            try {
                senderLauncher.launch(IntentSenderRequest.Builder(req.sender).build())
            } catch (e: Exception) {
                pending = null
                req.onResult(false)
            }
        }
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { overlay.toast(it) }
    }

    val glassOn by remember(vm) { vm.store.data }.collectAsStateWithLifecycle()
    val glassEffects = glassOn.glassEffects && !lowRam(context)

    CompositionLocalProvider(
        LocalNavigator provides nav,
        LocalOverlay provides overlay,
        LocalThumbRegistry provides registry,
        LocalGlassEffects provides glassEffects,
    ) {
        // Il vetro dei menu e dei fogli "legge" tutta l'app che sta dietro.
        GlassBox(
            modifier = Modifier.fillMaxSize(),
            content = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Ios.colors.background)
                ) {
                    when (request) {
                        is LaunchRequest.View -> ExternalViewer(vm, request, hasPermission, onFinish)
                        is LaunchRequest.Pick -> if (hasPermission) {
                            PickerScreen(vm, request, onPicked, onCancel = onFinish)
                        } else {
                            PermissionScreen(requestCount > 0) { permissionLauncher.launch(mediaPermissions()) }
                        }
                        LaunchRequest.Main -> if (hasPermission) {
                            MainApp(vm, partial) { permissionLauncher.launch(mediaPermissions()) }
                        } else {
                            PermissionScreen(requestCount > 0) { permissionLauncher.launch(mediaPermissions()) }
                        }
                    }
                }
            },
            overlay = { OverlayHost(overlay, vm) },
        )
    }
}

private fun lowRam(context: Context): Boolean =
    context.getSystemService(android.app.ActivityManager::class.java)?.isLowRamDevice == true

// ---- App principale con schede e livelli -----------------------------------------------------

@Composable
private fun MainApp(vm: GalleryViewModel, partial: Boolean, onManageAccess: () -> Unit) {
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val library by vm.library.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(MainTab.LIBRARY) }
    val librarySelection = remember { SelectionState() }
    val holder = rememberSaveableStateHolder()
    val c = Ios.colors
    var bannerDismissed by rememberSaveable { mutableStateOf(false) }

    // La mappa viene creata la prima volta che si apre la scheda e poi tenuta pronta.
    var mapController by remember { mutableStateOf<PhotoMapController?>(null) }
    val accent = c.accent.toArgb()
    if (tab == MainTab.MAP && mapController == null) {
        mapController = PhotoMapController(context, accent)
    }
    DisposableEffect(Unit) {
        onDispose { mapController?.destroy() }
    }

    // "Mostra sulla mappa" dal pannello Info.
    LaunchedEffect(Unit) {
        var last = vm.openMapRequest.value
        vm.openMapRequest.collect { value ->
            if (value != last) {
                last = value
                librarySelection.exit()
                nav.entries.clear()
                tab = MainTab.MAP
            }
        }
    }

    BackHandler(enabled = nav.hasEntries) { nav.pop() }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeight = navBottom + TabBarHeight

    Box(Modifier.fillMaxSize()) {
        GlassBox(
            modifier = Modifier.fillMaxSize(),
            content = {
                holder.SaveableStateProvider(tab.name) {
                    when (tab) {
                        MainTab.LIBRARY -> LibraryScreen(vm, librarySelection, bottomBarHeight, isActive = !nav.hasEntries)
                        MainTab.FOR_YOU -> ForYouScreen(vm, bottomBarHeight)
                        MainTab.ALBUMS -> AlbumsScreen(vm, bottomBarHeight)
                        MainTab.MAP -> mapController?.let { MapScreen(vm, it, bottomBarHeight) }
                        MainTab.SEARCH -> SearchScreen(vm, bottomBarHeight)
                    }
                }
            },
            overlay = {
                if (tab == MainTab.LIBRARY && librarySelection.active) {
                    SelectionToolbar(
                        selection = librarySelection,
                        source = library.all,
                        vm = vm,
                        favorites = library.favorites,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                } else {
                    GlassTabBar(
                        selected = tab,
                        onSelect = {
                            librarySelection.exit()
                            tab = it
                        },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                if (partial && !bannerDismissed) {
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = bottomBarHeight + 64.dp, start = 16.dp, end = 16.dp)
                            .widthIn(max = 480.dp)
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(24.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Accesso limitato", style = IosType.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.label)
                            Text("Galleria vede solo le foto che hai selezionato.", style = IosType.footnote, color = c.secondaryLabel)
                        }
                        BarTextButton("Gestisci", onManageAccess)
                        BarTextButton("OK", { bannerDismissed = true }, color = c.secondaryLabel)
                    }
                }
            },
        )

        nav.entries.forEach { entry ->
            key(entry.id) { RouteLayer(entry, vm, nav) }
        }
    }
}

@Composable
private fun RouteLayer(entry: NavEntry, vm: GalleryViewModel, nav: Navigator) {
    val route = entry.route
    val isTop = nav.isTop(entry)
    if (route is Route.Viewer) {
        if (!entry.transition.targetState) {
            LaunchedEffect(Unit) { nav.remove(entry) }
            return
        }
        Box(Modifier.fillMaxSize().blockTouches()) {
            ViewerScreen(vm, route, isTop, onClosed = { nav.remove(entry) })
        }
        return
    }

    val state = entry.transition
    LaunchedEffect(state.isIdle, state.currentState, state.targetState) {
        if (state.isIdle && !state.currentState && !state.targetState) nav.remove(entry)
    }
    val (enter, exit) = when (route) {
        is Route.MemoryPlayer, is Route.Editor ->
            (fadeIn(tween(260)) + scaleIn(tween(300), initialScale = 0.94f)) to
                (fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.96f))
        else ->
            slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { it } to
                slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it }
    }
    AnimatedVisibility(visibleState = state, enter = enter, exit = exit) {
        Box(Modifier.fillMaxSize().blockTouches()) {
            when (route) {
                is Route.Album -> AlbumDetailScreen(vm, route.key, isTop)
                Route.AllAlbums -> AllAlbumsScreen(vm)
                Route.Trash -> TrashScreen(vm, isTop)
                Route.Duplicates -> DuplicatesScreen(vm)
                Route.Settings -> SettingsScreen(vm)
                is Route.Editor -> EditorScreen(vm, route.item, isTop) { nav.pop() }
                is Route.MemoryPlayer -> MemoryPlayerScreen(route.memory, isTop) { nav.pop() }
                is Route.AddToAlbum -> AddToAlbumScreen(vm, route.albumId)
                is Route.Viewer -> Unit
            }
        }
    }
}

// ---- Apertura da un'altra app ------------------------------------------------------------------

@Composable
private fun ExternalViewer(
    vm: GalleryViewModel,
    request: LaunchRequest.View,
    hasPermission: Boolean,
    onFinish: () -> Unit,
) {
    val nav = LocalNavigator.current
    val library by vm.library.collectAsStateWithLifecycle()
    val mediaId = remember(request.uri) {
        if (request.uri.authority == "media") runCatching { ContentUris.parseId(request.uri) }.getOrNull() else null
    }
    val route = when {
        hasPermission && mediaId != null && library.loaded &&
            library.all.any { it.id == mediaId } -> Route.Viewer(Keys.ALL, mediaId)
        hasPermission && mediaId != null && !library.loaded -> null
        else -> Route.Viewer(Keys.SINGLE, vm.externalItem.value?.id ?: 0L)
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (route != null) {
            key(route.source) {
                ViewerScreen(vm, route, isTop = !nav.hasEntries, onClosed = onFinish)
            }
        }
        nav.entries.forEach { entry ->
            key(entry.id) { RouteLayer(entry, vm, nav) }
        }
    }
    BackHandler(enabled = nav.hasEntries) { nav.pop() }
}

// ---- Selettore per altre app (ACTION_PICK / GET_CONTENT) ---------------------------------------

@Composable
private fun PickerScreen(
    vm: GalleryViewModel,
    request: LaunchRequest.Pick,
    onPicked: (List<MediaItem>) -> Unit,
    onCancel: () -> Unit,
) {
    val library by vm.library.collectAsStateWithLifecycle()
    val c = Ios.colors
    val type = request.type.orEmpty()
    val items = remember(library, type) {
        when {
            type.contains("image") && !type.contains("video") -> library.all.filter { !it.isVideo }
            type.contains("video") && !type.contains("image") -> library.all.filter { it.isVideo }
            else -> library.all
        }
    }
    val selection = remember { SelectionState().apply { if (request.multiple) start() } }
    val gridState = rememberLazyGridState()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BackHandler { onCancel() }

    GlassScreen(
        background = c.background,
        content = {
        MediaGrid(
            items = items,
            state = gridState,
            columns = library.local.gridColumns.coerceIn(3, 5),
            selection = selection,
            favorites = library.favorites,
            selectionEnabled = request.multiple,
            onOpen = { item, _ ->
                if (request.multiple) selection.toggle(item.id) else onPicked(listOf(item))
            },
            onColumnsChange = { vm.setGridColumns(it) },
            contentPadding = PaddingValues(top = statusTop + 45.dp, bottom = navBottom + 16.dp),
            fastScrollTop = statusTop + 45.dp,
            modifier = Modifier.fillMaxSize(),
        )
        },
        overlay = {
        IosNavBar(
            title = if (request.multiple && selection.count > 0) "${selection.count} selezionati" else "Scegli",
        ) {
            if (request.multiple) {
                BarTextButton(
                    "Aggiungi",
                    { onPicked(selection.items(items)) },
                    bold = true,
                    enabled = selection.count > 0,
                )
            }
        }
        GlassTextButton(
            "Annulla",
            onCancel,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp),
        )
        },
    )
}

// ---- Benvenuto / permessi ----------------------------------------------------------------------

@Composable
private fun PermissionScreen(askedBefore: Boolean, onRequest: () -> Unit) {
    val c = Ios.colors
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Box(
            Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFFB547), Color(0xFFFF5277), Color(0xFF7A5CFF))
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Benvenuto in Galleria",
            style = IosType.largeTitle,
            color = c.label,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Tutte le tue foto e i tuoi video, ordinati e pronti da rivivere.",
            style = IosType.body,
            color = c.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))
        Feature(Icons.Rounded.PhotoLibrary, "Libreria", "Sfoglia per anni, mesi e giorni, con lo zoom a pizzico.")
        Feature(Icons.Rounded.Collections, "Album", "Crea album, ritrova video, screenshot, preferiti e duplicati.")
        Feature(Icons.Rounded.AutoAwesome, "Ricordi", "Presentazioni automatiche dei tuoi momenti migliori.")
        Feature(Icons.Rounded.Tune, "Modifica", "Regola luce e colori, applica filtri, ritaglia e ruota.")
        Spacer(Modifier.height(32.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.accent)
                .iosClickable(onClick = onRequest)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Consenti accesso a foto e video", style = IosType.headline, color = Color.White)
        }
        if (askedBefore) {
            Spacer(Modifier.height(12.dp))
            BarTextButton("Apri le impostazioni dell'app", { ShareUtil.openAppSettings(context) })
            Text(
                "Se hai negato l'accesso, abilitalo da Impostazioni › App › Galleria › Autorizzazioni.",
                style = IosType.footnote,
                color = c.secondaryLabel,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    val c = Ios.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = IosType.headline, color = c.label)
            Text(text, style = IosType.subhead, color = c.secondaryLabel)
        }
    }
}
