package us.bergnet.oversight.service

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import us.bergnet.oversight.data.model.ButtonMapping
import us.bergnet.oversight.data.model.RemapAction

class ButtonRemapperTest {

    private val timeout = 500L

    /** Manually advanced stand-in for the main-thread Handler. */
    private class FakeScheduler : ButtonRemapper.Scheduler {
        private class Task(val at: Long, val block: () -> Unit) { var cancelled = false }
        private val tasks = mutableListOf<Task>()
        var now = 0L

        override fun schedule(delayMs: Long, block: () -> Unit): ButtonRemapper.Cancellable {
            val task = Task(now + delayMs, block)
            tasks += task
            return ButtonRemapper.Cancellable { task.cancelled = true }
        }

        fun advance(ms: Long) {
            now += ms
            tasks.filter { !it.cancelled && it.at <= now }.forEach { tasks -= it; it.block() }
        }
    }

    private val scheduler = FakeScheduler()
    private var mappings = emptyList<ButtonMapping>()
    private var active = true
    private var capture: ((Int) -> Unit)? = null
    private val executed = mutableListOf<ButtonMapping>()
    private val emulated = mutableListOf<Int>()

    private val remapper = ButtonRemapper(
        mappings = { mappings },
        isActive = { active },
        takeCaptureListener = { capture.also { capture = null } },
        scheduler = scheduler,
        longPressTimeoutMs = timeout,
        actions = object : ButtonRemapper.Actions {
            override fun execute(mapping: ButtonMapping) { executed += mapping }
            override fun emulateDefault(keyCode: Int) { emulated += keyCode }
        },
    )

    private fun down(key: Int, repeat: Int = 0) = remapper.onKey(key, isDown = true, repeatCount = repeat)
    private fun up(key: Int) = remapper.onKey(key, isDown = false, repeatCount = 0)

    private val netflix = KeyEvent.KEYCODE_BUTTON_1
    private val back = KeyEvent.KEYCODE_BACK

    @Test
    fun unmappedKeysPassThrough() {
        mappings = listOf(ButtonMapping(netflix, false, RemapAction.HOME))
        assertFalse(down(back))
        assertFalse(up(back))
        assertTrue(executed.isEmpty())
    }

    @Test
    fun shortMappingFiresOnceOnDown() {
        val mapping = ButtonMapping(netflix, false, RemapAction.LAUNCH_APP, "com.plexapp.android")
        mappings = listOf(mapping)
        assertTrue(down(netflix))
        assertEquals(listOf(mapping), executed)
        assertTrue(down(netflix, repeat = 1))
        assertTrue(up(netflix))
        assertEquals(listOf(mapping), executed)
    }

    @Test
    fun volumeActionRepeatsWhileHeld() {
        val mapping = ButtonMapping(netflix, false, RemapAction.VOLUME_UP)
        mappings = listOf(mapping)
        down(netflix)
        down(netflix, repeat = 1)
        down(netflix, repeat = 2)
        up(netflix)
        assertEquals(3, executed.size)
    }

    @Test
    fun longPressFiresAfterTimeoutAndOnlyOnce() {
        val mapping = ButtonMapping(back, true, RemapAction.HOME)
        mappings = listOf(mapping)
        assertTrue(down(back))
        scheduler.advance(timeout - 1)
        assertTrue(executed.isEmpty())
        scheduler.advance(1)
        assertEquals(listOf(mapping), executed)
        assertTrue(down(back, repeat = 1))
        assertTrue(up(back))
        assertEquals(listOf(mapping), executed)
        assertTrue(emulated.isEmpty())
    }

    @Test
    fun quickReleaseOfLongOnlyReproducibleKeyEmulatesDefault() {
        mappings = listOf(ButtonMapping(back, true, RemapAction.HOME))
        down(back)
        scheduler.advance(100)
        assertTrue(up(back))
        scheduler.advance(timeout)
        assertTrue(executed.isEmpty())
        assertEquals(listOf(back), emulated)
    }

    @Test
    fun quickReleaseOfLongOnlyOtherKeyDoesNothing() {
        mappings = listOf(ButtonMapping(netflix, true, RemapAction.HOME))
        down(netflix)
        assertTrue(up(netflix))
        scheduler.advance(timeout)
        assertTrue(executed.isEmpty())
        assertTrue(emulated.isEmpty())
    }

    @Test
    fun shortAndLongOnSameKey() {
        val short = ButtonMapping(netflix, false, RemapAction.PLAY_PAUSE)
        val long = ButtonMapping(netflix, true, RemapAction.LAUNCH_APP, "com.netflix.ninja")
        mappings = listOf(short, long)

        down(netflix)
        assertTrue(executed.isEmpty()) // short waits to rule out a long press
        up(netflix)
        assertEquals(listOf(short), executed)

        executed.clear()
        down(netflix)
        scheduler.advance(timeout)
        up(netflix)
        assertEquals(listOf(long), executed)
    }

    @Test
    fun captureTakesNextKeyAndSwallowsWholePress() {
        mappings = listOf(ButtonMapping(back, false, RemapAction.BLOCK))
        val captured = mutableListOf<Int>()
        capture = { captured += it }
        active = false // capture happens with the setup screen open

        assertTrue(down(back))
        assertTrue(down(back, repeat = 1))
        assertTrue(up(back))
        assertEquals(listOf(back), captured)
        assertTrue(executed.isEmpty())

        // Listener fired once; the next press is handled normally (inactive, so passed through)
        assertFalse(down(back))
    }

    @Test
    fun inactivePassesEverythingThrough() {
        mappings = listOf(ButtonMapping(back, false, RemapAction.BLOCK))
        active = false
        assertFalse(down(back))
        assertFalse(up(back))
    }

    @Test
    fun pressStartedWhileMappedIsFullyConsumedEvenIfMappingRemoved() {
        mappings = listOf(ButtonMapping(back, false, RemapAction.BLOCK))
        down(back)
        mappings = emptyList()
        assertTrue(up(back))
    }
}
