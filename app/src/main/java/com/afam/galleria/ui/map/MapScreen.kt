package com.afam.galleria.ui.map

import android.Manifest
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afam.galleria.GalleryViewModel
import com.afam.galleria.data.Keys
import com.afam.galleria.data.Library
import com.afam.galleria.ui.components.MediaThumbnail
import com.afam.galleria.ui.components.iosClickable
import com.afam.galleria.ui.glass.GlassBox
import com.afam.galleria.ui.glass.GlassIconButton
import com.afam.galleria.ui.glass.GlassPanel
import com.afam.galleria.ui.glass.GlassTextButton
import com.afam.galleria.ui.glass.glass
import com.afam.galleria.ui.nav.LocalNavigator
import com.afam.galleria.ui.nav.Route
import com.afam.galleria.ui.theme.Ios
import com.afam.galleria.ui.theme.IosType
import com.afam.galleria.util.Dates
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MapScreen(
    vm: GalleryViewModel,
    controller: PhotoMapController,
    bottomBarHeight: Dp,
) {
    val library by vm.library.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    val focus by vm.mapFocus.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val c = Ios.colors
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshLocations()
    }

    LaunchedEffect(c.isDark) { controller.setDark(c.isDark) }
    LaunchedEffect(library, locations.points) { controller.setData(library, locations.points) }
    LaunchedEffect(focus, locations.points) {
        val id = focus ?: return@LaunchedEffect
        val point = locations.points[id] ?: return@LaunchedEffect
        controller.focus(point)
        vm.mapFocus.value = null
    }
    SideEffect {
        controller.onOpen = { ids, start -> nav.push(Route.Viewer(Keys.IDS, start, ids = ids)) }
        controller.attributionTop = with(density) { (statusTop + 78.dp).roundToPx() }
        with(density) {
            controller.mapPadding = intArrayOf(
                48.dp.roundToPx(),
                (statusTop + 90.dp).roundToPx(),
                48.dp.roundToPx(),
                (bottomBarHeight + 170.dp).roundToPx(),
            )
        }
    }

    // La mappa disegna solo mentre la scheda è visibile e l'app è in primo piano.
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.start()
                Lifecycle.Event.ON_RESUME -> controller.resume()
                Lifecycle.Event.ON_PAUSE -> controller.pause()
                Lifecycle.Event.ON_STOP -> controller.stop()
                else -> Unit
            }
        }
        val state = lifecycleOwner.lifecycle.currentState
        if (state.isAtLeast(Lifecycle.State.STARTED)) controller.start()
        if (state.isAtLeast(Lifecycle.State.RESUMED)) controller.resume()
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.stop()
        }
    }

    val withLocation = locations.points.count { (id, _) -> library.byId[id]?.let { !library.isHidden(it) } == true }

    GlassBox(
        modifier = Modifier
            .fillMaxSize()
            .background(c.groupedBackground),
        content = {
            AndroidView(
                factory = { controller.mapView.also { (it.parent as? ViewGroup)?.removeView(it) } },
                modifier = Modifier.fillMaxSize(),
            )
        },
        overlay = {
            // Titolo e pulsanti in alto.
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    Modifier
                        .glass(RoundedCornerShape(22.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Mappa", style = IosType.headline, color = c.label)
                    Text(
                        when {
                            locations.indexing && locations.total > 0 -> {
                                val pct = (locations.done * 100f / locations.total).roundToInt()
                                "Analisi delle posizioni… $pct%"
                            }
                            !locations.loaded -> "Caricamento…"
                            else -> if (withLocation == 1) "1 elemento con posizione" else "${Dates.format(withLocation)} elementi con posizione"
                        },
                        style = IosType.caption,
                        color = c.secondaryLabel,
                    )
                    if (controller.offline) {
                        Text("Mappa non disponibile senza connessione", style = IosType.caption, color = c.red)
                    }
                }
                Spacer(Modifier.weight(1f))
                GlassIconButton(Icons.Rounded.Public, "Mostra tutte", { controller.fitAll(animated = true) })
            }

            if (!controller.styleLoaded) {
                CircularProgressIndicator(
                    color = c.secondaryLabel,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            val showEmpty = locations.loaded && !locations.indexing && withLocation == 0
            AnimatedVisibility(
                visible = !locations.hasPermission || showEmpty,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                GlassPanel(Modifier.widthIn(max = 420.dp)) {
                    Column(
                        Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Rounded.Map, null, tint = c.accent, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(10.dp))
                        if (!locations.hasPermission) {
                            Text("Mostra dove hai scattato le foto", style = IosType.headline, color = c.label, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Per collocare le foto sulla mappa, Galleria deve poter leggere la posizione salvata nelle foto. Le posizioni restano sul telefono.",
                                style = IosType.subhead,
                                color = c.secondaryLabel,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(14.dp))
                            GlassTextButton(
                                "Consenti",
                                { permissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) },
                                prominent = true,
                            )
                        } else {
                            Text("Nessuna foto con posizione", style = IosType.headline, color = c.label, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Le foto appariranno qui se la fotocamera salva la posizione. Puoi attivarla dalle impostazioni della Fotocamera («Salva posizione»).",
                                style = IosType.subhead,
                                color = c.secondaryLabel,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            if (withLocation > 0) {
                PhotosSheet(
                    ids = controller.visibleIds,
                    library = library,
                    title = controller.place,
                    bottomBarHeight = bottomBarHeight,
                    onOpen = { ids, start -> nav.push(Route.Viewer(Keys.IDS, start, ids = ids)) },
                )
            }
        },
    )
}

/** Foglio di vetro con le foto dell'area inquadrata: si trascina tra tre altezze. */
@Composable
private fun PhotosSheet(
    ids: List<Long>,
    library: Library,
    title: String?,
    bottomBarHeight: Dp,
    onOpen: (List<Long>, Long) -> Unit,
) {
    val c = Ios.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val gridState = rememberLazyGridState()
    val items = remember(ids, library) { ids.mapNotNull { library.byId[it] } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val full = constraints.maxHeight.toFloat()
        val expanded = with(density) { (statusTop + 72.dp).toPx() }
        val collapsed = full - with(density) { (bottomBarHeight + 118.dp).toPx() }
        val half = (full * 0.5f).coerceIn(expanded, collapsed)
        val anchors = remember(expanded, half, collapsed) { floatArrayOf(expanded, half, collapsed) }

        var sheetY by remember { mutableFloatStateOf(Float.NaN) }
        if (sheetY.isNaN() || sheetY > collapsed) sheetY = collapsed
        var animation by remember { mutableStateOf<Job?>(null) }

        fun dragBy(delta: Float): Float {
            animation?.cancel()
            val old = sheetY
            sheetY = (old + delta).coerceIn(expanded, collapsed)
            return sheetY - old
        }

        fun settle(velocity: Float) {
            val target = if (abs(velocity) > 900f) {
                if (velocity < 0) anchors.filter { it < sheetY - 1f }.maxOrNull() ?: expanded
                else anchors.filter { it > sheetY + 1f }.minOrNull() ?: collapsed
            } else {
                anchors.minBy { abs(it - sheetY) }
            }
            animation?.cancel()
            animation = scope.launch {
                animate(sheetY, target, velocity, spring(dampingRatio = 0.86f, stiffness = 420f)) { v, _ -> sheetY = v }
            }
        }

        val connection = remember(anchors) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y < 0 && sheetY > expanded + 0.5f) return Offset(0f, dragBy(available.y))
                    return Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (available.y > 0 && source == NestedScrollSource.UserInput) return Offset(0f, dragBy(available.y))
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (sheetY > expanded + 0.5f && sheetY < collapsed - 0.5f) {
                        settle(available.y)
                        return available
                    }
                    return Velocity.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (anchors.none { abs(it - sheetY) < 1f }) settle(available.y)
                    return Velocity.Zero
                }
            }
        }

        val sheetHeight = with(density) { (full - expanded).toDp() }
        Column(
            Modifier
                .offset { IntOffset(0, sheetY.roundToInt()) }
                .fillMaxWidth()
                .height(sheetHeight)
                .glass(
                    RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                    tint = if (c.isDark) Color(0xB31C1C1E) else Color(0xC7F7F7F9),
                    blurRadius = 16.dp,
                )
                .nestedScroll(connection)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { dragBy(it) },
                        onDragStopped = { v -> settle(v) },
                    )
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .width(38.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(c.tertiaryLabel)
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            title ?: "Foto in questa zona",
                            style = IosType.title3.copy(fontWeight = FontWeight.Bold),
                            color = c.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (items.isEmpty()) "Nessuna foto qui: sposta o rimpicciolisci la mappa"
                            else Dates.count(items.count { !it.isVideo }, items.count { it.isVideo }),
                            style = IosType.footnote,
                            color = c.secondaryLabel,
                        )
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                state = gridState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomBarHeight + 24.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(items, key = { it.id }, contentType = { "photo" }) { item ->
                    MediaThumbnail(
                        item,
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .iosClickable { onOpen(ids, item.id) },
                    )
                }
            }
        }
    }
}
