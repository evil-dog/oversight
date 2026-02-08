package us.bergnet.oversight.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizePx
import com.mikepenz.iconics.utils.colorInt

@Composable
fun MdiIcon(
    name: String,
    tint: Color = Color.White,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val pxSize = with(density) { size.toPx() }.toInt()
    val argbColor = tint.toArgb()

    val drawable = remember(name, argbColor, pxSize) {
        val stripped = name.removePrefix("mdi:").replace("-", "_")
        val iconId = "cmd-$stripped"
        try {
            IconicsDrawable(context, iconId).apply {
                sizePx = pxSize
                colorInt = argbColor
            }
        } catch (e: Exception) {
            null
        }
    }

    if (drawable != null) {
        Canvas(modifier = modifier.size(size)) {
            drawIntoCanvas { canvas ->
                drawable.setBounds(0, 0, pxSize, pxSize)
                drawable.draw(canvas.nativeCanvas)
            }
        }
    }
}
