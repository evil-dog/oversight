package us.bergnet.oversight.util

import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial

object IconResolver {
    fun isMdiIcon(name: String?): Boolean {
        return name?.startsWith("mdi:") == true
    }

    fun getIconName(name: String): String {
        return name.removePrefix("mdi:")
    }

    /**
     * Returns true if the MDI icon name maps to a real icon in the Community Material font.
     * CommunityMaterial.getIcon() iterates all Icon enums (Icon, Icon2, Icon3) and throws
     * IllegalArgumentException if the icon name is not found.
     */
    fun isValidMdiIcon(name: String): Boolean {
        val stripped = name.removePrefix("mdi:").replace("-", "_")
        return try {
            CommunityMaterial.getIcon("cmd_$stripped")
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validates a field that accepts either an MDI icon name or an http(s) URL.
     * Returns null if valid, or an error message string if invalid.
     */
    fun validateIconField(fieldName: String, value: String): String? {
        return when {
            value.startsWith("mdi:") -> {
                if (isValidMdiIcon(value)) null
                else "Invalid MDI icon name for '$fieldName': $value"
            }
            value.startsWith("http://") || value.startsWith("https://") -> null
            else -> "Invalid icon value for '$fieldName': must start with 'mdi:' or 'http(s)://'"
        }
    }
}
