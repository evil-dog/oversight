package us.bergnet.oversight.util

import androidx.compose.ui.graphics.Color

object ColorParser {
    /**
     * Parses a hex color string to a Compose Color.
     * Supports: "#RGB", "#RRGGBB", "#AARRGGBB", "RGB", "RRGGBB", "AARRGGBB"
     */
    fun parse(hex: String?): Color? {
        if (hex.isNullOrBlank()) return null
        val clean = hex.trimStart('#')
        return try {
            when (clean.length) {
                3 -> {
                    val r = clean[0].digitToInt(16) * 17
                    val g = clean[1].digitToInt(16) * 17
                    val b = clean[2].digitToInt(16) * 17
                    Color(r, g, b)
                }
                6 -> {
                    val value = clean.toLong(16)
                    Color(
                        red = ((value shr 16) and 0xFF).toInt(),
                        green = ((value shr 8) and 0xFF).toInt(),
                        blue = (value and 0xFF).toInt()
                    )
                }
                8 -> {
                    val value = clean.toLong(16)
                    Color(
                        alpha = ((value shr 24) and 0xFF).toInt(),
                        red = ((value shr 16) and 0xFF).toInt(),
                        green = ((value shr 8) and 0xFF).toInt(),
                        blue = (value and 0xFF).toInt()
                    )
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses with a fallback color if parsing fails.
     */
    fun parseOrDefault(hex: String?, default: Color): Color = parse(hex) ?: default
}
