package us.bergnet.oversight.ui.overlay.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import us.bergnet.oversight.data.model.FixedNotification
import us.bergnet.oversight.data.model.enums.FixedNotificationShape
import us.bergnet.oversight.util.ColorParser
import us.bergnet.oversight.util.IconResolver

@Composable
fun FixedNotificationBadge(
    notification: FixedNotification,
    collapsed: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape: Shape = when (notification.shape) {
        FixedNotificationShape.CIRCLE -> CircleShape
        FixedNotificationShape.ROUNDED -> RoundedCornerShape(8.dp)
        FixedNotificationShape.RECTANGULAR -> RectangleShape
        null -> RoundedCornerShape(8.dp)
    }

    val bgColor = ColorParser.parseOrDefault(notification.backgroundColor, Color(0x99000000))
    val borderColor = ColorParser.parse(notification.borderColor)
    val iconColor = ColorParser.parseOrDefault(notification.iconColor, Color.White)
    val textColor = ColorParser.parseOrDefault(notification.messageColor, Color.White)

    val size = notification.size
    val showText = !collapsed && !notification.text.isNullOrBlank()

    val borderMod = if (borderColor != null) {
        Modifier.border(1.dp, borderColor, shape)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .clip(shape)
            .then(borderMod)
            .background(bgColor)
            .padding(horizontal = size.padding.dp, vertical = size.padding.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Icon
        notification.icon?.let { iconName ->
            if (iconName.isNotBlank()) {
                if (IconResolver.isMdiIcon(iconName)) {
                    Text(
                        text = IconResolver.getIconName(iconName),
                        color = iconColor,
                        fontSize = (size.fontSize * 0.8).sp,
                        maxLines = 1
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(iconName)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.size(size.imageSize.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        // Text
        if (showText) {
            Text(
                text = notification.text!!,
                color = textColor,
                fontSize = size.fontSize.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
