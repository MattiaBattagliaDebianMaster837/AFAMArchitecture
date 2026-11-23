package com.afam.galleria

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.afam.galleria.data.ForYou
import com.afam.galleria.data.Library
import com.afam.galleria.data.LocalStore
import com.afam.galleria.data.LocationIndex
import com.afam.galleria.data.LocationState
import com.afam.galleria.data.MediaItem
import com.afam.galleria.data.MediaRepository
import com.afam.galleria.data.MemoryBuilder
import com.afam.galleria.data.UserAlbum
import com.afam.galleria.ui.editor.EditParams
import com.afam.galleria.ui.editor.EditRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Richiesta di sistema (es. conferma di eliminazione) da lanciare dall'interfaccia. */
class PendingRequest(val sender: IntentSender, val onResult: (Boolean) -> Unit)

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    val repo = MediaRepository(app)
    val store = LocalStore(app)

    private val media = MutableStateFlow<List<MediaItem>?>(null)
    private val _trash = MutableStateFlow<List<MediaItem>?>(null)
    val trash: StateFlow<List<MediaItem>?> = _trash.asStateFlow()

    val library: StateFlow<Library> = combine(media, store.data) { m, local -> Library.build(m, local) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Library.EMPTY)

    val forYou: StateFlow<ForYou> = library
        .map { MemoryBuilder.build(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ForYou())

    private val _requests = MutableSharedFlow<PendingRequest>(extraBufferCapacity = 8)
    val requests: SharedFlow<PendingRequest> = _requests.asSharedFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Elemento aperto da un'altra app (intent VIEW). */
    val externalItem = MutableStateFlow<MediaItem?>(null)

    /** Ultima foto salvata dall'editor: il visualizzatore la mostra appena compare. */
    val lastSaved = MutableStateFlow<Uri?>(null)

    /** true mentre l'editor esporta l'immagine. */
    val saving = MutableStateFlow(false)

    // ---- Mappa ----------------------------------------------------------------------------

    private val locationIndex = LocationIndex(app)
    val locations: StateFlow<LocationState> = locationIndex.state

    /** Elemento da mostrare sulla mappa (dal pannello Info). */
    val mapFocus = MutableStateFlow<Long?>(null)

    /** Incrementato per chiedere all'interfaccia di aprire la scheda Mappa. */
    val openMapRequest = MutableStateFlow(0)

    private var locationJob: Job? = null

    init {
        // Indicizza le posizioni in background quando cambia l'insieme dei file.
        viewModelScope.launch {
            media.filter { it != null }
                .distinctUntilChanged { a, b -> a === b }
                .debounce(1200)
                .collect { items -> reindexLocations(items.orEmpty()) }
        }
    }

    private fun reindexLocations(items: List<MediaItem>) {
        locationJob?.cancel()
        locationJob = viewModelScope.launch { locationIndex.update(items) }
    }

    /** Da chiamare dopo aver ottenuto l'autorizzazione alla posizione delle foto. */
    fun refreshLocations() {
        media.value?.let { reindexLocations(it) }
    }

    fun openMap(focus: MediaItem? = null) {
        mapFocus.value = focus?.id
        openMapRequest.value++
    }

    private var observing = false
    private var loadJob: Job? = null

    fun start() {
        refresh()
        if (!observing) {
            observing = true
            viewModelScope.launch {
                repo.observeChanges().debounce(350).collect {
                    refresh()
                    if (_trash.value != null) loadTrash()
                }
            }
        }
    }

    /** Solo per i test: blocca la libreria su dati finti. */
    internal var debugFrozen = false

    internal fun debugSetMedia(items: List<MediaItem>) {
        debugFrozen = true
        loadJob?.cancel()
        media.value = items
    }

    fun refresh() {
        if (debugFrozen) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val items = repo.loadAll()
            if (!store.value.importedSystemFavorites && items.isNotEmpty()) {
                val systemFavorites = items.filter { it.systemFavorite }.map { it.id }
                store.update {
                    it.copy(favorites = it.favorites + systemFavorites, importedSystemFavorites = true)
                }
            }
            media.value = items
        }
    }

    fun loadTrash() {
        viewModelScope.launch { _trash.value = repo.loadTrash() }
    }

    fun message(text: String) {
        _messages.tryEmit(text)
    }

    // ---- Preferiti / nascosti -------------------------------------------------------------

    fun toggleFavorite(items: List<MediaItem>) {
        val lib = library.value
        val allFavorite = items.isNotEmpty() && items.all { lib.isFavorite(it) }
        setFavorite(items, !allFavorite)
    }

    fun setFavorite(items: List<MediaItem>, value: Boolean) {
        val ids = items.filter { !it.external }.map { it.id }.toSet()
        if (ids.isEmpty()) return
        store.update { d ->
            d.copy(favorites = if (value) d.favorites + ids else d.favorites - ids)
        }
    }

    fun setHidden(items: List<MediaItem>, hidden: Boolean) {
        val ids = items.filter { !it.external }.map { it.id }.toSet()
        if (ids.isEmpty()) return
        store.update { d -> d.copy(hidden = if (hidden) d.hidden + ids else d.hidden - ids) }
        message(
            if (hidden) {
                if (ids.size == 1) "Elemento nascosto" else "${ids.size} elementi nascosti"
            } else {
                if (ids.size == 1) "Elemento visibile" else "${ids.size} elementi visibili"
            }
        )
    }

    // ---- Eliminazione ---------------------------------------------------------------------

    fun canManageMedia(): Boolean = repo.canManageMedia()

    fun trash(items: List<MediaItem>, onDone: (Boolean) -> Unit = {}) {
        val real = items.filter { !it.external }
        if (real.isEmpty()) return
        try {
            val sender = repo.trashRequest(real, true)
            _requests.tryEmit(PendingRequest(sender) { ok ->
                if (ok) refresh()
                onDone(ok)
            })
        } catch (e: Exception) {
            message("Impossibile eliminare")
        }
    }

    fun restore(items: List<MediaItem>) {
        if (items.isEmpty()) return
        try {
            val sender = repo.trashRequest(items, false)
            _requests.tryEmit(PendingRequest(sender) { ok ->
                if (ok) {
                    loadTrash()
                    refresh()
                    message(if (items.size == 1) "Elemento recuperato" else "${items.size} elementi recuperati")
                }
            })
        } catch (e: Exception) {
            message("Impossibile recuperare")
        }
    }

    fun deleteForever(items: List<MediaItem>, onDone: (Boolean) -> Unit = {}) {
        val real = items.filter { !it.external }
        if (real.isEmpty()) return
        try {
            val sender = repo.deleteRequest(real)
            _requests.tryEmit(PendingRequest(sender) { ok ->
                if (ok) {
                    loadTrash()
                    refresh()
                }
                onDone(ok)
            })
        } catch (e: Exception) {
            message("Impossibile eliminare")
        }
    }

    // ---- Album ----------------------------------------------------------------------------

    fun createAlbum(name: String, items: List<MediaItem> = emptyList()): String {
        val id = store.newAlbumId()
        val clean = name.trim().ifEmpty { "Nuovo album" }
        store.update { d ->
            d.copy(
                albums = d.albums + UserAlbum(
                    id = id,
                    name = clean,
                    created = System.currentTimeMillis(),
                    items = items.filter { !it.external }.map { it.id }.distinct(),
                )
            )
        }
        if (items.isNotEmpty()) message("Aggiunto a «$clean»")
        return id
    }

    fun addToAlbum(albumId: String, items: List<MediaItem>) {
        val ids = items.filter { !it.external }.map { it.id }
        var name = ""
        store.update { d ->
            d.copy(albums = d.albums.map { a ->
                if (a.id == albumId) {
                    name = a.name
                    a.copy(items = (a.items + ids).distinct())
                } else a
            })
        }
        if (name.isNotEmpty()) message("Aggiunto a «$name»")
    }

    fun removeFromAlbum(albumId: String, items: List<MediaItem>) {
        val ids = items.map { it.id }.toSet()
        store.update { d ->
            d.copy(albums = d.albums.map { a ->
                if (a.id == albumId) a.copy(items = a.items.filter { it !in ids }) else a
            })
        }
    }

    fun renameAlbum(albumId: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        store.update { d ->
            d.copy(albums = d.albums.map { if (it.id == albumId) it.copy(name = clean) else it })
        }
    }

    fun deleteAlbum(albumId: String) {
        store.update { d -> d.copy(albums = d.albums.filter { it.id != albumId }) }
    }

    // ---- File -----------------------------------------------------------------------------

    fun duplicate(item: MediaItem) {
        viewModelScope.launch {
            val uri = repo.duplicate(item)
            message(if (uri != null) "Duplicato creato" else "Impossibile duplicare")
            if (uri != null) refresh()
        }
    }

    fun exportEdit(original: MediaItem, params: EditParams, onDone: (Boolean) -> Unit) {
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            val bitmap = EditRenderer.render(getApplication(), original, params)
            val uri = if (bitmap != null) repo.saveEdited(bitmap, original) else null
            bitmap?.recycle()
            saving.value = false
            if (uri != null) {
                lastSaved.value = uri
                message("Salvata come nuova foto")
                refresh()
            } else {
                message("Impossibile salvare la foto")
            }
            onDone(uri != null)
        }
    }

    fun openExternal(uri: Uri, type: String?) {
        viewModelScope.launch { externalItem.value = repo.externalItem(uri, type) }
    }

    // ---- Impostazioni ---------------------------------------------------------------------

    fun setNewestFirst(value: Boolean) = store.update { it.copy(newestFirst = value) }
    fun setLockPrivate(value: Boolean) = store.update { it.copy(lockPrivate = value) }
    fun setShowHiddenAlbum(value: Boolean) = store.update { it.copy(showHiddenAlbum = value) }
    fun setGridColumns(value: Int) = store.update { it.copy(gridColumns = value) }
    fun setGlassEffects(value: Boolean) = store.update { it.copy(glassEffects = value) }
}
