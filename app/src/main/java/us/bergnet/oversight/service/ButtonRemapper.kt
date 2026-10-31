package us.bergnet.oversight.service

import us.bergnet.oversight.data.model.ButtonMapping

/**
 * Decides, per key event, whether a remote button is remapped and when its action runs. Free of
 * Android types so the press timing can be unit tested; [ButtonRemapService] supplies the real
 * scheduler and actions. All calls must come from one thread (the main thread on device).
 *
 * Keys can't be re-injected, so once a DOWN is consumed the rest of that press (repeats and UP) is
 * consumed too, and a long-press-only mapping can only restore the short press via [Actions.emulateDefault].
 */
class ButtonRemapper(
    private val mappings: () -> List<ButtonMapping>,
    /** False while state is loading or the setup screen is open; keys then pass through untouched. */
    private val isActive: () -> Boolean,
    /** Takes the pending capture listener, if any, clearing it so it fires once. */
    private val takeCaptureListener: () -> ((Int) -> Unit)?,
    private val scheduler: Scheduler,
    private val longPressTimeoutMs: Long,
    private val actions: Actions,
) {
    fun interface Cancellable { fun cancel() }
    fun interface Scheduler { fun schedule(delayMs: Long, block: () -> Unit): Cancellable }

    interface Actions {
        fun execute(mapping: ButtonMapping)
        /** Performs the button's normal short press; only called when [ButtonMapping.canReproduceDefault]. */
        fun emulateDefault(keyCode: Int)
    }

    /** A press whose DOWN was consumed. Both mappings null means swallow it (a captured key). */
    private class Press(val short: ButtonMapping?, val long: ButtonMapping?) {
        var timer: Cancellable? = null
        var longFired = false
    }

    private val pressed = mutableMapOf<Int, Press>()

    /** Returns true if the event is consumed. */
    fun onKey(keyCode: Int, isDown: Boolean, repeatCount: Int): Boolean = when {
        !isDown -> onUp(keyCode)
        repeatCount == 0 -> onDown(keyCode)
        else -> onRepeat(keyCode)
    }

    private fun onDown(keyCode: Int): Boolean {
        // A DOWN without an UP (e.g. focus loss) leaves a stale press; drop it
        pressed.remove(keyCode)?.timer?.cancel()

        takeCaptureListener()?.let { listener ->
            pressed[keyCode] = Press(null, null)
            listener(keyCode)
            return true
        }

        if (!isActive()) return false
        val current = mappings()
        val short = current.firstOrNull { it.matches(keyCode, longPress = false) }
        val long = current.firstOrNull { it.matches(keyCode, longPress = true) }
        if (short == null && long == null) return false

        val press = Press(short, long)
        pressed[keyCode] = press
        if (long == null) {
            actions.execute(short!!)
        } else {
            press.timer = scheduler.schedule(longPressTimeoutMs) {
                press.timer = null
                press.longFired = true
                actions.execute(long)
            }
        }
        return true
    }

    private fun onRepeat(keyCode: Int): Boolean {
        val press = pressed[keyCode] ?: return false
        if (press.long == null && press.short?.action?.repeats == true) actions.execute(press.short)
        return true
    }

    private fun onUp(keyCode: Int): Boolean {
        val press = pressed.remove(keyCode) ?: return false
        if (press.long != null && !press.longFired) {
            press.timer?.cancel()
            when {
                press.short != null -> actions.execute(press.short)
                ButtonMapping.canReproduceDefault(keyCode) -> actions.emulateDefault(keyCode)
            }
        }
        return true
    }

    /** Cancels pending long-press timers, e.g. when the service is torn down. */
    fun reset() {
        pressed.values.forEach { it.timer?.cancel() }
        pressed.clear()
    }
}
