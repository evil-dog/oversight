package us.bergnet.oversight.ui.overlay.notification

import android.view.TextureView
import androidx.compose.animation.*
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

    LaunchedEffect(notification?.id) {
        if (notification != null) {
            visible = true
            val duration = notification.duration ?: durationSeconds
            delay(duration * 1000L)
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

            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .widthIn(max = layout.maxWidth.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor),
            ) {
                // Row 1: Layout content (icon + text)
                NotificationLayoutContent(
                    notification, layout,
                    overrideBgColor = Color.Transparent
                )

                // Row 2: Image or video, full width below the text
                if (hasImage) {
                    NotificationImage(
                        url = notification.image!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    )
                }
                if (hasVideo) {
                    NotificationVideo(
                        url = notification.video!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
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
        modifier = modifier
            .clip(RoundedCornerShape(8.dp)),
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
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
    )
}
