package com.afam.galleria.ui.foryou

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afam.galleria.GalleryViewModel
import com.afam.galleria.data.Keys
import com.afam.galleria.data.Memory
import com.afam.galleria.ui.components.EmptyState
import com.afam.galleria.ui.components.LargeTitle
import com.afam.galleria.ui.components.MediaThumbnail
import com.afam.galleria.ui.components.RegisteredThumbnail
import com.afam.galleria.ui.components.SectionHeader
import com.afam.galleria.ui.components.Thumbs
import com.afam.galleria.ui.components.iosClickable
import com.afam.galleria.ui.nav.LocalNavigator
import com.afam.galleria.ui.nav.LocalThumbRegistry
import com.afam.galleria.ui.nav.Route
import com.afam.galleria.ui.theme.Ios
import com.afam.galleria.ui.theme.IosType

@Composable
fun ForYouScreen(vm: GalleryViewModel, bottomBarHeight: Dp) {
    val forYou by vm.forYou.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val registry = LocalThumbRegistry.current
    val c = Ios.colors
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(c.background),
        contentPadding = PaddingValues(top = statusTop + 44.dp, bottom = bottomBarHeight + 24.dp),
    ) {
        item { LargeTitle("Per te") }

        val empty = forYou.memories.isEmpty() && forYou.onThisDay.isEmpty() && forYou.featured.isEmpty()
        if (empty) {
            item {
                EmptyState(
                    Icons.Rounded.AutoAwesome,
                    if (library.loaded) "Ancora nessun ricordo" else "Preparazione dei ricordi…",
                    "Man mano che scatti foto, qui troverai ricordi creati automaticamente e foto in evidenza.",
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 80.dp),
                )
            }
        }

        if (forYou.memories.isNotEmpty()) {
            item { SectionHeader("Ricordi") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(forYou.memories, key = { it.id }) { memory ->
                        MemoryCard(memory, width = screenWidth * 0.8f, aspect = 0.78f) {
                            nav.push(Route.MemoryPlayer(memory))
                        }
                    }
                }
            }
        }

        if (forYou.onThisDay.isNotEmpty()) {
            item { SectionHeader("In questo giorno") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(forYou.onThisDay, key = { it.id }) { memory ->
                        MemoryCard(memory, width = screenWidth * 0.62f, aspect = 1f) {
                            nav.push(Route.MemoryPlayer(memory))
                        }
                    }
                }
            }
        }

        if (forYou.featured.isNotEmpty()) {
            item { SectionHeader("Foto in evidenza") }
            item {
                val ids = forYou.featured.map { it.id }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(forYou.featured, key = { it.id }) { item ->
                        RegisteredThumbnail(
                            item,
                            Modifier
                                .width(screenWidth * 0.62f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .iosClickable {
                                    nav.push(Route.Viewer(Keys.IDS, item.id, ids = ids, origin = registry[item.id]))
                                },
                            size = Thumbs.LARGE,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryCard(memory: Memory, width: Dp, aspect: Float, onClick: () -> Unit) {
    val shadow = Shadow(Color.Black.copy(alpha = 0.45f), blurRadius = 10f)
    Box(
        Modifier
            .width(width)
            .aspectRatio(aspect)
            .shadow(6.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Ios.colors.placeholder)
            .iosClickable(onClick = onClick)
    ) {
        MediaThumbnail(memory.cover, Modifier.fillMaxSize(), size = Thumbs.LARGE)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.65f),
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp)
        ) {
            Text(
                memory.title,
                style = IosType.title2.copy(shadow = shadow),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                memory.subtitle.uppercase(),
                style = IosType.caption.copy(fontWeight = FontWeight.SemiBold, shadow = shadow),
                color = Color.White.copy(alpha = 0.9f),
            )
        }
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.3f))
                .padding(4.dp),
        )
    }
}
