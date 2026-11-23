package com.afam.pura.camera

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.location.Location
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Salva foto e video in DCIM/Camera, dove li trovano Galleria e Google Foto. */
class MediaSaver(private val context: Context) {

    private val resolver: ContentResolver = context.contentResolver
    private val nameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private var lastName = ""
    private var sameNameCount = 0

    @Synchronized
    fun newBaseName(prefix: String = "IMG"): String {
        val base = "${prefix}_${nameFormat.format(Date())}"
        sameNameCount = if (base == lastName) sameNameCount + 1 else 0
        lastName = base
        return if (sameNameCount == 0) base else "${base}_$sameNameCount"
    }

    /**
     * Salva un JPEG. Se servono ritocchi (ritaglio delle proporzioni, specchiatura del selfie)
     * li applica senza ricomprimere quando possibile.
     */
    fun saveJpeg(
        bytes: ByteArray,
        baseName: String,
        mime: String = "image/jpeg",
        cropAspect: Float? = null,
        mirror: Boolean = false,
        quality: Int = 95,
        target: Uri? = null,
    ): Uri? {
        var data = bytes
        if (cropAspect != null) data = cropJpeg(data, cropAspect, quality) ?: data
        if (mirror) data = mirrorExif(data) ?: data
        if (target != null) {
            return runCatching {
                resolver.openOutputStream(target, "wt")?.use { it.write(data) }
                target
            }.getOrNull()
        }
        return insert(
            collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            name = "$baseName.jpg",
            mime = mime,
        ) { out -> out.write(data) }
    }

    /** Salva il RAW in DNG (pixel a 16 bit già copiati in modo compatto). */
    fun saveDng(
        chars: CameraCharacteristics,
        result: TotalCaptureResult,
        raw: java.nio.ByteBuffer,
        size: Size,
        exifOrientation: Int,
        location: Location?,
        baseName: String,
    ): Uri? = runCatching {
        val creator = DngCreator(chars, result)
        try {
            creator.setOrientation(exifOrientation)
            location?.let { creator.setLocation(it) }
            insert(
                collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                name = "$baseName.dng",
                mime = "image/x-adobe-dng",
            ) { out -> creator.writeByteBuffer(out, size, raw, 0) }
        } finally {
            creator.close()
        }
    }.getOrNull()

    /** Crea il file del video (in attesa) e restituisce il descrittore su cui scrivere. */
    fun createVideo(baseName: String): Pair<Uri, ParcelFileDescriptor>? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$baseName.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = runCatching {
            resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
        }.getOrNull() ?: return null
        val pfd = runCatching { resolver.openFileDescriptor(uri, "rw") }.getOrNull()
        if (pfd == null) {
            resolver.delete(uri, null, null)
            return null
        }
        return uri to pfd
    }

    fun publish(uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        runCatching { resolver.update(uri, values, null, null) }
    }

    fun delete(uri: Uri) {
        runCatching { resolver.delete(uri, null, null) }
    }

    private inline fun insert(
        collection: Uri,
        name: String,
        mime: String,
        write: (java.io.OutputStream) -> Unit,
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { write(it) } ?: error("stream nullo")
            publish(uri)
            uri
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            null
        }
    }

    /** Ritaglia al centro il JPEG alle proporzioni richieste (solo se il sensore non le offre). */
    private fun cropJpeg(bytes: ByteArray, aspect: Float, quality: Int): ByteArray? = runCatching {
        val decoder = BitmapRegionDecoder.newInstance(bytes, 0, bytes.size)
        val w = decoder.width
        val h = decoder.height
        val long = max(w, h)
        val short = min(w, h)
        val current = long.toFloat() / short
        val rect = if (aspect < current) {
            // Accorcia il lato lungo.
            val newLong = (short * aspect).toInt()
            if (w >= h) Rect((w - newLong) / 2, 0, (w - newLong) / 2 + newLong, h)
            else Rect(0, (h - newLong) / 2, w, (h - newLong) / 2 + newLong)
        } else {
            val newShort = (long / aspect).toInt()
            if (w >= h) Rect(0, (h - newShort) / 2, w, (h - newShort) / 2 + newShort)
            else Rect((w - newShort) / 2, 0, (w - newShort) / 2 + newShort, h)
        }
        val bmp = decoder.decodeRegion(rect, BitmapFactory.Options())
        decoder.recycle()
        val out = ByteArrayOutputStream(bytes.size)
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        bmp.recycle()
        copyExif(bytes, out.toByteArray(), Size(rect.width(), rect.height()))
    }.getOrNull()

    private fun copyExif(from: ByteArray, to: ByteArray, size: Size): ByteArray {
        val src = ExifInterface(ByteArrayInputStream(from))
        val tmp = File.createTempFile("crop", ".jpg", context.cacheDir)
        return try {
            tmp.writeBytes(to)
            val dst = ExifInterface(tmp)
            for (tag in EXIF_TAGS) src.getAttribute(tag)?.let { dst.setAttribute(tag, it) }
            dst.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, size.width.toString())
            dst.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, size.height.toString())
            dst.saveAttributes()
            tmp.readBytes()
        } finally {
            tmp.delete()
        }
    }

    /** Specchia il selfie cambiando solo l'orientamento EXIF: nessuna perdita di qualità. */
    private fun mirrorExif(bytes: ByteArray): ByteArray? = runCatching {
        val tmp = File.createTempFile("mirror", ".jpg", context.cacheDir)
        try {
            tmp.writeBytes(bytes)
            val exif = ExifInterface(tmp)
            exif.flipHorizontally()
            exif.saveAttributes()
            tmp.readBytes()
        } finally {
            tmp.delete()
        }
    }.getOrNull()

    companion object {
        private val EXIF_TAGS = listOf(
            ExifInterface.TAG_ORIENTATION, ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED, ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_SUBSEC_TIME, ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
            ExifInterface.TAG_APERTURE_VALUE, ExifInterface.TAG_SHUTTER_SPEED_VALUE,
            ExifInterface.TAG_EXPOSURE_BIAS_VALUE, ExifInterface.TAG_FLASH, ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_METERING_MODE, ExifInterface.TAG_EXPOSURE_PROGRAM, ExifInterface.TAG_EXPOSURE_MODE,
            ExifInterface.TAG_DIGITAL_ZOOM_RATIO, ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF, ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_SOFTWARE,
        )

        /** Miniatura veloce: usa quella già dentro l'EXIF quando c'è. */
        fun thumbnail(bytes: ByteArray, maxSide: Int = 256): Bitmap? = runCatching {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            val rotation = exif.rotationDegrees
            val flip = exif.isFlipped
            val thumb = exif.thumbnailBitmap ?: run {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            if (rotation == 0 && !flip) thumb else {
                val m = Matrix()
                if (flip) m.postScale(-1f, 1f)
                m.postRotate(rotation.toFloat())
                Bitmap.createBitmap(thumb, 0, 0, thumb.width, thumb.height, m, true)
            }
        }.getOrNull()

        fun exifOrientation(degrees: Int): Int = when ((degrees % 360 + 360) % 360) {
            90 -> ExifInterface.ORIENTATION_ROTATE_90
            180 -> ExifInterface.ORIENTATION_ROTATE_180
            270 -> ExifInterface.ORIENTATION_ROTATE_270
            else -> ExifInterface.ORIENTATION_NORMAL
        }
    }
}
