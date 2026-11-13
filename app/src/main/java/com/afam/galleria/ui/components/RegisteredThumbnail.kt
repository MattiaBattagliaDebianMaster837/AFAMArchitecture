package com.afam.galleria.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.afam.galleria.data.MediaItem
import com.afam.galleria.ui.nav.LocalThumbRegistry

/** Miniatura che registra la propria posizione per l'animazione di apertura del visualizzatore. */
@Composable
fun RegisteredThumbnail(
    item: MediaItem,
    modifier: Modifier = Modifier,
    size: Int = Thumbs.GRID,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val registry = LocalThumbRegistry.current
    val holder = remember { arrayOfNulls<Rect>(1) }
    DisposableEffect(item.id) {
        onDispose { registry.remove(item.id, holder[0]) }
    }
    MediaThumbnail(
        item = item,
        modifier = modifier.onGloballyPositioned { coords ->
            val rect = coords.boundsInWindow()
            holder[0] = rect
            registry.put(item.id, rect)
        },
        size = size,
        contentScale = contentScale,
    )
}
