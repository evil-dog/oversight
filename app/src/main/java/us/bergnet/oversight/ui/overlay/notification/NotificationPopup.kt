package us.bergnet.oversight.ui.overlay.notification

import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Image
                if (layout.imageDisplay && !notification.image.isNullOrBlank()) {
                    NotificationImage(
                        url = notification.image,
                        small = layout.imageSmall
                    )
                }

                // Video
                if (!notification.video.isNullOrBlank()) {
                    NotificationVideo(
                        url = notification.video,
                        modifier = Modifier
                            .widthIn(max = layout.maxWidth.dp)
                            .heightIn(max = 200.dp)
                    )
                }

                // Layout content
                when (layout.name) {
                    "Only Icon" -> IconOnlyNotificationLayout(notification, layout)
                    "Minimalist" -> MinimalistNotificationLayout(notification, layout)
                    else -> DefaultNotificationLayout(notification, layout)
                }
            }
        }
    }
}

@Composable
fun NotificationImage(
    url: String,
    small: Boolean,
    modifier: Modifier = Modifier
) {
    val size = if (small) 80.dp else 160.dp

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = null,
        modifier = modifier
            .widthIn(max = size)
            .heightIn(max = size)
            .clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Fit
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun NotificationVideo(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(url)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(url) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(it).apply {
                player = exoPlayer
                useController = false
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
    )
}
