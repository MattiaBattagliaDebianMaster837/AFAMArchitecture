package com.afam.galleria.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.database.ContentObserver
import android.graphics.Bitmap
import android.location.Geocoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private const val TAG = "MediaRepository"

/** Dettagli mostrati nel pannello "Info". */
data class MediaDetails(
    val camera: String? = null,
    val lens: String? = null,
    val aperture: String? = null,
    val exposure: String? = null,
    val iso: String? = null,
    val focal: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val place: String? = null,
)

class MediaRepository(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    private val imagesUri: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    private val videosUri: Uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    suspend fun loadAll(): List<MediaItem> = withContext(Dispatchers.IO) {
        val images = query(imagesUri, isVideo = false, trashed = false)
        val videos = query(videosUri, isVideo = true, trashed = false)
        (images + videos).sortedWith(
            compareByDescending<MediaItem> { it.dateTaken }.thenByDescending { it.id }
        )
    }

    suspend fun loadTrash(): List<MediaItem> = withContext(Dispatchers.IO) {
        val images = query(imagesUri, isVideo = false, trashed = true)
        val videos = query(videosUri, isVideo = true, trashed = true)
        (images + videos).sortedByDescending { it.expires }
    }

    private fun query(collection: Uri, isVideo: Boolean, trashed: Boolean): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.ORIENTATION,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.IS_FAVORITE,
            MediaStore.MediaColumns.DATE_EXPIRES,
        )
        val args = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )
            if (trashed) putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        }
        val out = ArrayList<MediaItem>()
        try {
            resolver.query(collection, projection, args, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val iModified = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val iWidth = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val iHeight = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val iOrientation = c.getColumnIndexOrThrow(MediaStore.MediaColumns.ORIENTATION)
                val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val iDuration = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                val iBucketName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val iPath = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val iFav = c.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)
                val iExpires = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_EXPIRES)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val modified = c.getLong(iModified)
                    val added = c.getLong(iAdded)
                    var taken = c.getLong(iTaken)
                    if (taken <= 0L) taken = (if (modified > 0) modified else added) * 1000L
                    out += MediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        name = c.getString(iName) ?: "",
                        mime = c.getString(iMime) ?: if (isVideo) "video/mp4" else "image/jpeg",
                        isVideo = isVideo,
                        dateTaken = taken,
                        dateModified = modified,
                        width = c.getInt(iWidth),
                        height = c.getInt(iHeight),
                        orientation = c.getInt(iOrientation),
                        size = c.getLong(iSize),
                        duration = c.getLong(iDuration),
                        bucketId = c.getLong(iBucket),
                        bucketName = c.getString(iBucketName) ?: "",
                        relativePath = c.getString(iPath) ?: "",
                        systemFavorite = c.getInt(iFav) == 1,
                        expires = c.getLong(iExpires),
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "query failed for $collection", e)
        }
        return out
    }

    /** Emette un valore ogni volta che MediaStore segnala modifiche. */
    fun observeChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    fun canManageMedia(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context)

    fun trashRequest(items: List<MediaItem>, trash: Boolean): IntentSender =
        MediaStore.createTrashRequest(resolver, items.map { it.uri }, trash).intentSender

    fun deleteRequest(items: List<MediaItem>): IntentSender =
        MediaStore.createDeleteRequest(resolver, items.map { it.uri }).intentSender

    /** Crea una copia del file nella stessa cartella (se scrivibile). */
    suspend fun duplicate(item: MediaItem): Uri? = withContext(Dispatchers.IO) {
        val (base, ext) = splitName(item.name, if (item.isVideo) ".mp4" else ".jpg")
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$base copia$ext")
            put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, writableRelativePath(item))
            put(MediaStore.MediaColumns.DATE_TAKEN, item.dateTaken)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = if (item.isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val uri = try {
            resolver.insert(target, values)
        } catch (e: Exception) {
            Log.w(TAG, "insert failed", e); null
        } ?: return@withContext null
        try {
            resolver.openInputStream(item.uri).use { input ->
                resolver.openOutputStream(uri).use { output ->
                    if (input == null || output == null) error("stream non disponibile")
                    input.copyTo(output)
                }
            }
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            uri
        } catch (e: Exception) {
            Log.w(TAG, "duplicate failed", e)
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    /**
     * Salva una foto modificata come nuovo file accanto all'originale,
     * mantenendo la data di scatto e i principali metadati EXIF.
     */
    suspend fun saveEdited(bitmap: Bitmap, original: MediaItem): Uri? = withContext(Dispatchers.IO) {
        val png = original.mime.equals("image/png", true)
        val (base, _) = splitName(original.name, ".jpg")
        val ext = if (png) ".png" else ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$base-modificata$ext")
            put(MediaStore.MediaColumns.MIME_TYPE, if (png) "image/png" else "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, writableRelativePath(original))
            put(MediaStore.MediaColumns.DATE_TAKEN, original.dateTaken)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = try {
            resolver.insert(target, values)
        } catch (e: Exception) {
            Log.w(TAG, "insert failed", e); null
        } ?: return@withContext null
        try {
            resolver.openOutputStream(uri).use { out ->
                if (out == null) error("stream non disponibile")
                val ok = bitmap.compress(
                    if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                    95,
                    out
                )
                if (!ok) error("compressione fallita")
            }
            if (!png && !original.external) copyExif(original, uri)
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            uri
        } catch (e: Exception) {
            Log.w(TAG, "save failed", e)
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun copyExif(original: MediaItem, target: Uri) {
        try {
            val dateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            val source = resolver.openInputStream(original.uri)?.use { ExifInterface(it) }
            resolver.openFileDescriptor(target, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                val tags = listOf(
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
                    ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_F_NUMBER,
                    ExifInterface.TAG_EXPOSURE_TIME,
                    ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                    ExifInterface.TAG_FOCAL_LENGTH,
                    ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
                    ExifInterface.TAG_LENS_MODEL,
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE_REF,
                    ExifInterface.TAG_GPS_ALTITUDE,
                    ExifInterface.TAG_GPS_ALTITUDE_REF,
                )
                for (tag in tags) {
                    source?.getAttribute(tag)?.let { exif.setAttribute(tag, it) }
                }
                if (exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) == null) {
                    exif.setAttribute(
                        ExifInterface.TAG_DATETIME_ORIGINAL,
                        dateFormat.format(Date(original.dateTaken))
                    )
                }
                exif.setAttribute(ExifInterface.TAG_DATETIME, dateFormat.format(Date()))
                exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Galleria")
                exif.setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL.toString()
                )
                exif.saveAttributes()
            }
        } catch (e: Exception) {
            Log.w(TAG, "exif copy failed", e)
        }
    }

    suspend fun details(item: MediaItem): MediaDetails = withContext(Dispatchers.IO) {
        if (item.isVideo) videoDetails(item) else imageDetails(item)
    }

    private fun imageDetails(item: MediaItem): MediaDetails {
        val exif: ExifInterface = try {
            val original = if (!item.external) {
                runCatching { MediaStore.setRequireOriginal(item.uri) }.getOrDefault(item.uri)
            } else item.uri
            val stream = runCatching { resolver.openInputStream(original) }.getOrNull()
                ?: resolver.openInputStream(item.uri)
                ?: return MediaDetails()
            stream.use { ExifInterface(it) }
        } catch (e: Exception) {
            return MediaDetails()
        }
        val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
        val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
        val camera = when {
            model.isEmpty() -> make.ifEmpty { null }
            make.isEmpty() || model.startsWith(make, true) -> model
            else -> "$make $model"
        }
        val fNumber = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
        val exposure = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
        val iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
        val focal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
        val focal35 = exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
        val latLong = exif.latLong
        val lat = latLong?.getOrNull(0)
        val lng = latLong?.getOrNull(1)
        val hasLocation = lat != null && lng != null && !(lat == 0.0 && lng == 0.0)
        return MediaDetails(
            camera = camera,
            lens = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim()?.ifEmpty { null },
            aperture = if (fNumber > 0) "ƒ/" + formatNumber(fNumber) else null,
            exposure = if (exposure > 0) formatExposure(exposure) else null,
            iso = if (iso > 0) "ISO $iso" else null,
            focal = when {
                focal35 > 0 -> "$focal35 mm"
                focal > 0 -> formatNumber(focal) + " mm"
                else -> null
            },
            latitude = if (hasLocation) lat else null,
            longitude = if (hasLocation) lng else null,
            place = if (hasLocation) geocode(lat, lng) else null,
        )
    }

    private fun videoDetails(item: MediaItem): MediaDetails {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, item.uri)
            val location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            var lat: Double? = null
            var lng: Double? = null
            if (location != null) {
                // Formato ISO-6709, es. "+45.4642+009.1900/"
                val match = Regex("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)").find(location)
                if (match != null) {
                    lat = match.groupValues[1].toDoubleOrNull()
                    lng = match.groupValues[2].toDoubleOrNull()
                }
            }
            val model = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
            MediaDetails(
                camera = model,
                latitude = lat,
                longitude = lng,
                place = if (lat != null && lng != null) geocode(lat, lng) else null,
            )
        } catch (e: Exception) {
            MediaDetails()
        } finally {
            runCatching { retriever.release() }
        }
    }

    @Suppress("DEPRECATION")
    private fun geocode(lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        return try {
            val address = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)
                ?.firstOrNull() ?: return null
            listOfNotNull(
                address.locality ?: address.subAdminArea,
                address.adminArea,
                address.countryName
            ).distinct().joinToString(", ").ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }

    /** Crea un MediaItem per un file aperto da un'altra app (intent VIEW). */
    suspend fun externalItem(uri: Uri, typeHint: String?): MediaItem = withContext(Dispatchers.IO) {
        var name = uri.lastPathSegment ?: "File"
        var size = 0L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val iName = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val iSize = c.getColumnIndex(OpenableColumns.SIZE)
                        if (iName >= 0) c.getString(iName)?.let { name = it }
                        if (iSize >= 0) size = c.getLong(iSize)
                    }
                }
        }
        val mime = typeHint?.takeIf { it.contains('/') && !it.endsWith("*") }
            ?: runCatching { resolver.getType(uri) }.getOrNull()
            ?: typeHint
            ?: "image/*"
        MediaItem(
            id = -(abs(uri.hashCode().toLong()) + 1),
            uri = uri,
            name = name,
            mime = mime,
            isVideo = mime.startsWith("video"),
            dateTaken = System.currentTimeMillis(),
            dateModified = 0,
            width = 0,
            height = 0,
            orientation = 0,
            size = size,
            duration = 0,
            bucketId = 0,
            bucketName = "",
            relativePath = "",
            systemFavorite = false,
            external = true,
        )
    }

    companion object {
        fun writableRelativePath(item: MediaItem): String {
            val path = item.relativePath
            val allowed = if (item.isVideo) listOf("DCIM/", "Movies/") else listOf("DCIM/", "Pictures/")
            return if (allowed.any { path.startsWith(it, ignoreCase = true) }) path
            else if (item.isVideo) "Movies/Galleria/" else "Pictures/Galleria/"
        }

        fun splitName(name: String, defaultExt: String): Pair<String, String> {
            val dot = name.lastIndexOf('.')
            return if (dot > 0) name.substring(0, dot) to name.substring(dot)
            else (name.ifEmpty { "IMG_" + System.currentTimeMillis() }) to defaultExt
        }

        private fun formatNumber(value: Double): String =
            if (abs(value - Math.round(value)) < 0.05) Math.round(value).toString()
            else String.format(Locale.ITALIAN, "%.1f", value)

        private fun formatExposure(seconds: Double): String =
            if (seconds >= 1) formatNumber(seconds) + " s"
            else "1/" + Math.round(1.0 / seconds) + " s"
    }
}
