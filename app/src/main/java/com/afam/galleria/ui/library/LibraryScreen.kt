package com.afam.galleria.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afam.galleria.GalleryViewModel
import com.afam.galleria.data.DayGroup
import com.afam.galleria.data.Keys
import com.afam.galleria.data.Library
import com.afam.galleria.data.MediaItem
import com.afam.galleria.data.MonthGroup
import com.afam.galleria.data.YearGroup
import com.afam.galleria.ui.components.EmptyState
import com.afam.galleria.ui.components.LocalOverlay
import com.afam.galleria.ui.components.MediaGrid
import com.afam.galleria.ui.components.MediaThumbnail
import com.afam.galleria.ui.components.PillButton
import com.afam.galleria.ui.components.PillIconButton
import com.afam.galleria.ui.components.RegisteredThumbnail
import com.afam.galleria.ui.components.SegmentedControl
import com.afam.galleria.ui.components.SelectionState
import com.afam.galleria.ui.components.SheetAction
import com.afam.galleria.ui.components.Thumbs
import com.afam.galleria.ui.components.iosClickable
import com.afam.galleria.ui.nav.LocalNavigator
import com.afam.galleria.ui.nav.LocalThumbRegistry
import com.afam.galleria.ui.nav.Route
import com.afam.galleria.ui.theme.Ios
import com.afam.galleria.ui.theme.IosType
import com.afam.galleria.util.Dates
import com.afam.galleria.util.ShareUtil
import kotlinx.coroutines.launch
import java.time.LocalDate

private val MODES = listOf("Anni", "Mesi", "Giorni", "Tutte le foto")
private const val MODE_YEARS = 0
private const val MODE_MONTHS = 1
private const val MODE_DAYS = 2
private const val MODE_ALL = 3

@Composable
fun LibraryScreen(
    vm: GalleryViewModel,
    selection: SelectionState,
    bottomBarHeight: Dp,
    isActive: Boolean,
) {
    val library by vm.library.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val overlay = LocalOverlay.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val c = Ios.colors
    val scope = rememberCoroutineScope()

    val newestFirst = library.local.newestFirst
    var mode by rememberSaveable { mutableIntStateOf(MODE_ALL) }

    val gridState = rememberLazyGridState()
    val daysState = rememberLazyListState()
    val monthsState = rememberLazyListState()
    val yearsState = rememberLazyListState()

    val items = remember(library) { if (library.local.newestFirst) library.all else library.all.asReversed() }
    val days = remember(library) { if (library.local.newestFirst) library.days else library.days.asReversed() }
    val months = remember(library) { if (library.local.newestFirst) library.months else library.months.asReversed() }
    val years = remember(library) { if (library.local.newestFirst) library.years else library.years.asReversed() }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topPad = statusTop + 56.dp
    val bottomPad = bottomBarHeight + 64.dp

    // Come su iOS, le foto più recenti sono in fondo: all'avvio si parte dal fondo.
    var gridPositioned by rememberSaveable { mutableStateOf(false) }
    var daysPositioned by rememberSaveable { mutableStateOf(false) }
    var monthsPositioned by rememberSaveable { mutableStateOf(false) }
    var yearsPositioned by rememberSaveable { mutableStateOf(false) }
    var lastOrder by rememberSaveable { mutableStateOf(newestFirst) }

    LaunchedEffect(library.loaded, items.size, newestFirst) {
        if (!library.loaded || items.isEmpty()) return@LaunchedEffect
        if (lastOrder != newestFirst) {
            lastOrder = newestFirst
            gridPositioned = false
            daysPositioned = false
            monthsPositioned = false
            yearsPositioned = false
        }
        if (!gridPositioned) {
            gridState.scrollToItem(if (newestFirst) 0 else items.lastIndex)
            gridPositioned = true
        }
    }
    LaunchedEffect(mode, library.loaded) {
        if (!library.loaded) return@LaunchedEffect
        when (mode) {
            MODE_DAYS -> if (!daysPositioned && days.isNotEmpty()) {
                daysState.scrollToItem(if (newestFirst) 0 else days.lastIndex); daysPositioned = true
            }
            MODE_MONTHS -> if (!monthsPositioned && months.isNotEmpty()) {
                monthsState.scrollToItem(if (newestFirst) 0 else months.lastIndex); monthsPositioned = true
            }
            MODE_YEARS -> if (!yearsPositioned && years.isNotEmpty()) {
                yearsState.scrollToItem(if (newestFirst) 0 else years.lastIndex); yearsPositioned = true
            }
        }
    }

    BackHandler(enabled = isActive && selection.active && !nav.hasEntries) { selection.exit() }

    val title by remember(items, days) {
        derivedStateOf {
            when (mode) {
                MODE_ALL -> {
                    val visible = gridState.layoutInfo.visibleItemsInfo
                    val first = visible.firstOrNull()?.let { items.getOrNull(it.index) }
                    val last = visible.lastOrNull()?.let { items.getOrNull(it.index) }
                    if (first != null && last != null) Dates.range(first.dateTaken, last.dateTaken) else "Libreria"
                }
                MODE_DAYS -> days.getOrNull(daysState.firstVisibleItemIndex)
                    ?.let { Dates.monthYear(it.items.first().dateTaken) } ?: "Libreria"
                else -> "Libreria"
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.background)
    ) {
        when {
            !library.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.secondaryLabel)
            }
            items.isEmpty() -> EmptyState(
                Icons.Outlined.PhotoLibrary,
                "Nessuna foto o video",
                "Le foto e i video che scatti o salvi sul telefono appariranno qui.",
                Modifier.fillMaxSize(),
            )
            else -> Crossfade(targetState = mode, animationSpec = tween(220), label = "mode") { m ->
                when (m) {
                    MODE_ALL -> MediaGrid(
                        items = items,
                        state = gridState,
                        columns = library.local.gridColumns,
                        selection = selection,
                        favorites = library.favorites,
                        onOpen = { item, rect -> nav.push(Route.Viewer(Keys.ALL, item.id, origin = rect)) },
                        onColumnsChange = { vm.setGridColumns(it) },
                        contentPadding = PaddingValues(top = topPad, bottom = bottomPad),
                        fastScrollTop = topPad,
                        fastScrollBottom = bottomPad,
                        modifier = Modifier.fillMaxSize(),
                    )
                    MODE_DAYS -> DaysView(
                        days = days,
                        state = daysState,
                        library = library,
                        contentPadding = PaddingValues(top = topPad, bottom = bottomPad),
                    )
                    MODE_MONTHS -> MonthsView(
                        months = months,
                        state = monthsState,
                        library = library,
                        contentPadding = PaddingValues(top = topPad, bottom = bottomPad),
                        onOpen = { month ->
                            val target = if (newestFirst) month.items.first() else month.items.last()
                            val index = items.indexOfFirst { it.id == target.id }.coerceAtLeast(0)
                            scope.launch {
                                gridState.scrollToItem(index)
                                gridPositioned = true
                                mode = MODE_ALL
                            }
                        },
                    )
                    else -> YearsView(
                        years = years,
                        state = yearsState,
                        library = library,
                        contentPadding = PaddingValues(top = topPad, bottom = bottomPad),
                        onOpen = { year ->
                            val index = months.indexOfFirst { it.year == year.year }.coerceAtLeast(0)
                            scope.launch {
                                monthsState.scrollToItem(index)
                                monthsPositioned = true
                                mode = MODE_MONTHS
                            }
                        },
                    )
                }
            }
        }

        // Intestazione sfumata con data e pulsanti.
        Box(
            Modifier
                .fillMaxWidth()
                .height(topPad + 28.dp)
                .background(
                    Brush.verticalGradient(
                        0f to c.background.copy(alpha = 0.96f),
                        0.55f to c.background.copy(alpha = 0.80f),
                        1f to Color.Transparent,
                    )
                )
        )
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (selection.active) {
                        when (selection.count) {
                            0 -> "Seleziona elementi"
                            1 -> "1 elemento"
                            else -> "${selection.count} elementi"
                        }
                    } else title,
                    style = IosType.title2.copy(fontSize = 24.sp),
                    color = c.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (mode == MODE_ALL && items.isNotEmpty()) {
                PillButton(
                    text = if (selection.active) "Annulla" else "Seleziona",
                    onClick = { if (selection.active) selection.exit() else selection.start() },
                    highlighted = selection.active,
                )
            }
            if (!selection.active) {
                Spacer(Modifier.padding(start = 8.dp))
                PillIconButton(Icons.Rounded.MoreHoriz, "Altro", onClick = {
                    overlay.actionSheet(
                        actions = listOf(
                            SheetAction(if (newestFirst) "Mostra prima i meno recenti" else "Mostra prima i più recenti") {
                                vm.setNewestFirst(!newestFirst)
                            },
                            SheetAction("Apri Fotocamera") { ShareUtil.openCamera(context) },
                            SheetAction("Impostazioni") { nav.push(Route.Settings) },
                        )
                    )
                })
            }
        }

        // Selettore Anni / Mesi / Giorni / Tutte le foto.
        AnimatedVisibility(
            visible = !selection.active && items.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomBarHeight + 12.dp, start = 20.dp, end = 20.dp),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            SegmentedControl(
                options = MODES,
                selected = mode,
                onSelect = { mode = it },
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

private fun coverOf(items: List<MediaItem>, library: Library): MediaItem? =
    items.firstOrNull { library.isFavorite(it) && !it.isVideo }
        ?: items.firstOrNull { !it.isVideo && !it.isScreenshot && it.aspect in 0.6f..1.9f }
        ?: items.firstOrNull()

@Composable
private fun DaysView(
    days: List<DayGroup>,
    state: LazyListState,
    library: Library,
    contentPadding: PaddingValues,
) {
    LazyColumn(state = state, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        items(days, key = { it.epochDay }) { day -> DayCard(day, library) }
    }
}

@Composable
private fun DayCard(day: DayGroup, library: Library) {
    val c = Ios.colors
    val nav = LocalNavigator.current
    val registry = LocalThumbRegistry.current
    val photos = day.items.count { !it.isVideo }
    val videos = day.items.size - photos
    val shown = remember(day) {
        val sorted = day.items.sortedByDescending { if (library.isFavorite(it)) 1 else 0 }
        sorted.take(4)
    }
    fun open(item: MediaItem) {
        nav.push(Route.Viewer(Keys.day(day.epochDay), item.id, origin = registry[item.id]))
    }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .iosClickable { nav.push(Route.Album(Keys.day(day.epochDay))) }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(Dates.dayTitle(day.epochDay), style = IosType.title3.copy(fontWeight = FontWeight.Bold), color = c.label)
                Text(Dates.count(photos, videos), style = IosType.footnote, color = c.secondaryLabel)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = c.tertiaryLabel)
        }
        Spacer(Modifier.height(8.dp))
        val shape = RoundedCornerShape(14.dp)
        when (shown.size) {
            1 -> RegisteredThumbnail(
                shown[0],
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(shape)
                    .iosClickable { open(shown[0]) },
                size = Thumbs.LARGE,
            )
            2 -> Row(Modifier.fillMaxWidth().clip(shape), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                shown.forEach { item ->
                    RegisteredThumbnail(
                        item,
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.8f)
                            .iosClickable { open(item) },
                        size = Thumbs.LARGE,
                    )
                }
            }
            else -> Column(Modifier.fillMaxWidth().clip(shape), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                RegisteredThumbnail(
                    shown[0],
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f)
                        .iosClickable { open(shown[0]) },
                    size = Thumbs.LARGE,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    shown.drop(1).forEachIndexed { i, item ->
                        Box(Modifier.weight(1f).aspectRatio(1f)) {
                            RegisteredThumbnail(
                                item,
                                Modifier
                                    .fillMaxSize()
                                    .iosClickable { open(item) },
                            )
                            val extra = day.items.size - shown.size
                            if (i == shown.size - 2 && extra > 0) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.45f))
                                        .iosClickable { nav.push(Route.Album(Keys.day(day.epochDay))) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("+$extra", style = IosType.title2, color = Color.White)
                                }
                            }
                        }
                    }
                    repeat(3 - (shown.size - 1)) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun MonthsView(
    months: List<MonthGroup>,
    state: LazyListState,
    library: Library,
    contentPadding: PaddingValues,
    onOpen: (MonthGroup) -> Unit,
) {
    LazyColumn(state = state, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        items(months, key = { it.key }) { month ->
            val cover = remember(month) { coverOf(month.items, library) }
            val sameYear = month.year == LocalDate.now().year
            BigCard(
                cover = cover,
                title = Dates.monthName(month.month),
                subtitle = if (sameYear) Dates.count(month.items.count { !it.isVideo }, month.items.count { it.isVideo })
                else month.year.toString(),
                aspect = 1.45f,
                titleSize = 30,
                onClick = { onOpen(month) },
            )
        }
    }
}

@Composable
private fun YearsView(
    years: List<YearGroup>,
    state: LazyListState,
    library: Library,
    contentPadding: PaddingValues,
    onOpen: (YearGroup) -> Unit,
) {
    LazyColumn(state = state, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        items(years, key = { it.year }) { year ->
            val cover = remember(year) { coverOf(year.items, library) }
            BigCard(
                cover = cover,
                title = year.year.toString(),
                subtitle = Dates.count(year.items.count { !it.isVideo }, year.items.count { it.isVideo }),
                aspect = 1.0f,
                titleSize = 40,
                onClick = { onOpen(year) },
            )
        }
    }
}

@Composable
private fun BigCard(
    cover: MediaItem?,
    title: String,
    subtitle: String,
    aspect: Float,
    titleSize: Int,
    onClick: () -> Unit,
) {
    val c = Ios.colors
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(18.dp))
            .background(c.placeholder)
            .iosClickable(onClick = onClick)
    ) {
        if (cover != null) {
            MediaThumbnail(cover, Modifier.fillMaxSize(), size = Thumbs.LARGE)
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.45f to Color.Transparent,
                        1f to Color.Transparent,
                    )
                )
        )
        val shadow = Shadow(Color.Black.copy(alpha = 0.4f), blurRadius = 8f)
        Column(Modifier.padding(18.dp)) {
            Text(
                title,
                style = IosType.title1.copy(fontSize = titleSize.sp, lineHeight = (titleSize + 4).sp, shadow = shadow),
                color = Color.White,
            )
            Text(
                subtitle,
                style = IosType.subhead.copy(fontWeight = FontWeight.SemiBold, shadow = shadow),
                color = Color.White.copy(alpha = 0.92f),
            )
        }
    }
}
