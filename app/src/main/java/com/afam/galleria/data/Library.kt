package com.afam.galleria.data

import androidx.compose.runtime.Immutable
import com.afam.galleria.util.Dates
import java.time.LocalDate
import java.time.ZoneId

enum class AlbumKind { SMART, USER, FOLDER, TYPE }

@Immutable
data class AlbumInfo(
    val key: String,
    val title: String,
    val count: Int,
    val cover: MediaItem?,
    val kind: AlbumKind,
    val userAlbumId: String? = null,
)

@Immutable
data class DayGroup(val epochDay: Long, val items: List<MediaItem>)

@Immutable
data class MonthGroup(val year: Int, val month: Int, val items: List<MediaItem>) {
    val key: Int get() = year * 100 + month
}

@Immutable
data class YearGroup(val year: Int, val items: List<MediaItem>)

/** Chiavi che identificano una "sorgente" di elementi (album, tipo, giorno...). */
object Keys {
    const val ALL = "all"
    const val FAV = "fav"
    const val HIDDEN = "hidden"
    const val TRASH = "trash"
    const val SINGLE = "single"
    const val IDS = "ids"
    fun bucket(id: Long) = "bucket:$id"
    fun album(id: String) = "album:$id"
    fun type(t: String) = "type:$t"
    fun day(epochDay: Long) = "day:$epochDay"
    fun month(key: Int) = "month:$key"
}

private class MediaType(val id: String, val title: String, val test: (MediaItem) -> Boolean)

private val MEDIA_TYPES = listOf(
    MediaType("video", "Video") { it.isVideo },
    MediaType("selfie", "Selfie") { it.isSelfie },
    MediaType("screenshot", "Screenshot") { it.isScreenshot },
    MediaType("screenrec", "Registrazioni schermo") { it.isScreenRecording },
    MediaType("pano", "Panoramiche") { it.isPanorama },
    MediaType("gif", "GIF animate") { it.isGif },
    MediaType("raw", "RAW") { it.isRaw },
)

/** Istantanea immutabile della libreria, con tutti i raggruppamenti già calcolati. */
@Immutable
class Library(
    val loaded: Boolean,
    /** Elementi visibili (non nascosti), dal più recente. */
    val all: List<MediaItem>,
    val hidden: List<MediaItem>,
    val byId: Map<Long, MediaItem>,
    val favorites: Set<Long>,
    val days: List<DayGroup>,
    val months: List<MonthGroup>,
    val years: List<YearGroup>,
    val folders: List<AlbumInfo>,
    val userAlbums: List<AlbumInfo>,
    val mediaTypes: List<AlbumInfo>,
    val duplicates: List<List<MediaItem>>,
    val local: LocalData,
) {
    private val cache = HashMap<String, List<MediaItem>>()

    fun isFavorite(item: MediaItem): Boolean = item.id in favorites
    fun isHidden(item: MediaItem): Boolean = item.id in local.hidden

    val favoriteItems: List<MediaItem> get() = itemsFor(Keys.FAV)

    /** Elementi della sorgente indicata, dal più recente. */
    fun itemsFor(key: String): List<MediaItem> = synchronized(cache) {
        cache.getOrPut(key) { compute(key) }
    }

    private fun compute(key: String): List<MediaItem> {
        val arg = key.substringAfter(':', "")
        return when {
            key == Keys.ALL -> all
            key == Keys.FAV -> all.filter { it.id in favorites }
            key == Keys.HIDDEN -> hidden
            key.startsWith("bucket:") -> {
                val id = arg.toLongOrNull() ?: return emptyList()
                all.filter { it.bucketId == id }
            }
            key.startsWith("album:") -> {
                val album = local.albums.firstOrNull { it.id == arg } ?: return emptyList()
                album.items.mapNotNull { byId[it] }
                    .filter { it.id !in local.hidden }
                    .sortedWith(compareByDescending<MediaItem> { it.dateTaken }.thenByDescending { it.id })
            }
            key.startsWith("type:") -> {
                val type = MEDIA_TYPES.firstOrNull { it.id == arg } ?: return emptyList()
                all.filter(type.test)
            }
            key.startsWith("day:") -> {
                val d = arg.toLongOrNull() ?: return emptyList()
                days.firstOrNull { it.epochDay == d }?.items ?: emptyList()
            }
            key.startsWith("month:") -> {
                val m = arg.toIntOrNull() ?: return emptyList()
                months.firstOrNull { it.key == m }?.items ?: emptyList()
            }
            else -> emptyList()
        }
    }

    fun titleFor(key: String): String {
        val arg = key.substringAfter(':', "")
        return when {
            key == Keys.ALL -> "Recenti"
            key == Keys.FAV -> "Preferiti"
            key == Keys.HIDDEN -> "Nascosti"
            key == Keys.TRASH -> "Eliminati di recente"
            key.startsWith("bucket:") -> folders.firstOrNull { it.key == key }?.title ?: "Album"
            key.startsWith("album:") -> local.albums.firstOrNull { it.id == arg }?.name ?: "Album"
            key.startsWith("type:") -> MEDIA_TYPES.firstOrNull { it.id == arg }?.title ?: "Album"
            key.startsWith("day:") -> arg.toLongOrNull()?.let { Dates.dayTitle(it) } ?: ""
            key.startsWith("month:") -> arg.toIntOrNull()?.let { Dates.monthYear(it / 100, it % 100) } ?: ""
            else -> ""
        }
    }

    /** Tutti gli album "personali": Recenti, Preferiti, album utente e cartelle. */
    val myAlbums: List<AlbumInfo> by lazy {
        buildList {
            add(AlbumInfo(Keys.ALL, "Recenti", all.size, all.firstOrNull(), AlbumKind.SMART))
            val fav = itemsFor(Keys.FAV)
            add(AlbumInfo(Keys.FAV, "Preferiti", fav.size, fav.firstOrNull(), AlbumKind.SMART))
            addAll(userAlbums)
            addAll(folders)
        }
    }

    companion object {
        val EMPTY = Library(
            loaded = false,
            all = emptyList(),
            hidden = emptyList(),
            byId = emptyMap(),
            favorites = emptySet(),
            days = emptyList(),
            months = emptyList(),
            years = emptyList(),
            folders = emptyList(),
            userAlbums = emptyList(),
            mediaTypes = emptyList(),
            duplicates = emptyList(),
            local = LocalData(),
        )

        fun build(media: List<MediaItem>?, local: LocalData): Library {
            if (media == null) {
                return Library(
                    false, emptyList(), emptyList(), emptyMap(), local.favorites, emptyList(),
                    emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), local
                )
            }
            val zone = ZoneId.systemDefault()
            val visible = ArrayList<MediaItem>(media.size)
            val hidden = ArrayList<MediaItem>()
            val byId = HashMap<Long, MediaItem>(media.size * 2)
            for (item in media) {
                byId[item.id] = item
                if (item.id in local.hidden) hidden += item else visible += item
            }

            // Giorni, mesi e anni (gli elementi sono già ordinati dal più recente).
            val days = ArrayList<DayGroup>()
            var currentDay = Long.MIN_VALUE
            var bucket = ArrayList<MediaItem>()
            for (item in visible) {
                val day = java.time.Instant.ofEpochMilli(item.dateTaken).atZone(zone).toLocalDate().toEpochDay()
                if (day != currentDay) {
                    if (bucket.isNotEmpty()) days += DayGroup(currentDay, bucket)
                    bucket = ArrayList()
                    currentDay = day
                }
                bucket += item
            }
            if (bucket.isNotEmpty()) days += DayGroup(currentDay, bucket)

            val months = ArrayList<MonthGroup>()
            for (d in days) {
                val date = LocalDate.ofEpochDay(d.epochDay)
                val last = months.lastOrNull()
                if (last != null && last.year == date.year && last.month == date.monthValue) {
                    (last.items as ArrayList<MediaItem>).addAll(d.items)
                } else {
                    months += MonthGroup(date.year, date.monthValue, ArrayList(d.items))
                }
            }
            val years = ArrayList<YearGroup>()
            for (m in months) {
                val last = years.lastOrNull()
                if (last != null && last.year == m.year) {
                    (last.items as ArrayList<MediaItem>).addAll(m.items)
                } else {
                    years += YearGroup(m.year, ArrayList(m.items))
                }
            }

            // Cartelle del dispositivo.
            val folders = visible.groupBy { it.bucketId }.map { (id, items) ->
                AlbumInfo(
                    key = Keys.bucket(id),
                    title = items.first().bucketName.ifEmpty { "Senza nome" },
                    count = items.size,
                    cover = items.first(),
                    kind = AlbumKind.FOLDER,
                )
            }.sortedWith(
                compareByDescending<AlbumInfo> { it.title.equals("Camera", true) }
                    .thenByDescending { it.count }
            )

            // Album creati dall'utente.
            val userAlbums = local.albums.sortedBy { it.created }.map { album ->
                val items = album.items.mapNotNull { byId[it] }.filter { it.id !in local.hidden }
                AlbumInfo(
                    key = Keys.album(album.id),
                    title = album.name,
                    count = items.size,
                    cover = items.maxByOrNull { it.dateTaken },
                    kind = AlbumKind.USER,
                    userAlbumId = album.id,
                )
            }

            val mediaTypes = MEDIA_TYPES.mapNotNull { type ->
                val items = visible.filter(type.test)
                if (items.isEmpty()) null
                else AlbumInfo(Keys.type(type.id), type.title, items.size, items.first(), AlbumKind.TYPE)
            }

            val duplicates = visible
                .filter { it.size > 0 }
                .groupBy { "${it.size}_${it.width}_${it.height}_${it.isVideo}" }
                .values
                .filter { it.size > 1 }
                .map { group -> group.sortedBy { it.dateTaken } }
                .sortedByDescending { group -> group.maxOf { it.dateTaken } }

            val favorites = local.favorites.filterTo(HashSet()) { it in byId }

            return Library(
                loaded = true,
                all = visible,
                hidden = hidden,
                byId = byId,
                favorites = favorites,
                days = days,
                months = months,
                years = years,
                folders = folders,
                userAlbums = userAlbums,
                mediaTypes = mediaTypes,
                duplicates = duplicates,
                local = local,
            )
        }
    }
}
