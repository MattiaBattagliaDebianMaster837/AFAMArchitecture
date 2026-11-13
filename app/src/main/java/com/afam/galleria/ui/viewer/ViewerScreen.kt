package com.afam.galleria.ui.viewer

import android.content.ContentUris
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afam.galleria.GalleryViewModel
import com.afam.galleria.data.Keys
import com.afam.galleria.data.MediaItem
import com.afam.galleria.data.Memory
import com.afam.galleria.ui.albums.daysLeft
import com.afam.galleria.ui.components.BarIconButton
import com.afam.galleria.ui.components.BarTextButton
import com.afam.galleria.ui.components.BottomToolbar
import com.afam.galleria.ui.components.IosNavBar
import com.afam.galleria.ui.components.LocalOverlay
import com.afam.galleria.ui.components.MediaThumbnail
import com.afam.galleria.ui.components.SheetAction
import com.afam.galleria.ui.components.Thumbs
import com.afam.galleria.ui.components.iosClickable
import com.afam.galleria.ui.components.rememberDeleteAction
import com.afam.galleria.ui.nav.LocalNavigator
import com.afam.galleria.ui.nav.LocalThumbRegistry
import com.afam.galleria.ui.nav.Route
import com.afam.galleria.ui.theme.Ios
import com.afam.galleria.ui.theme.IosType
import com.afam.galleria.util.Dates
import com.afam.galleria.util.ShareUtil
import com.afam.galleria.util.SystemBarsEffect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Animazione di apertura/chiusura tra miniatura e schermo intero. */
private class ZoomTransition(
    val item: MediaItem,
    val from: Rect,
    val to: Rect?,
    val fromAlpha: Float,
)

@Composable
fun ViewerScreen(
    vm: GalleryViewModel,
    route: Route.Viewer,
    isTop: Boolean,
    onClosed: () -> Unit,
) {
    val library by vm.library.collectAsStateWithLifecycle()
    val trash by vm.trash.collectAsStateWithLifecycle()
    val external by vm.externalItem.collectAsStateWithLifecycle()

    val isTrash = route.source == Keys.TRASH
    val isSingle = route.source == Keys.SINGLE

    val items: List<MediaItem> = remember(library, trash, external, route) {
        when {
            isSingle -> listOfNotNull(external)
            isTrash -> trash.orEmpty()
            route.ids != null -> route.ids.mapNotNull { library.byId[it] }
            else -> library.itemsFor(route.source).let { if (library.local.newestFirst) it else it.asReversed() }
        }
    }

    val ready = when {
        isSingle -> external != null
        isTrash -> trash != null
        else -> library.loaded
    }

    if (!ready) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Color.White)
        }
        BackHandler(enabled = isTop) { onClosed() }
        return
    }
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onClosed() }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
        return
    }
    ViewerContent(vm, route, items, isTop, isTrash, isSingle, onClosed)
}

@Composable
private fun ViewerContent(
    vm: GalleryViewModel,
    route: Route.Viewer,
    items: List<MediaItem>,
    isTop: Boolean,
    isTrash: Boolean,
    isSingle: Boolean,
    onClosed: () -> Unit,
) {
    val library by vm.library.collectAsStateWithLifecycle()
    val lastSaved by vm.lastSaved.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val registry = LocalThumbRegistry.current
    val overlay = LocalOverlay.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val c = Ios.colors
    val delete = rememberDeleteAction(vm)
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val startIndex = remember { items.indexOfFirst { it.id == route.startId }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = startIndex) { items.size }
    val currentIndex = pagerState.currentPage.coerceIn(0, items.lastIndex)
    val current = items[currentIndex]

    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var showInfo by remember { mutableStateOf(false) }
    var transition by remember { mutableStateOf<ZoomTransition?>(null) }
    val progress = remember { Animatable(1f) }
    var closing by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf(route.origin == null) }

    // Elemento corrente: le griglie sottostanti lo tengono visibile.
    LaunchedEffect(pagerState, items) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page -> nav.viewerItemId = items.getOrNull(page)?.id }
    }
    DisposableEffect(Unit) {
        onDispose { nav.viewerItemId = null }
    }

    // Dopo il salvataggio dall'editor, mostra la nuova foto.
    LaunchedEffect(lastSaved, items) {
        val uri = lastSaved ?: return@LaunchedEffect
        val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return@LaunchedEffect
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) {
            pagerState.scrollToPage(index)
            vm.lastSaved.value = null
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val container = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val screenHeight = container.height

        fun fitRect(item: MediaItem): Rect {
            val bmp = Thumbs.peekBest(item)
            val aspect = if (bmp != null && bmp.height > 0) bmp.width.toFloat() / bmp.height else item.aspect
            val s = fitSize(container, aspect)
            return Rect(
                Offset((container.width - s.width) / 2f, (container.height - s.height) / 2f),
                s,
            )
        }

        val dragFraction = (abs(dragOffset.y) / (screenHeight * 0.6f)).coerceIn(0f, 1f)
        val dragScale = 1f - 0.35f * (dragOffset.y.coerceAtLeast(0f) / screenHeight).coerceIn(0f, 1f)

        // Apertura con zoom dalla miniatura.
        LaunchedEffect(Unit) {
            val origin = route.origin
            if (origin != null && !opened) {
                transition = ZoomTransition(current, origin, fitRect(current), 0f)
                progress.snapTo(0f)
                progress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 420f))
                transition = null
                opened = true
            }
        }

        fun close() {
            if (closing) return
            closing = true
            showInfo = false
            val item = items.getOrNull(pagerState.currentPage)
            if (item == null) {
                onClosed(); return
            }
            val fit = fitRect(item)
            val center = fit.center + dragOffset
            val w = fit.width * dragScale
            val h = fit.height * dragScale
            val from = Rect(center.x - w / 2f, center.y - h / 2f, center.x + w / 2f, center.y + h / 2f)
            val target = if (isSingle || zoomed) null else registry[item.id]
            transition = ZoomTransition(item, from, target, 1f - dragFraction)
            scope.launch {
                progress.snapTo(0f)
                progress.animateTo(1f, if (target != null) spring<Float>(dampingRatio = 0.9f, stiffness = 480f) else tween<Float>(200))
                onClosed()
            }
        }

        BackHandler(enabled = isTop && !closing && !showInfo) { close() }

        val showChrome = chrome && dragOffset == Offset.Zero && transition == null && !closing
        val blackBackground = c.isDark || !chrome || dragOffset != Offset.Zero
        val bgColor by animateColorAsState(if (blackBackground) Color.Black else Color.White, tween(200), label = "bg")
        SystemBarsEffect(darkBackground = blackBackground, hidden = !chrome)

        val t = transition
        val bgAlpha = when {
            t == null -> 1f - dragFraction
            t.to == null && closing -> t.fromAlpha * (1f - progress.value)
            closing -> t.fromAlpha * (1f - progress.value)
            else -> progress.value
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = bgAlpha }
                .background(bgColor)
        )

        HorizontalPager(
            state = pagerState,
            key = { items.getOrNull(it)?.id ?: it },
            beyondViewportPageCount = 1,
            pageSpacing = 16.dp,
            userScrollEnabled = !zoomed && dragOffset == Offset.Zero,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (t == null) 1f else 0f),
        ) { page ->
            val item = items[page]
            val isCurrent = page == pagerState.currentPage
            val onDismissDrag: (Offset) -> Unit = { delta ->
                if (!closing) dragOffset += delta
            }
            val onDismissEnd: (Float) -> Unit = { velocity ->
                val threshold = with(density) { 110.dp.toPx() }
                if (dragOffset.y > threshold || velocity > 1400f || dragOffset.y < -threshold * 1.5f) {
                    close()
                } else {
                    val start = dragOffset
                    scope.launch {
                        animate(0f, 1f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f)) { v, _ ->
                            dragOffset = start * (1f - v)
                        }
                        dragOffset = Offset.Zero
                    }
                }
            }
            if (item.isVideo) {
                VideoPage(
                    item = item,
                    isCurrent = isCurrent,
                    active = opened && !closing,
                    chromeVisible = showChrome,
                    controlsBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 52.dp,
                    dragOffset = if (isCurrent) dragOffset else Offset.Zero,
                    dragScale = if (isCurrent) dragScale else 1f,
                    onTap = { chrome = !chrome },
                    onDismissDrag = onDismissDrag,
                    onDismissEnd = onDismissEnd,
                )
            } else {
                PhotoPage(
                    item = item,
                    isCurrent = isCurrent,
                    dragOffset = dragOffset,
                    dragScale = dragScale,
                    onTap = { chrome = !chrome },
                    onZoomChange = { zoomed = it },
                    onDismissDrag = onDismissDrag,
                    onDismissEnd = onDismissEnd,
                )
            }
        }

        // Immagine animata durante l'apertura e la chiusura.
        if (t != null) {
            val p = progress.value
            val rect = if (closing) {
                if (t.to != null) lerp(t.from, t.to, p) else {
                    val s = lerp(1f, 0.92f, p)
                    val cx = t.from.center
                    Rect(cx.x - t.from.width * s / 2, cx.y - t.from.height * s / 2, cx.x + t.from.width * s / 2, cx.y + t.from.height * s / 2)
                }
            } else {
                lerp(t.from, t.to ?: t.from, p)
            }
            val alpha = if (closing && t.to == null) 1f - p else 1f
            val bitmap = remember(t.item.cacheKey) { Thumbs.peekBest(t.item) }
            Box(
                Modifier
                    .offset { IntOffset(rect.left.toInt(), rect.top.toInt()) }
                    .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                    .graphicsLayer { this.alpha = alpha }
                    .clip(RoundedCornerShape(0.dp))
            ) {
                if (bitmap != null) {
                    val image = remember(bitmap) { bitmap.asImageBitmap() }
                    Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    MediaThumbnail(t.item, Modifier.fillMaxSize(), placeholder = Color.Transparent)
                }
            }
        }

        // Barra superiore.
        AnimatedVisibility(
            visible = showChrome,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
        ) {
            IosNavBar(
                title = "",
                titleVisible = false,
                onBack = { close() },
                backLabel = null,
                background = c.bar,
            ) {
                if (!isTrash && current.isEditable) {
                    BarTextButton("Modifica", { nav.push(Route.Editor(current)) })
                }
                if (!isSingle && !isTrash) {
                    BarIconButton(Icons.Rounded.MoreHoriz, "Altro", {
                        showMoreMenu(current, items, currentIndex, vm, overlay, nav, context, library.isHidden(current))
                    })
                }
            }
        }
        AnimatedVisibility(
            visible = showChrome,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusTop),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
        ) {
            Column(
                Modifier.height(44.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text(
                    if (isSingle) current.name else Dates.viewerTitle(current.dateTaken),
                    style = IosType.headline,
                    color = c.label,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 110.dp),
                )
                if (!isSingle) {
                    Text(Dates.time(current.dateTaken), style = IosType.caption, color = c.secondaryLabel)
                }
            }
        }

        // Barra inferiore con la pellicola di miniature.
        AnimatedVisibility(
            visible = showChrome,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
        ) {
            Column {
                if (!current.isVideo && items.size > 1) {
                    Filmstrip(
                        items = items,
                        current = currentIndex,
                        onSelect = { scope.launch { pagerState.scrollToPage(it) } },
                    )
                }
                BottomToolbar(background = c.bar) {
                    when {
                        isTrash -> {
                            BarTextButton("Elimina", {
                                overlay.actionSheet(
                                    message = "Questa azione non può essere annullata.",
                                    actions = listOf(SheetAction("Elimina definitivamente", destructive = true) {
                                        vm.deleteForever(listOf(current))
                                    }),
                                )
                            }, color = c.red)
                            Text(daysLeft(current) ?: "", style = IosType.footnote, color = c.secondaryLabel)
                            BarTextButton("Recupera", { vm.restore(listOf(current)) })
                        }
                        isSingle -> {
                            BarIconButton(Icons.Rounded.IosShare, "Condividi", { ShareUtil.share(context, listOf(current)) })
                            BarIconButton(Icons.Outlined.Info, "Info", { showInfo = true })
                            BarTextButton("Apri con", { ShareUtil.openWith(context, current) })
                        }
                        else -> {
                            val fav = library.isFavorite(current)
                            BarIconButton(Icons.Rounded.IosShare, "Condividi", { ShareUtil.share(context, listOf(current)) })
                            HeartButton(fav) { vm.toggleFavorite(listOf(current)) }
                            BarIconButton(Icons.Outlined.Info, "Info", { showInfo = true })
                            BarIconButton(Icons.Outlined.Delete, "Elimina", { delete(listOf(current)) {} })
                        }
                    }
                }
            }
        }

        if (showInfo) {
            InfoSheet(
                item = current,
                vm = vm,
                favorite = library.isFavorite(current),
                onDismiss = { showInfo = false },
            )
        }
    }
}

@Composable
private fun HeartButton(favorite: Boolean, onClick: () -> Unit) {
    val c = Ios.colors
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .size(44.dp)
            .iosClickable {
                onClick()
                scope.launch {
                    scale.snapTo(0.6f)
                    scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 600f))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (favorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
            tint = if (favorite) c.red else c.accent,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
        )
    }
}

@Composable
private fun Filmstrip(items: List<MediaItem>, current: Int, onSelect: (Int) -> Unit) {
    val c = Ios.colors
    val state = rememberLazyListState()
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(c.bar)
            .padding(vertical = 6.dp)
    ) {
        val half = maxWidth / 2 - 22.dp
        LaunchedEffect(current) {
            if (abs(state.firstVisibleItemIndex - current) > 40) state.scrollToItem(current)
            else state.animateScrollToItem(current)
        }
        LazyRow(
            state = state,
            contentPadding = PaddingValues(start = half, end = half),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
                val selected = i == current
                val width by animateDpAsState(if (selected) 44.dp else 30.dp, label = "film")
                MediaThumbnail(
                    item,
                    Modifier
                        .width(width)
                        .height(42.dp)
                        .padding(horizontal = if (selected) 3.dp else 0.dp)
                        .clip(RoundedCornerShape(if (selected) 4.dp else 1.dp))
                        .iosClickable { onSelect(i) },
                    size = Thumbs.SMALL,
                )
            }
        }
    }
}

private fun showMoreMenu(
    item: MediaItem,
    items: List<MediaItem>,
    index: Int,
    vm: GalleryViewModel,
    overlay: com.afam.galleria.ui.components.OverlayState,
    nav: com.afam.galleria.ui.nav.Navigator,
    context: android.content.Context,
    hidden: Boolean,
) {
    val actions = buildList {
        add(SheetAction("Copia") { ShareUtil.copy(context, item) })
        add(SheetAction("Duplica") { vm.duplicate(item) })
        add(SheetAction("Aggiungi ad album") { overlay.pickAlbum(listOf(item)) })
        add(SheetAction(if (hidden) "Mostra" else "Nascondi") { vm.setHidden(listOf(item), !hidden) })
        val slides = items.drop(index).filter { !it.isVideo }.take(200)
        if (slides.size > 1) {
            add(SheetAction("Presentazione") {
                nav.push(
                    Route.MemoryPlayer(
                        Memory(
                            id = "slideshow-${item.id}",
                            title = "",
                            subtitle = "",
                            cover = slides.first(),
                            items = slides,
                        )
                    )
                )
            })
        }
        if (!item.isVideo) add(SheetAction("Imposta come…") { ShareUtil.setAs(context, item) })
        add(SheetAction("Apri con…") { ShareUtil.openWith(context, item) })
        if (item.bucketName.isNotEmpty()) {
            add(SheetAction("Vai all'album «${item.bucketName}»") { nav.push(Route.Album(Keys.bucket(item.bucketId))) })
        }
    }
    overlay.actionSheet(actions = actions)
}
