package us.bergnet.oversight.ui.overlay

import android.text.format.DateFormat
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import us.bergnet.oversight.data.model.NonNullableOverlayCustomization
import us.bergnet.oversight.util.ColorParser
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ClockOverlay(
    customization: NonNullableOverlayCustomization,
    clockTextFormat: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val systemDefault = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    var timeText by remember { mutableStateOf("") }

    LaunchedEffect(clockTextFormat, systemDefault) {
        while (true) {
            val format = clockTextFormat ?: systemDefault
            timeText = try {
                SimpleDateFormat(format, Locale.getDefault()).format(Date())
            } catch (_: Exception) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            }
            delay(1000L)
        }
    }

    val textColor = ColorParser.parseOrDefault(customization.color, Color.White)
    val shadow = if (customization.displayShadow) {
        Shadow(
            color = Color.Black,
            blurRadius = 4f
        )
    } else null

    Text(
        text = timeText,
        color = textColor,
        fontSize = customization.fontSize.sp,
        fontWeight = customization.fontWeight.toFontWeight(),
        style = TextStyle(shadow = shadow),
        modifier = modifier.padding(8.dp)
    )
}
