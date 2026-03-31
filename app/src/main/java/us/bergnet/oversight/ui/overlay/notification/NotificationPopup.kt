package us.bergnet.oversight.ui.overlay.notification

import android.view.TextureView
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.MediaSource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import us.bergnet.oversight.data.model.NotificationLayout
import us.bergnet.oversight.data.model.ReceivedNotification
import us.bergnet.oversight.data.model.enums.HotCorner
import us.bergnet.oversight.util.ColorParser

@Composable
fun NotificationPopup(
    notification: ReceivedNotification?,
    layout: NotificationLayout,
    durationSeconds: Int,
    corner: HotCorner = HotCorner.TOP_END,
    onDismiss: () -> Unit
) {
    var visible by remember(notification?.id) { mutableStateOf(false) }
    val progress = remember(notification?.id) { Animatable(1f) }

    LaunchedEffect(notification?.id) {
        if (notification != null) {
            visible = true
            val durationMs = (notification.duration ?: durationSeconds) * 1000
            progress.snapTo(1f)
            launch {
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = durationMs, easing = LinearEasing)
                )
            }
            delay(durationMs.toLong())
            visible = false
            delay(500L) // wait for exit animation
            onDismiss()
        }
    }

    val expandAlignment = when (corner) {
        HotCorner.TOP_START -> Alignment.TopStart
        HotCorner.TOP_END -> Alignment.TopEnd
        HotCorner.BOTTOM_START -> Alignment.BottomStart
        HotCorner.BOTTOM_END -> Alignment.BottomEnd
    }

    AnimatedVisibility(
        visible = visible && notification != null,
        enter = expandIn(expandFrom = expandAlignment, clip = false) + fadeIn(),
        exit = shrinkOut(shrinkTowards = expandAlignment, clip = false) + fadeOut()
    ) {
        if (notification != null) {
            val bgColor = ColorParser.parseOrDefault(
                layout.backgroundColor, Color.Black.copy(alpha = 0.4f)
            )
            val hasImage = layout.imageDisplay && !notification.image.isNullOrBlank()
            val hasVideo = !notification.video.isNullOrBlank()
            val hasMedia = hasImage || hasVideo

            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .width(IntrinsicSize.Max)
                    .widthIn(
                        min = if (hasMedia) 240.dp else 0.dp,
                        max = layout.maxWidth.dp
                    )
                    .clip(RoundedCornerShape(9.dp))
                    .background(bgColor),
            ) {
                // Row 1: Layout content (icon + text)
                NotificationLayoutContent(
                    notification, layout,
                    overrideBgColor = Color.Transparent
                )

                val barColor = ColorParser.parseOrDefault(layout.progressBarColor, Color(0xFF2196F3))

                if (hasMedia) {
                    // Media + progress bar overlaid at the bottom edge of the media.
                    // The media goes flush to the card edge; the outer clip rounds its corners.
                    Box {
                        if (hasImage) {
                            NotificationImage(
                                url = notification.image!!,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (hasVideo) {
                            NotificationVideo(
                                url = notification.video!!,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                            )
                        }
                        // Progress bar overlaid on the bottom of the media
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.value)
                                .height(3.dp)
                                .align(Alignment.BottomStart)
                                .background(barColor)
                        )
                    }
                } else {
                    // No media — progress bar sits below the text at the card bottom
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.value)
                            .height(3.dp)
                            .background(barColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationLayoutContent(
    notification: ReceivedNotification,
    layout: NotificationLayout,
    overrideBgColor: Color? = null
) {
    when (layout.name) {
        "Only Icon" -> IconOnlyNotificationLayout(
            notification, layout, overrideBgColor = overrideBgColor
        )
        "Minimalist" -> MinimalistNotificationLayout(
            notification, layout, overrideBgColor = overrideBgColor
        )
        else -> DefaultNotificationLayout(
            notification, layout, overrideBgColor = overrideBgColor
        )
    }
}

@Composable
fun NotificationImage(
    url: String,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.FillWidth
    )
}

@Composable
fun NotificationVideo(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("NotificationVideo", "ExoPlayer error: ${error.message}", error)
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    val stateName = when (playbackState) {
                        androidx.media3.common.Player.STATE_IDLE -> "IDLE"
                        androidx.media3.common.Player.STATE_BUFFERING -> "BUFFERING"
                        androidx.media3.common.Player.STATE_READY -> "READY"
                        androidx.media3.common.Player.STATE_ENDED -> "ENDED"
                        else -> "UNKNOWN"
                    }
                    android.util.Log.d("NotificationVideo", "Playback state: $stateName")
                }
            })
        }
    }

    DisposableEffect(url) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Use a raw TextureView instead of PlayerView. PlayerView uses SurfaceView
    // by default, which creates its own window surface and renders full-screen
    // black in TYPE_APPLICATION_OVERLAY windows. TextureView composites within
    // the normal view hierarchy.
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : android.graphics.SurfaceTexture.OnFrameAvailableListener,
                    TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: android.graphics.SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        android.util.Log.d("NotificationVideo", "SurfaceTexture available ${width}x${height}, starting playback for $url")
                        exoPlayer.setVideoTextureView(this@apply)
                        val mediaItem = MediaItem.fromUri(url)
                        if (url.startsWith("rtsp://", ignoreCase = true)) {
                            // Force TCP interleaved transport for RTSP — many servers
                            // (go2rtc, mediamtx, etc.) reject UDP with 461.
                            val rtspSource = RtspMediaSource.Factory()
                                .setForceUseRtpTcp(true)
                                .createMediaSource(mediaItem)
                            exoPlayer.setMediaSource(rtspSource)
                        } else {
                            exoPlayer.setMediaItem(mediaItem)
                        }
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: android.graphics.SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {}

                    override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                        exoPlayer.setVideoTextureView(null)
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}

                    override fun onFrameAvailable(surfaceTexture: android.graphics.SurfaceTexture?) {}
                }
            }
        },
        modifier = modifier
            .background(Color.Black)
    )
}
