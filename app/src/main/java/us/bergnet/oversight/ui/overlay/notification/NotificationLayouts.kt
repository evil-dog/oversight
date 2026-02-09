package us.bergnet.oversight.ui.overlay.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import us.bergnet.oversight.data.model.NotificationLayout
import us.bergnet.oversight.data.model.ReceivedNotification
import us.bergnet.oversight.ui.components.MdiIcon
import us.bergnet.oversight.util.ColorParser
import us.bergnet.oversight.util.IconResolver

@Composable
fun DefaultNotificationLayout(
    notification: ReceivedNotification,
    layout: NotificationLayout,
    modifier: Modifier = Modifier,
    overrideBgColor: Color? = null
) {
    val bgColor = overrideBgColor
        ?: ColorParser.parseOrDefault(layout.backgroundColor, Color.Black.copy(alpha = 0.4f))

    Row(
        modifier = modifier
            .widthIn(max = layout.maxWidth.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Icon
        if (layout.iconDisplay) {
            val iconUrl = notification.getDisplayIcon()
            if (!iconUrl.isNullOrBlank()) {
                if (IconResolver.isMdiIcon(iconUrl)) {
                    MdiIcon(
                        name = iconUrl,
                        tint = Color.White,
                        size = layout.iconSize.dp
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(iconUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(layout.iconSize.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        // Text content
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Source
            if (layout.sourceDisplay) {
                notification.source?.let { source ->
                    val fmt = layout.sourceFormat
                    Text(
                        text = source,
                        color = ColorParser.parseOrDefault(fmt.color, Color.White),
                        fontSize = (fmt.fontSize ?: 9).sp,
                        fontWeight = fmt.fontWeight?.toFontWeight(),
                        maxLines = fmt.maxLines.coerceAtLeast(1),
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Title
            if (layout.titleDisplay) {
                val displayTitle = notification.getDisplayTitle()
                if (displayTitle.isNotBlank()) {
                    val fmt = layout.titleFormat
                    Text(
                        text = displayTitle,
                        color = ColorParser.parseOrDefault(fmt.color, Color.White),
                        fontSize = (fmt.fontSize ?: 12).sp,
                        fontWeight = fmt.fontWeight?.toFontWeight() ?: FontWeight.Bold,
                        maxLines = fmt.maxLines.coerceAtLeast(1),
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Message
            if (layout.messageDisplay) {
                notification.getDisplayMessage()?.let { message ->
                    val fmt = layout.messageFormat
                    Text(
                        text = message,
                        color = ColorParser.parseOrDefault(fmt.color, Color.White),
                        fontSize = (fmt.fontSize ?: 11).sp,
                        fontWeight = fmt.fontWeight?.toFontWeight(),
                        maxLines = fmt.maxLines.coerceAtLeast(1),
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Secondary icon
        if (layout.iconSecondaryDisplay) {
            notification.smallIcon?.let { iconUrl ->
                if (!IconResolver.isMdiIcon(iconUrl)) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(iconUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(layout.iconSecondarySize.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

@Composable
fun MinimalistNotificationLayout(
    notification: ReceivedNotification,
    layout: NotificationLayout,
    modifier: Modifier = Modifier,
    overrideBgColor: Color? = null
) {
    DefaultNotificationLayout(notification, layout, modifier, overrideBgColor)
}

@Composable
fun IconOnlyNotificationLayout(
    notification: ReceivedNotification,
    layout: NotificationLayout,
    modifier: Modifier = Modifier,
    overrideBgColor: Color? = null
) {
    val bgColor = overrideBgColor
        ?: ColorParser.parseOrDefault(layout.backgroundColor, Color.Black.copy(alpha = 0.4f))
    val iconUrl = notification.getDisplayIcon()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!iconUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(iconUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(layout.iconSize.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Fit
            )
        }
    }
}
