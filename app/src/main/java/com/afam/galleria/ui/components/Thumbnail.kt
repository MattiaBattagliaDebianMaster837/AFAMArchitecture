package com.afam.galleria.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.os.CancellationSignal
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.afam.galleria.data.MediaItem
import com.afam.galleria.ui.theme.Ios
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.max

/** Cache e caricamento delle miniature (usa le miniature di sistema di MediaStore). */
object Thumbs {
    const val SMALL = 160
    const val GRID = 320
    const val LARGE = 720

    private val maxKb: Int = (Runtime.getRuntime().maxMemory() / 1024L / 5L).toInt()

    private val cache = object : LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    private val dispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
    private val fullDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

    fun key(item: MediaItem, size: Int): String = "${item.cacheKey}#$size"

    fun peek(item: MediaItem, size: Int): Bitmap? = cache.get(key(item, size))

    /** La miniatura migliore già in memoria (per mostrare subito qualcosa). */
    fun peekBest(item: MediaItem): Bitmap? =
        peek(item, LARGE) ?: peek(item, GRID) ?: peek(item, SMALL)

    suspend fun load(context: Context, item: MediaItem, size: Int): Bitmap? {
        peek(item, size)?.let { return it }
        return withContext(dispatcher) {
            peek(item, size) ?: run {
                val signal = CancellationSignal()
                val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
                    if (cause != null) signal.cancel()
                }
                try {
                    decode(context, item, size, signal)?.also { cache.put(key(item, size), it) }
                } finally {
                    handle?.dispose()
                }
            }
        }
    }

    private fun decode(context: Context, item: MediaItem, size: Int, signal: CancellationSignal): Bitmap? {
        try {
            return context.contentResolver.loadThumbnail(item.uri, android.util.Size(size, size), signal)
        } catch (e: Exception) {
            if (signal.isCanceled) return null
        }
        return try {
            if (item.isVideo) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, item.uri)
                    retriever.getScaledFrameAtTime(
                        0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, size, size
                    )
                } finally {
                    runCatching { retriever.release() }
                }
            } else {
                decodeSampled(context, item, size, software = false)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Decodifica l'immagine a piena qualità ridotta a [maxDim] (lato più lungo). */
    suspend fun loadFull(context: Context, item: MediaItem, maxDim: Int): Bitmap? =
        withContext(fullDispatcher) {
            try {
                decodeSampled(context, item, maxDim, software = false)
            } catch (e: Throwable) {
                null
            }
        }

    fun decodeSampled(context: Context, item: MediaItem, maxDim: Int, software: Boolean): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, item.uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            var sample = 1
            while (longest / sample > maxDim) sample *= 2
            if (sample > 1) decoder.setTargetSampleSize(sample)
            if (software) decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    fun clear() = cache.evictAll()
}

/** Miniatura quadrata (o con il [contentScale] richiesto) di un elemento. */
@Composable
fun MediaThumbnail(
    item: MediaItem,
    modifier: Modifier = Modifier,
    size: Int = Thumbs.GRID,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: Color = Ios.colors.placeholder,
    colorFilter: ColorFilter? = null,
    alignment: Alignment = Alignment.Center,
) {
    val context = LocalContext.current
    val key = Thumbs.key(item, size)
    var bitmap by remember(key) {
        mutableStateOf(Thumbs.peek(item, size) ?: Thumbs.peekBest(item))
    }
    LaunchedEffect(key) {
        if (Thumbs.peek(item, size) == null) {
            Thumbs.load(context, item, size)?.let { bitmap = it }
        }
    }
    Box(modifier.background(placeholder)) {
        val b = bitmap
        if (b != null) {
            val image = remember(b) { b.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                alignment = alignment,
                colorFilter = colorFilter,
            )
        }
    }
}
