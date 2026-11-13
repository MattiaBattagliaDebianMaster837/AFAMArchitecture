package com.afam.galleria.data

import androidx.compose.runtime.Immutable
import com.afam.galleria.util.Dates
import java.time.LocalDate

/** Un "ricordo" generato automaticamente, riprodotto come presentazione. */
@Immutable
data class Memory(
    val id: String,
    val title: String,
    val subtitle: String,
    val cover: MediaItem,
    /** Elementi in ordine cronologico (dal più vecchio). */
    val items: List<MediaItem>,
)

@Immutable
data class ForYou(
    val memories: List<Memory> = emptyList(),
    val onThisDay: List<Memory> = emptyList(),
    val featured: List<MediaItem> = emptyList(),
)

object MemoryBuilder {

    private const val MAX_ITEMS = 40

    fun build(library: Library): ForYou {
        if (!library.loaded || library.all.isEmpty()) return ForYou()
        val today = LocalDate.now(Dates.zone)
        val photos = library.all.filter { !it.isVideo && !it.isScreenshot }

        // In questo giorno, negli anni passati.
        val onThisDay = photos
            .filter {
                val d = Dates.localDate(it.dateTaken)
                d.monthValue == today.monthValue && d.dayOfMonth == today.dayOfMonth && d.year < today.year
            }
            .groupBy { Dates.localDate(it.dateTaken).year }
            .entries
            .sortedByDescending { it.key }
            .filter { it.value.size >= 2 }
            .map { (year, items) ->
                val ago = today.year - year
                memory(
                    id = "otd-$year",
                    title = if (ago == 1) "1 anno fa" else "$ago anni fa",
                    subtitle = Dates.capitalize(Dates.shortDate(items.first().dateTaken)),
                    items = items,
                    library = library,
                )
            }

        val memories = ArrayList<Memory>()

        // Giornate speciali: giorni con molte foto.
        library.days
            .map { day -> day to day.items.filter { !it.isVideo && !it.isScreenshot } }
            .filter { it.second.size >= 15 }
            .sortedByDescending { it.second.size }
            .take(6)
            .forEach { (day, items) ->
                memory(
                    id = "day-${day.epochDay}",
                    title = Dates.capitalize(LocalDate.ofEpochDay(day.epochDay).let {
                        "${it.dayOfMonth} ${Dates.monthName(it.monthValue)} ${it.year}"
                    }),
                    subtitle = "Una giornata da ricordare",
                    items = items,
                    library = library,
                )?.let { memories += it }
            }

        // Mesi recenti con molte foto.
        library.months
            .take(12)
            .map { m -> m to m.items.filter { !it.isVideo && !it.isScreenshot } }
            .filter { it.second.size >= 12 }
            .take(4)
            .forEach { (m, items) ->
                memory(
                    id = "month-${m.key}",
                    title = "Momenti di ${Dates.monthName(m.month).lowercase(Dates.locale)}",
                    subtitle = m.year.toString(),
                    items = items,
                    library = library,
                )?.let { memories += it }
            }

        // Il meglio di ogni anno passato.
        library.years
            .filter { it.year < today.year }
            .map { y -> y to y.items.filter { !it.isVideo && !it.isScreenshot } }
            .filter { it.second.size >= 25 }
            .forEach { (y, items) ->
                memory(
                    id = "year-${y.year}",
                    title = "Il meglio del ${y.year}",
                    subtitle = Dates.count(items.size, 0),
                    items = items,
                    library = library,
                )?.let { memories += it }
            }

        // Preferiti.
        val favs = library.favoriteItems.filter { !it.isVideo }
        if (favs.size >= 5) {
            memory("favorites", "I tuoi preferiti", Dates.count(favs.size, 0), favs, library)
                ?.let { memories += it }
        }

        val sorted = memories
            .distinctBy { it.id }
            .sortedByDescending { it.items.lastOrNull()?.dateTaken ?: 0L }

        // Foto in evidenza: preferiti recenti, completati da una selezione stabile.
        val featured = LinkedHashSet<MediaItem>()
        featured.addAll(favs.take(12))
        if (featured.size < 12 && photos.isNotEmpty()) {
            val seed = today.toEpochDay()
            val step = maxOf(1, photos.size / 24)
            var i = (seed % step).toInt()
            while (i < photos.size && featured.size < 12) {
                featured += photos[i]
                i += step
            }
        }

        return ForYou(
            memories = sorted,
            onThisDay = onThisDay.filterNotNull(),
            featured = featured.toList(),
        )
    }

    private fun memory(
        id: String,
        title: String,
        subtitle: String,
        items: List<MediaItem>,
        library: Library,
    ): Memory? {
        if (items.isEmpty()) return null
        val chrono = items.sortedBy { it.dateTaken }
        val picked = if (chrono.size <= MAX_ITEMS) chrono else {
            val step = chrono.size.toDouble() / MAX_ITEMS
            (0 until MAX_ITEMS).map { chrono[(it * step).toInt().coerceIn(0, chrono.lastIndex)] }
        }
        val cover = picked.firstOrNull { library.isFavorite(it) && it.aspect < 1.2f }
            ?: picked.firstOrNull { it.aspect in 0.6f..0.85f }
            ?: picked[picked.size / 3]
        return Memory(id, title, subtitle, cover, picked)
    }
}
