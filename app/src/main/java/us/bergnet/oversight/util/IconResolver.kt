package us.bergnet.oversight.util

import android.content.Context
import com.mikepenz.iconics.IconicsDrawable

object IconResolver {
    fun isMdiIcon(name: String?): Boolean {
        return name?.startsWith("mdi:") == true
    }

    fun getIconName(name: String): String {
        return name.removePrefix("mdi:")
    }

    /**
     * Returns true if the MDI icon name maps to a real icon in the Iconics font.
     * Uses the same try/catch strategy as MdiIcon composable.
     */
    fun isValidMdiIcon(context: Context, name: String): Boolean {
        val stripped = name.removePrefix("mdi:").replace("-", "_")
        return try {
            IconicsDrawable(context, "cmd-$stripped")
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validates a field that accepts either an MDI icon name or an http(s) URL.
     * Returns null if valid, or an error message string if invalid.
     */
    fun validateIconField(context: Context, fieldName: String, value: String): String? {
        return when {
            value.startsWith("mdi:") -> {
                if (isValidMdiIcon(context, value)) null
                else "Invalid MDI icon name for '$fieldName': $value"
            }
            value.startsWith("http://") || value.startsWith("https://") -> null
            else -> "Invalid icon value for '$fieldName': must start with 'mdi:' or 'http(s)://'"
        }
    }
}
