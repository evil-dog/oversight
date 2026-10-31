package us.bergnet.oversight.data.model

import android.view.KeyEvent
import kotlinx.serialization.Serializable

/** What a remapped remote button does. Limited to what an accessibility service can perform; it can't inject key codes. */
@Serializable
enum class RemapAction(val label: String) {
    BACK("Back"),
    HOME("Home"),
    RECENTS("Recent Apps"),
    NOTIFICATIONS("Notifications"),
    PLAY_PAUSE("Play / Pause"),
    NEXT("Next Track"),
    PREVIOUS("Previous Track"),
    VOLUME_UP("Volume Up"),
    VOLUME_DOWN("Volume Down"),
    MUTE("Mute"),
    LAUNCH_APP("Launch App"),
    BLOCK("Do Nothing");

    /** Actions that keep firing while the button is held. */
    val repeats: Boolean get() = this == VOLUME_UP || this == VOLUME_DOWN
}

/** One remote button remapping; at most one per ([keyCode], [longPress]). */
@Serializable
data class ButtonMapping(
    val keyCode: Int,
    val longPress: Boolean,
    val action: RemapAction,
    /** Target of [RemapAction.LAUNCH_APP]. */
    val packageName: String? = null,
    /** Display name of [packageName], captured when the mapping is made. */
    val appLabel: String? = null,
) {
    fun matches(keyCode: Int, longPress: Boolean) = this.keyCode == keyCode && this.longPress == longPress

    fun actionLabel(): String =
        if (action == RemapAction.LAUNCH_APP) "Launch ${appLabel ?: packageName ?: "app"}" else action.label

    companion object {
        private val MEDIA_KEYS = setOf(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_STOP,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND,
        )
        private val VOLUME_KEYS = setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE)

        fun isMediaKey(keyCode: Int) = keyCode in MEDIA_KEYS
        fun isVolumeKey(keyCode: Int) = keyCode in VOLUME_KEYS

        /**
         * Whether the button's normal short press can be reproduced after it was consumed to detect a long press.
         * Other keys (D-pad, OK, vendor app buttons) can't be re-injected, so their short press is lost.
         */
        fun canReproduceDefault(keyCode: Int) =
            keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME || isMediaKey(keyCode) || isVolumeKey(keyCode)

        private val KEY_NAMES = mapOf(
            KeyEvent.KEYCODE_BACK to "Back",
            KeyEvent.KEYCODE_HOME to "Home",
            KeyEvent.KEYCODE_MENU to "Menu",
            KeyEvent.KEYCODE_SEARCH to "Search",
            KeyEvent.KEYCODE_ASSIST to "Assistant",
            KeyEvent.KEYCODE_VOICE_ASSIST to "Voice Assistant",
            KeyEvent.KEYCODE_DPAD_UP to "D-pad Up",
            KeyEvent.KEYCODE_DPAD_DOWN to "D-pad Down",
            KeyEvent.KEYCODE_DPAD_LEFT to "D-pad Left",
            KeyEvent.KEYCODE_DPAD_RIGHT to "D-pad Right",
            KeyEvent.KEYCODE_DPAD_CENTER to "OK",
            KeyEvent.KEYCODE_ENTER to "Enter",
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "Play / Pause",
            KeyEvent.KEYCODE_MEDIA_PLAY to "Play",
            KeyEvent.KEYCODE_MEDIA_PAUSE to "Pause",
            KeyEvent.KEYCODE_MEDIA_NEXT to "Next",
            KeyEvent.KEYCODE_MEDIA_PREVIOUS to "Previous",
            KeyEvent.KEYCODE_MEDIA_STOP to "Stop",
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD to "Fast Forward",
            KeyEvent.KEYCODE_MEDIA_REWIND to "Rewind",
            KeyEvent.KEYCODE_VOLUME_UP to "Volume Up",
            KeyEvent.KEYCODE_VOLUME_DOWN to "Volume Down",
            KeyEvent.KEYCODE_VOLUME_MUTE to "Mute",
            KeyEvent.KEYCODE_GUIDE to "Guide",
            KeyEvent.KEYCODE_TV_INPUT to "Input",
            KeyEvent.KEYCODE_SETTINGS to "Settings",
            KeyEvent.KEYCODE_BOOKMARK to "Bookmark",
            KeyEvent.KEYCODE_ALL_APPS to "All Apps",
            KeyEvent.KEYCODE_BUTTON_A to "Button A",
            KeyEvent.KEYCODE_BUTTON_B to "Button B",
            KeyEvent.KEYCODE_BUTTON_X to "Button X",
            KeyEvent.KEYCODE_BUTTON_Y to "Button Y",
        )

        /** Friendly name for a remote key, e.g. "Back", or "BUTTON 13" / "Key 300" for unusual vendor keys. */
        fun keyName(keyCode: Int): String = KEY_NAMES[keyCode]
            ?: KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
                .let { if (it.all(Char::isDigit)) "Key $it" else it.replace('_', ' ') }
    }
}
