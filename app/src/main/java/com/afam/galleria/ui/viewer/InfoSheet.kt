package com.afam.galleria.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afam.galleria.GalleryViewModel
import com.afam.galleria.data.MediaDetails
import com.afam.galleria.data.MediaItem
import com.afam.galleria.ui.components.Hairline
import com.afam.galleria.ui.components.iosClickable
import com.afam.galleria.ui.theme.Ios
import com.afam.galleria.ui.theme.IosType
import com.afam.galleria.util.Dates
import com.afam.galleria.util.ShareUtil
import java.util.Locale

private fun formatLabel(item: MediaItem): String {
    val sub = item.mime.substringAfter('/', "").lowercase(Locale.ROOT)
    return when {
        sub.contains("jpeg") || sub.contains("jpg") -> "JPEG"
        sub.contains("heic") || sub.contains("heif") -> "HEIF"
        sub.contains("png") -> "PNG"
        sub.contains("webp") -> "WEBP"
        sub.contains("gif") -> "GIF"
        sub.contains("dng") -> "RAW"
        sub.contains("avif") -> "AVIF"
        sub.contains("mp4") -> "MP4"
        sub.contains("quicktime") -> "MOV"
        sub.contains("3gp") -> "3GP"
        sub.contains("webm") -> "WEBM"
        sub.contains("matroska") -> "MKV"
        else -> sub.uppercase(Locale.ROOT).ifEmpty { if (item.isVideo) "VIDEO" else "FOTO" }
    }
}

@Composable
fun InfoSheet(
    item: MediaItem,
    vm: GalleryViewModel,
    favorite: Boolean,
    onDismiss: () -> Unit,
    onShowOnMap: (() -> Unit)? = null,
) {
    val c = Ios.colors
    val context = LocalContext.current
    val details by produceState<MediaDetails?>(null, item.id, item.uri) {
        value = vm.repo.details(item)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.groupedBackground,
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.tertiaryLabel) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        Dates.fullDate(item.dateTaken),
                        style = IosType.headline,
                        color = c.label,
                    )
                    Text(
                        Dates.time(item.dateTaken),
                        style = IosType.subhead,
                        color = c.secondaryLabel,
                    )
                }
                if (favorite) {
                    Icon(Icons.Rounded.Favorite, contentDescription = "Preferito", tint = c.red, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.height(14.dp))

            // Scheda tecnica, come su iOS.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.card)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(c.elevated.copy(alpha = if (c.isDark) 1f else 0.6f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        details?.camera ?: if (item.isVideo) "Video" else "Foto",
                        style = IosType.headline,
                        color = c.label,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        formatLabel(item),
                        style = IosType.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = c.secondaryLabel,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(c.fill)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    details?.lens?.let {
                        Text(it, style = IosType.subhead, color = c.secondaryLabel)
                    }
                    Text(
                        item.name,
                        style = IosType.subhead,
                        color = c.label,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val w = item.displayWidth
                    val h = item.displayHeight
                    val parts = mutableListOf<String>()
                    if (w > 0 && h > 0) {
                        if (!item.isVideo) {
                            val mp = w.toLong() * h / 1_000_000.0
                            if (mp >= 0.5) parts += String.format(Locale.ITALIAN, "%.0f MP", mp)
                        }
                        parts += "$w × $h"
                    }
                    parts += Dates.fileSize(item.size)
                    if (item.isVideo && item.duration > 0) parts += Dates.duration(item.duration)
                    Text(parts.joinToString(" · "), style = IosType.subhead, color = c.secondaryLabel)
                }
                val d = details
                val exif = listOfNotNull(d?.iso, d?.focal, d?.aperture, d?.exposure)
                if (exif.isNotEmpty()) {
                    Hairline()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        exif.forEachIndexed { i, value ->
                            if (i > 0) {
                                Box(
                                    Modifier
                                        .width(0.5.dp)
                                        .height(18.dp)
                                        .background(c.separator)
                                )
                            }
                            Text(value, style = IosType.footnote, color = c.secondaryLabel)
                        }
                    }
                }
            }

            val d = details
            if (d?.latitude != null && d.longitude != null) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.card)
                        .iosClickable { ShareUtil.openMap(context, d.latitude, d.longitude, d.place) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Place, contentDescription = null, tint = c.accent)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.place ?: "Posizione", style = IosType.headline, color = c.label)
                        Text(
                            String.format(Locale.ITALIAN, "%.5f, %.5f", d.latitude, d.longitude),
                            style = IosType.footnote,
                            color = c.secondaryLabel,
                        )
                    }
                    Text("Mappe", style = IosType.subhead, color = c.accent)
                }
                if (onShowOnMap != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Mostra sulla mappa di Galleria",
                        style = IosType.headline,
                        color = c.accent,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.card)
                            .iosClickable {
                                onDismiss()
                                onShowOnMap()
                            }
                            .padding(vertical = 14.dp),
                    )
                }
            }

            if (!item.external && item.bucketName.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.card)
                        .padding(14.dp)
                ) {
                    Text("Cartella", style = IosType.footnote, color = c.secondaryLabel)
                    Text(item.bucketName, style = IosType.body, color = c.label)
                    if (item.relativePath.isNotEmpty()) {
                        Text(item.relativePath.trimEnd('/'), style = IosType.footnote, color = c.secondaryLabel)
                    }
                }
            }
        }
    }
}
