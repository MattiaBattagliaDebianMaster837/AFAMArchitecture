package com.afam.galleria.ui.viewer

import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.afam.galleria.data.MediaItem
import com.afam.galleria.ui.components.MediaThumbnail
import com.afam.galleria.ui.components.Thumbs
import com.afam.galleria.ui.components.iosClickable
import com.afam.galleria.ui.theme.IosType
import com.afam.galleria.util.Dates
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.media3.common.MediaItem as ExoMediaItem

@Composable
fun VideoPage(
    item: MediaItem,
    isCurrent: Boolean,
    active: Boolean,
    chromeVisible: Boolean,
    controlsBottomPadding: Dp,
    dragOffset: Offset,
    dragScale: Float,
    onTap: () -> Unit,
    onDismissDrag: (Offset) -> Unit,
    onDismissEnd: (Float) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var firstFrame by remember { mutableStateOf(false) }
    var aspect by remember { mutableFloatStateOf(item.aspect) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(item.duration) }
    var muted by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableStateOf<Float?>(null) }

    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDismissDrag by rememberUpdatedState(onDismissDrag)
    val currentOnDismissEnd by rememberUpdatedState(onDismissEnd)

    val shouldPlay = isCurrent && active
    DisposableEffect(shouldPlay, item.uri) {
        if (!shouldPlay) return@DisposableEffect onDispose { }
        val exo = ExoPlayer.Builder(context).build()
        exo.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,
        )
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                ended = playbackState == Player.STATE_ENDED
                if (playbackState == Player.STATE_READY && exo.duration > 0) duration = exo.duration
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onRenderedFirstFrame() {
                firstFrame = true
            }
        }
        exo.addListener(listener)
        exo.setMediaItem(ExoMediaItem.fromUri(item.uri))
        exo.volume = if (muted) 0f else 1f
        exo.prepare()
        exo.playWhenReady = true
        player = exo
        onDispose {
            exo.removeListener(listener)
            exo.release()
            player = null
            playing = false
            firstFrame = false
        }
    }

    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        while (true) {
            if (seekFraction == null) position = p.currentPosition.coerceAtLeast(0L)
            delay(120)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player?.pause() }

    fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else {
            if (ended) p.seekTo(0)
            p.play()
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(item.id) {
                viewerGestures(
                    zoom = null,
                    onZoomChange = {},
                    onDismissDrag = { currentOnDismissDrag(it) },
                    onDismissEnd = { currentOnDismissEnd(it) },
                    onZoomEnd = {},
                )
            }
            .pointerInput(item.id) {
                detectTapGestures(onTap = { currentOnTap() })
            }
    ) {
        val fit = fitSize(Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()), aspect)
        val w = with(density) { fit.width.toDp() }
        val h = with(density) { fit.height.toDp() }
        Box(
            Modifier
                .align(Alignment.Center)
                .size(w, h)
                .graphicsLayer {
                    if (isCurrent) {
                        scaleX = dragScale
                        scaleY = dragScale
                        translationX = dragOffset.x
                        translationY = dragOffset.y
                    }
                }
        ) {
            val p = player
            if (p != null) {
                AndroidView(
                    factory = { ctx -> TextureView(ctx) },
                    update = { view ->
                        if (view.tag !== p) {
                            p.setVideoTextureView(view)
                            view.tag = p
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (!firstFrame) {
                MediaThumbnail(
                    item,
                    Modifier.fillMaxSize(),
                    size = Thumbs.LARGE,
                    contentScale = ContentScale.Crop,
                    placeholder = Color.Transparent,
                )
            }
        }

        AnimatedVisibility(
            visible = isCurrent && (!playing || ended) && chromeVisible && dragOffset == Offset.Zero,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .iosClickable {
                        if (player == null) return@iosClickable
                        togglePlay()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, "Riproduci", tint = Color.White, modifier = Modifier.size(46.dp))
            }
        }

        AnimatedVisibility(
            visible = isCurrent && chromeVisible && dragOffset == Offset.Zero,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = controlsBottomPadding + 8.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val shownPosition = seekFraction?.let { (it * duration).toLong() } ?: position
            Row(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xB3202022))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .iosClickable { togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) "Pausa" else "Riproduci",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Text(
                    Dates.duration(shownPosition),
                    style = IosType.caption.copy(fontFamily = FontFamily.Monospace),
                    color = Color.White,
                )
                Spacer(Modifier.width(8.dp))
                Scrubber(
                    fraction = if (duration > 0) (shownPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    onSeek = { seekFraction = it },
                    onSeekEnd = { f ->
                        player?.seekTo((f * duration).toLong())
                        position = (f * duration).toLong()
                        seekFraction = null
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "-" + Dates.duration((duration - shownPosition).coerceAtLeast(0)),
                    style = IosType.caption.copy(fontFamily = FontFamily.Monospace),
                    color = Color.White,
                )
                Box(
                    Modifier
                        .size(40.dp)
                        .iosClickable {
                            muted = !muted
                            player?.volume = if (muted) 0f else 1f
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                        contentDescription = if (muted) "Attiva audio" else "Disattiva audio",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/** Barra di avanzamento trascinabile in stile iOS. */
@Composable
fun Scrubber(
    fraction: Float,
    onSeek: (Float) -> Unit,
    onSeekEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.3f),
    progressColor: Color = Color.White,
) {
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnSeekEnd by rememberUpdatedState(onSeekEnd)
    BoxWithConstraints(
        modifier
            .height(32.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    var f = (down.position.x / width).coerceIn(0f, 1f)
                    currentOnSeek(f)
                    down.consume()
                    drag(down.id) { change ->
                        f = (change.position.x / width).coerceIn(0f, 1f)
                        currentOnSeek(f)
                        change.consume()
                    }
                    currentOnSeekEnd(f)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(trackColor)
        )
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(progressColor)
        )
        val thumbPx = with(LocalDensity.current) { 14.dp.toPx() }
        Box(
            Modifier
                .offset { IntOffset(((widthPx - thumbPx) * fraction).roundToInt(), 0) }
                .size(14.dp)
                .shadow(2.dp, CircleShape)
                .background(progressColor, CircleShape)
        )
    }
}
