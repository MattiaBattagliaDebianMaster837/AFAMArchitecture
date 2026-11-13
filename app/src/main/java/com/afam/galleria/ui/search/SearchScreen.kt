package com.afam.galleria.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afam.galleria.GalleryViewModel
import com.afam.galleria.data.AlbumInfo
import com.afam.galleria.data.Keys
import com.afam.galleria.data.Library
import com.afam.galleria.data.MediaItem
import com.afam.galleria.ui.albums.AlbumTile
import com.afam.galleria.ui.albums.typeIcon
import com.afam.galleria.ui.components.BarTextButton
import com.afam.galleria.ui.components.EmptyState
import com.afam.galleria.ui.components.GroupedSection
import com.afam.galleria.ui.components.LargeTitle
import com.afam.galleria.ui.components.ListRow
import com.afam.galleria.ui.components.MediaGrid
import com.afam.galleria.ui.components.SectionHeader
import com.afam.galleria.ui.components.SelectionState
import com.afam.galleria.ui.components.iosClickable
import com.afam.galleria.ui.nav.LocalNavigator
import com.afam.galleria.ui.nav.Route
import com.afam.galleria.ui.theme.Ios
import com.afam.galleria.ui.theme.IosType
import com.afam.galleria.util.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.time.LocalDate

class SearchResults(val albums: List<AlbumInfo>, val items: List<MediaItem>)

/** Ricerca per album, nome file, mese, anno, giorno della settimana e tipo. */
object Search {
    private val accents = Regex("\\p{Mn}+")
    private val spaces = Regex("\\s+")

    fun normalize(s: String): String =
        accents.replace(Normalizer.normalize(s.lowercase(Dates.locale), Normalizer.Form.NFD), "")

    private val monthNames = (1..12).associateWith { normalize(Dates.monthName(it)) }
    private val weekdayNames = listOf("lunedi", "martedi", "mercoledi", "giovedi", "venerdi", "sabato", "domenica")

    fun run(library: Library, query: String): SearchResults {
        val tokens = normalize(query).split(spaces).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return SearchResults(emptyList(), emptyList())

        val albums = (library.myAlbums + library.mediaTypes).filter { a ->
            val title = normalize(a.title)
            tokens.all { title.contains(it) }
        }

        val albumNames = HashMap<Long, MutableList<String>>()
        library.local.albums.forEach { album ->
            val n = normalize(album.name)
            album.items.forEach { albumNames.getOrPut(it) { mutableListOf() }.add(n) }
        }
        val today = LocalDate.now(Dates.zone)
        val items = library.all.filter { item ->
            val date = Dates.localDate(item.dateTaken)
            val bucket = normalize(item.bucketName)
            val name = normalize(item.name)
            tokens.all { t -> matches(item, t, date, today, bucket, name, library, albumNames[item.id]) }
        }
        return SearchResults(albums, items)
    }

    private fun matches(
        item: MediaItem,
        t: String,
        date: LocalDate,
        today: LocalDate,
        bucket: String,
        name: String,
        library: Library,
        albums: List<String>?,
    ): Boolean {
        if (bucket.contains(t) || name.contains(t)) return true
        if (albums != null && albums.any { it.contains(t) }) return true
        if (t.length >= 3 && monthNames[date.monthValue]!!.startsWith(t)) return true
        if (t.length >= 3 && weekdayNames[date.dayOfWeek.value - 1].startsWith(t)) return true
        if (t.length == 4 && t.all { it.isDigit() } && date.year.toString() == t) return true
        if (t.length <= 2 && t.all { it.isDigit() } && date.dayOfMonth.toString() == t) return true
        return when {
            t.startsWith("vid") || t.startsWith("filmat") -> item.isVideo
            t == "foto" || t.startsWith("immagin") -> !item.isVideo
            t.startsWith("screen") || t.startsWith("schermat") -> item.isScreenshot || item.isScreenRecording
            t.startsWith("preferit") || t.startsWith("cuor") -> library.isFavorite(item)
            t.startsWith("selfie") -> item.isSelfie
            t == "gif" -> item.isGif
            t.startsWith("panoram") -> item.isPanorama
            t == "raw" -> item.isRaw
            t == "oggi" -> date == today
            t == "ieri" -> date == today.minusDays(1)
            else -> false
        }
    }
}

@Composable
fun SearchScreen(vm: GalleryViewModel, bottomBarHeight: Dp) {
    val library by vm.library.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val focusManager = LocalFocusManager.current
    val c = Ios.colors
    var query by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<SearchResults?>(null) }
    val selection = remember { SelectionState() }
    val gridState = rememberLazyGridState()

    LaunchedEffect(query, library) {
        if (query.isBlank()) {
            results = null
            return@LaunchedEffect
        }
        delay(160)
        results = withContext(Dispatchers.Default) { Search.run(library, query) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
    ) {
        AnimatedVisibility(
            visible = !focused && query.isEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(44.dp))
                LargeTitle("Cerca")
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.fill)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = IosType.body.copy(color = c.label),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focused = it.isFocused },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text("Album, mesi, luoghi, tipi…", style = IosType.body, color = c.secondaryLabel, maxLines = 1)
                            }
                            inner()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Cancel,
                        contentDescription = "Cancella",
                        tint = c.tertiaryLabel,
                        modifier = Modifier
                            .size(20.dp)
                            .iosClickable { query = "" },
                    )
                }
            }
            AnimatedVisibility(
                visible = focused || query.isNotEmpty(),
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut(),
            ) {
                BarTextButton("Annulla", {
                    query = ""
                    focusManager.clearFocus()
                })
            }
        }

        val r = results
        when {
            query.isBlank() -> Suggestions(library, bottomBarHeight) { query = it }
            r == null -> Spacer(Modifier.weight(1f))
            r.items.isEmpty() && r.albums.isEmpty() -> EmptyState(
                Icons.Outlined.SearchOff,
                "Nessun risultato",
                "Prova a cercare un album, un mese, un anno o un tipo (es. «video», «agosto 2024»).",
                Modifier
                    .fillMaxWidth()
                    .padding(top = 60.dp),
            )
            else -> {
                val ids = remember(r) { r.items.map { it.id } }
                MediaGrid(
                    items = r.items,
                    state = gridState,
                    columns = 4,
                    selection = selection,
                    favorites = library.favorites,
                    selectionEnabled = false,
                    onOpen = { item, rect ->
                        focusManager.clearFocus()
                        nav.push(Route.Viewer(Keys.IDS, item.id, ids = ids, origin = rect))
                    },
                    contentPadding = PaddingValues(bottom = bottomBarHeight + 16.dp),
                    header = {
                        Column {
                            if (r.albums.isNotEmpty()) {
                                SectionHeader("Album")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(r.albums, key = { it.key }) { album ->
                                        AlbumTile(album, 120.dp, onClick = { nav.push(Route.Album(album.key)) })
                                    }
                                }
                            }
                            if (r.items.isNotEmpty()) {
                                Row(
                                    Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.Bottom,
                                ) {
                                    Text("Foto", style = IosType.title2, color = c.label, modifier = Modifier.weight(1f))
                                    Text(Dates.elements(r.items.size), style = IosType.subhead, color = c.secondaryLabel)
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Suggestions(library: Library, bottomBarHeight: Dp, onQuery: (String) -> Unit) {
    val nav = LocalNavigator.current
    val c = Ios.colors
    val width = LocalConfiguration.current.screenWidthDp.dp
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomBarHeight + 24.dp),
    ) {
        if (library.myAlbums.isNotEmpty()) {
            item { SectionHeader("Album") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(library.myAlbums, key = { it.key }) { album ->
                        AlbumTile(album, (width - 56.dp) / 3, onClick = { nav.push(Route.Album(album.key)) })
                    }
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
        if (library.years.isNotEmpty()) {
            item { SectionHeader("Anni") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(library.years, key = { it.year }) { y ->
                        Chip(y.year.toString()) { onQuery(y.year.toString()) }
                    }
                }
            }
        }
        if (library.months.isNotEmpty()) {
            item { SectionHeader("Mesi recenti") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(library.months.take(12), key = { it.key }) { m ->
                        val label = Dates.monthYear(m.year, m.month)
                        Chip(label) { nav.push(Route.Album(Keys.month(m.key))) }
                    }
                }
            }
        }
        item {
            Text(
                "Suggerimento: puoi combinare più parole, ad esempio «video agosto» o «Camera 2024».",
                style = IosType.footnote,
                color = c.secondaryLabel,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            )
        }
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    val c = Ios.colors
    Text(
        text,
        style = IosType.subhead.copy(fontWeight = FontWeight.Medium),
        color = c.accent,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(c.fill)
            .iosClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
