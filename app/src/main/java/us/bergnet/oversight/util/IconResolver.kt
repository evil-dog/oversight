package us.bergnet.oversight.util

import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import java.util.concurrent.ConcurrentHashMap

object IconResolver {
    // CommunityMaterial.getIcon iterates the Icon, Icon2, Icon3 enums and throws on miss; both the
    // iteration and the exception are expensive, and the same names recur per /notify request.
    private val validationCache = ConcurrentHashMap<String, Boolean>()

    fun isMdiIcon(name: String?): Boolean {
        return name?.startsWith("mdi:") == true
    }

    fun getIconName(name: String): String {
        return name.removePrefix("mdi:")
    }

    fun isValidMdiIcon(name: String): Boolean {
        return validationCache.getOrPut(name) {
            val stripped = name.removePrefix("mdi:").replace("-", "_")
            try {
                CommunityMaterial.getIcon("cmd_$stripped")
                true
            } catch (e: Exception) {
                false
            }
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
