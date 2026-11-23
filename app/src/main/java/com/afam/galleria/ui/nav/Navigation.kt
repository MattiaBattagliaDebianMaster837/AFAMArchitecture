package com.afam.galleria.ui.nav

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import com.afam.galleria.data.MediaItem
import com.afam.galleria.data.Memory

/** Schermate che si sovrappongono alle schede principali. */
sealed interface Route {
    /**
     * Visualizzatore a schermo intero.
     * @param source chiave della sorgente (vedi [com.afam.galleria.data.Keys]).
     * @param ids elenco esplicito di elementi (es. risultati di ricerca), nell'ordine di visualizzazione.
     * @param origin posizione della miniatura toccata, per l'animazione di zoom.
     */
    data class Viewer(
        val source: String,
        val startId: Long,
        val ids: List<Long>? = null,
        val origin: Rect? = null,
    ) : Route

    data class Album(val key: String) : Route
    data object AllAlbums : Route
    data object Trash : Route
    data object Duplicates : Route
    data object Settings : Route
    data class Editor(val item: MediaItem) : Route
    data class MemoryPlayer(val memory: Memory) : Route
    data class AddToAlbum(val albumId: String) : Route
}

class NavEntry(val id: Long, val route: Route) {
    val transition = MutableTransitionState(false).apply { targetState = true }
    val isActive: Boolean get() = transition.targetState
}

@Stable
class Navigator {
    private var nextId = 1L
    val entries = mutableStateListOf<NavEntry>()

    /** Elemento attualmente mostrato nel visualizzatore (le griglie lo mantengono visibile). */
    var viewerItemId by mutableStateOf<Long?>(null)

    val hasEntries: Boolean get() = entries.any { it.isActive }

    val top: NavEntry? get() = entries.lastOrNull { it.isActive }

    fun push(route: Route) {
        entries += NavEntry(nextId++, route)
    }

    /** Chiude la schermata in cima con animazione. */
    fun pop() {
        top?.transition?.targetState = false
    }

    /** Rimuove subito una schermata (usato dal visualizzatore dopo la sua animazione). */
    fun remove(entry: NavEntry) {
        entries.remove(entry)
    }

    fun isTop(entry: NavEntry): Boolean = top?.id == entry.id
}

/** Posizioni delle miniature visibili, per animare l'apertura/chiusura del visualizzatore. */
class ThumbRegistry {
    private val map = HashMap<Long, Rect>()
    private val providers = ArrayList<(Long) -> Rect?>()

    /**
     * Le griglie registrano una funzione che calcola la posizione di una miniatura solo
     * quando serve, invece di aggiornarla a ogni fotogramma di scorrimento.
     */
    fun addProvider(provider: (Long) -> Rect?): () -> Unit {
        providers += provider
        return { providers.remove(provider) }
    }

    fun put(id: Long, rect: Rect) {
        map[id] = rect
    }

    fun remove(id: Long, rect: Rect?) {
        if (rect == null || map[id] == rect) map.remove(id)
    }

    operator fun get(id: Long): Rect? {
        for (i in providers.indices.reversed()) {
            providers[i](id)?.let { return it }
        }
        return map[id]
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator mancante") }
val LocalThumbRegistry = staticCompositionLocalOf { ThumbRegistry() }
