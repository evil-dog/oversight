package us.bergnet.oversight.util

object ExpirationParser {
    private val DURATION_REGEX = Regex(
        """(?:(\d+)y)?(?:(\d+)w)?(?:(\d+)d)?(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?"""
    )

    /**
     * Parses an expiration string into an epoch timestamp in milliseconds.
     *
     * Supports:
     * - Epoch timestamp (13+ digit number) -> used directly
     * - Epoch timestamp in seconds (10 digit number) -> converted to millis
     * - Relative duration string (e.g., "1y2w3d4h5m6s") -> added to receivedTime
     * - Raw seconds (plain number) -> added to receivedTime
     */
    fun parse(expiration: String, receivedTime: Long): Long? {
        val trimmed = expiration.trim()
        if (trimmed.isEmpty()) return null

        // Try as a plain number first
        trimmed.toLongOrNull()?.let { num ->
            return when {
                // Looks like epoch millis (13+ digits, after year 2001)
                num > 1_000_000_000_000L -> num
                // Looks like epoch seconds (10 digits, after year 2001)
                num > 1_000_000_000L -> num * 1000L
                // Otherwise treat as relative seconds
                else -> receivedTime + (num * 1000L)
            }
        }

        // Try as a duration string (e.g., "1d2h3m")
        val match = DURATION_REGEX.matchEntire(trimmed)
        if (match != null) {
            val years = match.groupValues[1].toLongOrNull() ?: 0
            val weeks = match.groupValues[2].toLongOrNull() ?: 0
            val days = match.groupValues[3].toLongOrNull() ?: 0
            val hours = match.groupValues[4].toLongOrNull() ?: 0
            val minutes = match.groupValues[5].toLongOrNull() ?: 0
            val seconds = match.groupValues[6].toLongOrNull() ?: 0

            val totalSeconds = years * 365 * 24 * 3600 +
                    weeks * 7 * 24 * 3600 +
                    days * 24 * 3600 +
                    hours * 3600 +
                    minutes * 60 +
                    seconds

            if (totalSeconds > 0) {
                return receivedTime + (totalSeconds * 1000L)
            }
        }

        return null
    }
}
