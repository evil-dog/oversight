package us.bergnet.oversight.util

/**
 * Resolves MDI icon names (e.g., "mdi:home") to display characters.
 * For now, provides a basic mapping. A full implementation would use
 * the community-material-typeface library.
 */
object IconResolver {
    fun isMdiIcon(name: String?): Boolean {
        return name?.startsWith("mdi:") == true
    }

    fun getIconName(name: String): String {
        return name.removePrefix("mdi:")
    }
}
