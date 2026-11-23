package com.afam.galleria.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** Album creato dall'utente (virtuale, come su iOS: una foto può stare in più album). */
data class UserAlbum(
    val id: String,
    val name: String,
    val created: Long,
    val items: List<Long>,
)

/** Dati locali dell'app: preferiti, nascosti, album e impostazioni. */
data class LocalData(
    val favorites: Set<Long> = emptySet(),
    val hidden: Set<Long> = emptySet(),
    val albums: List<UserAlbum> = emptyList(),
    val newestFirst: Boolean = false,
    val lockPrivate: Boolean = true,
    val showHiddenAlbum: Boolean = true,
    val importedSystemFavorites: Boolean = false,
    val gridColumns: Int = 4,
    /** Effetto Liquid Glass completo (sfocatura e rifrazione). */
    val glassEffects: Boolean = true,
)

class LocalStore(context: Context) {

    private val file = File(context.filesDir, "galleria.json")
    private val writer = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private val _data = MutableStateFlow(load())
    val data: StateFlow<LocalData> = _data.asStateFlow()

    val value: LocalData get() = _data.value

    fun update(transform: (LocalData) -> LocalData) {
        val json: String
        synchronized(lock) {
            val next = transform(_data.value)
            if (next == _data.value) return
            _data.value = next
            json = toJson(next).toString()
        }
        writer.execute {
            try {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(json)
                if (!tmp.renameTo(file)) {
                    file.writeText(json)
                    tmp.delete()
                }
            } catch (e: Exception) {
                Log.w("LocalStore", "save failed", e)
            }
        }
    }

    fun newAlbumId(): String = UUID.randomUUID().toString()

    private fun load(): LocalData {
        if (!file.exists()) return LocalData()
        return try {
            val o = JSONObject(file.readText())
            LocalData(
                favorites = o.optJSONArray("favorites").toLongSet(),
                hidden = o.optJSONArray("hidden").toLongSet(),
                albums = o.optJSONArray("albums")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        val a = arr.optJSONObject(i) ?: return@mapNotNull null
                        UserAlbum(
                            id = a.optString("id"),
                            name = a.optString("name"),
                            created = a.optLong("created"),
                            items = a.optJSONArray("items").toLongList(),
                        )
                    }
                } ?: emptyList(),
                newestFirst = o.optBoolean("newestFirst", false),
                lockPrivate = o.optBoolean("lockPrivate", true),
                showHiddenAlbum = o.optBoolean("showHiddenAlbum", true),
                importedSystemFavorites = o.optBoolean("importedSystemFavorites", false),
                gridColumns = o.optInt("gridColumns", 4),
                glassEffects = o.optBoolean("glassEffects", true),
            )
        } catch (e: Exception) {
            Log.w("LocalStore", "load failed", e)
            LocalData()
        }
    }

    private fun toJson(d: LocalData): JSONObject = JSONObject().apply {
        put("favorites", JSONArray(d.favorites.toList()))
        put("hidden", JSONArray(d.hidden.toList()))
        put("albums", JSONArray().apply {
            d.albums.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id)
                    put("name", a.name)
                    put("created", a.created)
                    put("items", JSONArray(a.items))
                })
            }
        })
        put("newestFirst", d.newestFirst)
        put("lockPrivate", d.lockPrivate)
        put("showHiddenAlbum", d.showHiddenAlbum)
        put("importedSystemFavorites", d.importedSystemFavorites)
        put("gridColumns", d.gridColumns)
        put("glassEffects", d.glassEffects)
    }

    private fun JSONArray?.toLongList(): List<Long> {
        if (this == null) return emptyList()
        return (0 until length()).map { optLong(it) }
    }

    private fun JSONArray?.toLongSet(): Set<Long> = toLongList().toSet()
}
