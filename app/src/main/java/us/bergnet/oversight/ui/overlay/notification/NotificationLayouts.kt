package us.bergnet.oversight.ui.overlay.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
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

/** Removes the built-in font padding (ascender/descender gap) that adds extra vertical whitespace. */
private val tightTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both
    )
)

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
            .clip(RoundedCornerShape(9.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Primary icon with optional small icon badge overlay
        if (layout.iconDisplay) {
            val primaryIcon = notification.getDisplayIcon()
            val smallIconName = notification.smallIcon?.takeIf {
                layout.iconSecondaryDisplay && IconResolver.isMdiIcon(it)
            }
            val smallIconTint = ColorParser.parseOrDefault(notification.smallIconColor, Color.White)

            if (primaryIcon != null) {
                Box(modifier = Modifier.size(layout.iconSize.dp)) {
                    if (IconResolver.isMdiIcon(primaryIcon)) {
                        MdiIcon(name = primaryIcon, tint = Color.White, size = layout.iconSize.dp)
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(primaryIcon).crossfade(true).build(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(layout.iconSize.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                    if (smallIconName != null) {
                        SmallIconBadge(
                            name = smallIconName,
                            tint = smallIconTint,
                            size = layout.iconSecondarySize,
                            modifier = Modifier.align(Alignment.TopEnd)
                        )
                    }
                }
            } else if (smallIconName != null) {
                SmallIconBadge(
                    name = smallIconName,
                    tint = smallIconTint,
                    size = layout.iconSecondarySize
                )
            }
        }

        // Text content
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
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
                        overflow = TextOverflow.Ellipsis,
                        style = tightTextStyle,
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
                        overflow = TextOverflow.Ellipsis,
                        style = tightTextStyle,
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
                        overflow = TextOverflow.Ellipsis,
                        style = tightTextStyle,
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
    val primaryIcon = notification.getDisplayIcon()
    val smallIconName = notification.smallIcon?.takeIf { IconResolver.isMdiIcon(it) }
    val smallIconTint = ColorParser.parseOrDefault(notification.smallIconColor, Color.White)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (primaryIcon != null) {
            Box(modifier = Modifier.size(layout.iconSize.dp)) {
                if (IconResolver.isMdiIcon(primaryIcon)) {
                    MdiIcon(name = primaryIcon, tint = Color.White, size = layout.iconSize.dp)
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(primaryIcon).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(layout.iconSize.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
                if (smallIconName != null) {
                    SmallIconBadge(
                        name = smallIconName,
                        tint = smallIconTint,
                        size = layout.iconSecondarySize,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
            }
        } else if (smallIconName != null) {
            SmallIconBadge(name = smallIconName, tint = smallIconTint, size = layout.iconSecondarySize)
        }
    }
}

/**
 * Small MDI icon rendered inside a dark semi-transparent circle.
 * Used standalone when no large icon is present, or as an overlay badge
 * on the bottom-right corner of the large icon.
 */
@Composable
private fun SmallIconBadge(
    name: String,
    tint: Color,
    size: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(0x99000000)),
        contentAlignment = Alignment.Center
    ) {
        MdiIcon(name = name, tint = tint, size = (size * 0.65f).dp)
    }
}
