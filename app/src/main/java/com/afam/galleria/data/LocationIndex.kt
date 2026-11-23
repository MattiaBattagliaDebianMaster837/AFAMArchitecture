package com.afam.galleria.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@Immutable
data class GeoPoint(val lat: Double, val lng: Double)

@Immutable
data class LocationState(
    /** Posizione di ogni elemento geolocalizzato (id MediaStore → coordinate). */
    val points: Map<Long, GeoPoint> = emptyMap(),
    val loaded: Boolean = false,
    val indexing: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    /** false se manca l'autorizzazione ACCESS_MEDIA_LOCATION (Android oscura il GPS). */
    val hasPermission: Boolean = true,
)

/**
 * Indice delle posizioni GPS delle foto, letto dai metadati EXIF (e dai video) in background.
 * Viene salvato su disco: le scansioni successive leggono solo gli elementi nuovi o modificati.
 */
class LocationIndex(private val context: Context) {

    private class Entry(val modified: Long, val lat: Float, val lng: Float) {
        val hasLocation: Boolean get() = !lat.isNaN()
    }

    private val file = File(context.filesDir, "locations.bin")
    private val mutex = Mutex()
    private val entries = HashMap<Long, Entry>()
    private var loaded = false
    private var storedPermission = false
    /** Thread a bassa priorità: l'indicizzazione non deve rubare tempo all'interfaccia. */
    private val io = java.util.concurrent.Executors.newFixedThreadPool(3) { r ->
        Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            r.run()
        }, "location-index").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val _state = MutableStateFlow(LocationState())
    val state: StateFlow<LocationState> = _state.asStateFlow()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Aggiorna l'indice per gli elementi indicati (quelli assenti dall'elenco vengono rimossi). */
    suspend fun update(items: Collection<MediaItem>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!loaded) {
                load()
                loaded = true
                publish(indexing = false, done = 0, total = 0)
            }
            val permission = hasPermission()
            var dirty = false
            if (permission != storedPermission) {
                // Con la nuova autorizzazione le posizioni prima oscurate diventano leggibili.
                if (permission) entries.entries.removeIf { !it.value.hasLocation }
                storedPermission = permission
                dirty = true
            }
            val ids = HashSet<Long>(items.size * 2)
            items.forEach { ids += it.id }
            if (entries.keys.retainAll(ids)) dirty = true

            val todo = items.filter { item ->
                !item.external && entries[item.id].let { it == null || it.modified != item.dateModified }
            }
            if (todo.isEmpty()) {
                if (dirty) save()
                publish(indexing = false, done = 0, total = 0)
                return@withContext
            }
            publish(indexing = true, done = 0, total = todo.size)
            var done = 0
            var sinceSave = 0
            try {
                for (chunk in todo.chunked(48)) {
                    ensureActive()
                    val results = coroutineScope {
                        chunk.map { item -> async(io) { item to read(item, permission) } }.awaitAll()
                    }
                    for ((item, point) in results) {
                        entries[item.id] = Entry(
                            item.dateModified,
                            point?.lat?.toFloat() ?: Float.NaN,
                            point?.lng?.toFloat() ?: Float.NaN,
                        )
                    }
                    done += chunk.size
                    sinceSave += chunk.size
                    // Mostra i risultati man mano, senza ricalcolare la mappa a ogni foto.
                    publish(indexing = true, done = done, total = todo.size, refreshPoints = sinceSave >= 480)
                    if (sinceSave >= 480) {
                        save()
                        sinceSave = 0
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    save()
                    publish(indexing = false, done = 0, total = 0)
                }
            }
        }
    }

    private var lastPoints: Map<Long, GeoPoint> = emptyMap()

    private fun publish(indexing: Boolean, done: Int, total: Int, refreshPoints: Boolean = true) {
        if (refreshPoints || !indexing) {
            val map = HashMap<Long, GeoPoint>()
            for ((id, e) in entries) {
                if (e.hasLocation) map[id] = GeoPoint(e.lat.toDouble(), e.lng.toDouble())
            }
            lastPoints = if (map == lastPoints) lastPoints else map
        }
        _state.value = LocationState(
            points = lastPoints,
            loaded = true,
            indexing = indexing,
            done = done,
            total = total,
            hasPermission = storedPermission,
        )
    }

    private fun read(item: MediaItem, permission: Boolean): GeoPoint? {
        val uri: Uri = if (permission) {
            runCatching { MediaStore.setRequireOriginal(item.uri) }.getOrDefault(item.uri)
        } else item.uri
        return try {
            if (item.isVideo) readVideo(uri) else readImage(uri)
        } catch (e: Exception) {
            null
        }
    }

    private fun readImage(uri: Uri): GeoPoint? {
        val latLong = context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            ExifInterface(pfd.fileDescriptor).latLong
        } ?: return null
        return valid(latLong[0], latLong[1])
    }

    private fun readVideo(uri: Uri): GeoPoint? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                ?: return null
            val match = Regex("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)").find(location) ?: return null
            val lat = match.groupValues[1].toDoubleOrNull() ?: return null
            val lng = match.groupValues[2].toDoubleOrNull() ?: return null
            valid(lat, lng)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun valid(lat: Double, lng: Double): GeoPoint? {
        if (lat.isNaN() || lng.isNaN()) return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        if (lat == 0.0 && lng == 0.0) return null
        return GeoPoint(lat, lng)
    }

    private fun load() {
        if (!file.exists()) return
        try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) return
                storedPermission = input.readBoolean()
                val count = input.readInt()
                repeat(count) {
                    val id = input.readLong()
                    val modified = input.readLong()
                    val lat = input.readFloat()
                    val lng = input.readFloat()
                    entries[id] = Entry(modified, lat, lng)
                }
            }
        } catch (e: Exception) {
            Log.w("LocationIndex", "load failed", e)
            entries.clear()
        }
    }

    private fun save() {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(VERSION)
                out.writeBoolean(storedPermission)
                out.writeInt(entries.size)
                for ((id, e) in entries) {
                    out.writeLong(id)
                    out.writeLong(e.modified)
                    out.writeFloat(e.lat)
                    out.writeFloat(e.lng)
                }
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.w("LocationIndex", "save failed", e)
        }
    }

    private companion object {
        const val MAGIC = 0x47414C4C
        const val VERSION = 1
    }
}
