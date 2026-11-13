package com.afam.galleria.data

import android.net.Uri
import androidx.compose.runtime.Immutable

/** Un elemento della libreria (foto o video) letto da MediaStore. */
@Immutable
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mime: String,
    val isVideo: Boolean,
    /** Data di scatto in millisecondi. */
    val dateTaken: Long,
    /** Ultima modifica in secondi (usata come chiave per le cache). */
    val dateModified: Long,
    val width: Int,
    val height: Int,
    val orientation: Int,
    val size: Long,
    /** Durata in millisecondi (solo video). */
    val duration: Long,
    val bucketId: Long,
    val bucketName: String,
    val relativePath: String,
    val systemFavorite: Boolean,
    /** Scadenza nel cestino (secondi), 0 se non è nel cestino. */
    val expires: Long = 0L,
    /** true se l'elemento non proviene da MediaStore (aperto da un'altra app). */
    val external: Boolean = false,
) {
    val displayWidth: Int get() = if (orientation % 180 != 0) height else width
    val displayHeight: Int get() = if (orientation % 180 != 0) width else height

    val aspect: Float
        get() = if (displayWidth > 0 && displayHeight > 0) displayWidth.toFloat() / displayHeight else 1f

    val isGif: Boolean get() = mime.equals("image/gif", ignoreCase = true)

    val isRaw: Boolean
        get() = mime.contains("dng", true) || mime.contains("raw", true) ||
            name.endsWith(".dng", true)

    val isScreenshot: Boolean
        get() = !isVideo && (bucketName.contains("screenshot", true) ||
            relativePath.contains("screenshot", true) ||
            name.startsWith("Screenshot", true))

    val isScreenRecording: Boolean
        get() = isVideo && (bucketName.contains("screen", true) ||
            relativePath.contains("screenrecord", true) ||
            relativePath.contains("screen record", true) ||
            name.startsWith("screen", true))

    val isPanorama: Boolean
        get() = !isVideo && !isScreenshot && displayWidth > 0 && aspect >= 2.2f

    val isSelfie: Boolean
        get() = !isVideo && (bucketName.contains("selfie", true) ||
            relativePath.contains("selfie", true) || name.contains("selfie", true))

    val isEditable: Boolean get() = !isVideo && !isGif

    /** Chiave per la cache delle miniature: cambia se il file viene modificato. */
    val cacheKey: String get() = "$id@$dateModified@${uri.hashCode()}"
}
