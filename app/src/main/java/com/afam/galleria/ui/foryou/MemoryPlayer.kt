package com.afam.galleria.ui.foryou

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afam.galleria.data.MediaItem
import com.afam.galleria.data.Memory
import com.afam.galleria.ui.components.Thumbs
import com.afam.galleria.ui.components.iosClickable
import com.afam.galleria.ui.theme.IosType
import com.afam.galleria.util.ShareUtil
import com.afam.galleria.util.SystemBarsEffect
import kotlinx.coroutines.launch

private const val SLIDE_MS = 4200

/** Riproduzione di un ricordo: dissolvenze ed effetto "Ken Burns", come su iOS. */
@Composable
fun MemoryPlayerScreen(memory: Memory, isTop: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = memory.items
    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    val progress = remember { Animatable(0f) }

    SystemBarsEffect(darkBackground = true, hidden = !chrome)
    BackHandler(enabled = isTop) { onClose() }

    LaunchedEffect(index, paused, finished) {
        if (paused || finished) {
            progress.stop()
            return@LaunchedEffect
        }
        val remaining = ((1f - progress.value) * SLIDE_MS).toInt().coerceAtLeast(1)
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        if (index < items.lastIndex) {
            progress.snapTo(0f)
            index++
        } else {
            finished = true
            chrome = true
        }
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2500)
        if (!paused) chrome = false
    }

    fun go(delta: Int) {
        scope.launch {
            val next = (index + delta).coerceIn(0, items.lastIndex)
            progress.snapTo(0f)
            finished = false
            index = next
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(items) {
                detectTapGestures(
                    onTap = { pos ->
                        val w = size.width
                        when {
                            pos.x < w * 0.28f -> go(-1)
                            pos.x > w * 0.72f -> go(1)
                            else -> chrome = !chrome
                        }
                    },
                    onLongPress = { paused = true },
                    onPress = {
                        tryAwaitRelease()
                    },
                )
            }
    ) {
        Crossfade(targetState = index, animationSpec = tween(900), label = "memory") { i ->
            KenBurnsImage(items[i], seed = i)
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.25f to Color.Transparent,
                        0.7f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.5f),
                    )
                )
        )

        // Titolo iniziale.
        AnimatedVisibility(
            visible = index == 0 && memory.title.isNotEmpty() && progress.value < 0.75f,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(600)),
            exit = fadeOut(tween(700)),
        ) {
            val shadow = Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 14f)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Text(
                    memory.title,
                    style = IosType.largeTitle.copy(fontSize = 38.sp, lineHeight = 44.sp, shadow = shadow),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                if (memory.subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        memory.subtitle.uppercase(),
                        style = IosType.footnote.copy(fontWeight = FontWeight.SemiBold, shadow = shadow, letterSpacing = 2.sp),
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Barra di avanzamento a segmenti (o continua se gli elementi sono tanti).
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            if (items.size <= 30) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    items.indices.forEach { i ->
                        val fill = when {
                            i < index -> 1f
                            i == index -> progress.value
                            else -> 0f
                        }
                        ProgressSegment(fill, Modifier.weight(1f))
                    }
                }
            } else {
                ProgressSegment((index + progress.value) / items.size, Modifier.fillMaxWidth())
            }
            AnimatedVisibility(visible = chrome, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundButton(Icons.Rounded.Close, "Chiudi", onClose)
                    Spacer(Modifier.weight(1f))
                    RoundButton(Icons.Rounded.IosShare, "Condividi") {
                        ShareUtil.share(context, listOf(items[index]))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = chrome || paused || finished,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f))
                    .iosClickable {
                        if (finished) {
                            scope.launch {
                                progress.snapTo(0f)
                                index = 0
                                finished = false
                                paused = false
                            }
                        } else {
                            paused = !paused
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when {
                        finished -> Icons.Rounded.Replay
                        paused -> Icons.Rounded.PlayArrow
                        else -> Icons.Rounded.Pause
                    },
                    contentDescription = if (paused) "Riproduci" else "Pausa",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}

@Composable
private fun ProgressSegment(fraction: Float, modifier: Modifier) {
    Box(
        modifier
            .height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.3f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(3.dp)
                .background(Color.White)
        )
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .iosClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun KenBurnsImage(item: MediaItem, seed: Int) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val thumb = remember(item.cacheKey) { Thumbs.peekBest(item) }
    val full by produceState<Bitmap?>(null, item.cacheKey) {
        value = Thumbs.loadFull(context, item, 2048)
    }
    val anim = remember { Animatable(0f) }
    LaunchedEffect(item.id) {
        anim.animateTo(1f, tween(SLIDE_MS + 1800, easing = LinearEasing))
    }
    val bitmap = full ?: thumb
    val dirX = if (seed % 2 == 0) 1f else -1f
    val dirY = if ((seed / 2) % 2 == 0) 1f else -1f
    val zoomIn = seed % 3 != 1
    val drift = with(density) { 18.dp.toPx() }
    if (bitmap != null) {
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = anim.value
                    val s = if (zoomIn) 1.04f + 0.12f * t else 1.16f - 0.12f * t
                    scaleX = s
                    scaleY = s
                    translationX = dirX * drift * (t - 0.5f)
                    translationY = dirY * drift * 0.6f * (t - 0.5f)
                },
        )
    } else {
        Box(Modifier.fillMaxSize().background(Color.Black))
    }
}
