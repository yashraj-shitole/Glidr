package com.yashraj.glidr

import com.yashraj.glidr.hid.ConnectionState
import com.yashraj.glidr.hid.HidDescriptor
import com.yashraj.glidr.hid.HidEngine
import com.yashraj.glidr.hid.HidReports
import com.yashraj.glidr.hid.HidTransport
import com.yashraj.glidr.hid.KeyboardLayout
import com.yashraj.glidr.hid.PcDevice
import com.yashraj.glidr.input.MotionPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeTransport : HidTransport {
    override val state: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Connected)
    override val host: StateFlow<PcDevice?> = MutableStateFlow(null)
    // Copy-on-write lists: the glide and typing run on other threads while the tests read.
    val mouse = java.util.concurrent.CopyOnWriteArrayList<List<Byte>>()
    val keyboard = java.util.concurrent.CopyOnWriteArrayList<List<Byte>>()

    /** Every report in the order it was sent: "m" for mouse, "k" for keyboard. */
    val log = java.util.concurrent.CopyOnWriteArrayList<String>()
    override fun start() {}
    override fun stop() {}
    override fun retry() {}
    override fun bondedHosts() = emptyList<PcDevice>()
    override fun connect(device: PcDevice) {}
    override fun disconnect() {}
    override fun sendMouse(buf: ByteArray): Boolean { mouse += buf.toList(); log += "m"; return true }
    override fun sendKeyboard(buf: ByteArray): Boolean { keyboard += buf.toList(); log += "k"; return true }
    override fun sendConsumer(buf: ByteArray) = true
}

class HidTest {
    @Test
    fun descriptorDeclaresAllThreeReportIds() {
        val d = HidDescriptor.BYTES.map { it.toInt() and 0xFF }
        for (id in listOf(1, 2, 3)) {
            val found = (0 until d.size - 1).any { d[it] == 0x85 && d[it + 1] == id }
            assertTrue("report id $id", found)
        }
    }

    @Test
    fun mouseReportClampsAndPacks() {
        val b = ByteArray(HidDescriptor.MOUSE_SIZE)
        HidReports.mouse(b, HidReports.BUTTON_LEFT or HidReports.BUTTON_RIGHT, 500, -500, 3, -3)
        assertEquals(listOf<Byte>(3, 127, -127, 3, -3), b.toList())
    }

    @Test
    fun consumerReportIsLittleEndian() {
        val b = ByteArray(HidDescriptor.CONSUMER_SIZE)
        HidReports.consumer(b, 0x00E2)
        assertEquals(listOf<Byte>(0xE2.toByte(), 0), b.toList())
    }

    @Test
    fun usLayoutBasics() {
        val us = KeyboardLayout.US
        assertEquals(0x04, us.strokeFor('a')!!.usage)
        assertEquals(HidReports.MOD_SHIFT, us.strokeFor('A')!!.modifiers)
        assertEquals(0x1F, us.strokeFor('@')!!.usage)
        assertEquals(HidReports.MOD_SHIFT, us.strokeFor('@')!!.modifiers)
        assertNull(us.strokeFor('न')) // Devanagari cannot be typed over plain HID
    }

    @Test
    fun ukAndDeDiffer() {
        assertEquals(0x1F, KeyboardLayout.UK.strokeFor('"')!!.usage)
        assertEquals(HidReports.MOD_SHIFT, KeyboardLayout.UK.strokeFor('£')!!.modifiers)
        assertEquals(0x1D, KeyboardLayout.DE.strokeFor('y')!!.usage) // QWERTZ
        assertEquals(0x1C, KeyboardLayout.DE.strokeFor('z')!!.usage)
        assertEquals(HidReports.MOD_ALTGR, KeyboardLayout.DE.strokeFor('@')!!.modifiers)
        assertNotNull(KeyboardLayout.DE.strokeFor('ä'))
    }

    @Test
    fun slowMovementIsNotLost() {
        val m = MotionPipeline().apply { sensitivity = 1f; acceleration = 0 }
        var total = 0
        repeat(10) { m.process(0.3f, 0f, 8f); total += m.outX }
        assertEquals(3, total)
    }

    @Test
    fun accelerationRaisesGainWithSpeed() {
        val flat = MotionPipeline().apply { sensitivity = 1f; acceleration = 0 }
        val fast = MotionPipeline().apply { sensitivity = 1f; acceleration = 3 }
        flat.process(40f, 0f, 8f)
        fast.process(40f, 0f, 8f)
        assertTrue(fast.outX > flat.outX)
    }

    @Test
    fun engineBatchesMovesAndSplitsLargeReports() {
        val t = FakeTransport()
        val e = HidEngine(t, CoroutineScope(Dispatchers.Unconfined))
        e.motion.sensitivity = 1f
        e.motion.acceleration = 0
        e.move(10f, 0f, 1_000_000L)
        e.move(5f, 0f, 9_000_000L)
        assertTrue(e.flushNow())
        assertEquals(1, t.mouse.size)
        assertEquals(15.toByte(), t.mouse[0][1])
        assertEquals(false, e.flushNow())
        e.move(200f, 0f, 20_000_000L)
        e.flushNow()
        assertEquals(listOf<Byte>(127, 73), t.mouse.drop(1).map { it[1] })
    }

    @Test
    fun naturalScrollFollowsFingers() {
        val t = FakeTransport()
        val e = HidEngine(t, CoroutineScope(Dispatchers.Unconfined))
        e.naturalScroll = true
        e.scrollSpeed = 1f
        e.scrollBy(0f, -60f) // finger moves up
        e.flushNow()
        assertTrue(t.mouse.last()[3] < 0) // wheel down
        e.naturalScroll = false
        e.scrollBy(0f, -60f)
        e.flushNow()
        assertTrue(t.mouse.last()[3] > 0)
    }

    @Test
    fun movementWhileLeftHeldCarriesTheButton() {
        val t = FakeTransport()
        val e = HidEngine(t, CoroutineScope(Dispatchers.Unconfined))
        e.motion.sensitivity = 1f
        e.motion.acceleration = 0
        e.buttonDown(HidReports.BUTTON_LEFT)
        e.move(20f, 5f, 1_000_000L)
        e.flushNow()
        val drag = t.mouse.last()
        assertEquals(HidReports.BUTTON_LEFT.toByte(), drag[0])
        assertEquals(20.toByte(), drag[1])
        e.buttonUp(HidReports.BUTTON_LEFT)
        assertEquals(0.toByte(), t.mouse.last()[0])
    }

    @Test
    fun roamPathSumsToTheTargetAndStaysWithinOneReport() {
        val path = com.yashraj.glidr.input.NudgePath.roam(Math.PI / 3, 600.0, 27)
        assertEquals(27, path.size)
        val sx = path.sumOf { it[0] }
        val sy = path.sumOf { it[1] }
        assertEquals(300, sx)
        assertEquals(520, sy)
        assertTrue(path.all { kotlin.math.abs(it[0]) <= 127 && kotlin.math.abs(it[1]) <= 127 })
        // Eases in and out: the middle step is bigger than the first.
        assertTrue(kotlin.math.abs(path[13][1]) > kotlin.math.abs(path[0][1]))
    }

    @Test
    fun pinchHoldsCtrlWhileTheWheelTurnsThenLetsGo() {
        val t = FakeTransport()
        val e = HidEngine(t, CoroutineScope(Dispatchers.Unconfined))
        assertEquals(2, e.zoomBy(90f)) // fingers apart: two detents, zoom in
        e.zoomEnd()
        assertEquals(HidReports.MOD_CTRL.toByte(), t.keyboard.first()[0])
        assertEquals(0.toByte(), t.keyboard.last()[0])
        assertEquals(2.toByte(), t.mouse.last()[3])
        // Ctrl goes down before the wheel and comes up after it.
        assertEquals(listOf("k", "m", "k"), t.log)
    }

    @Test
    fun pinchInUsesNegativeWheelAndEndWithoutPinchSendsNothing() {
        val t = FakeTransport()
        val e = HidEngine(t, CoroutineScope(Dispatchers.Unconfined))
        e.zoomEnd()
        assertTrue(t.log.isEmpty())
        assertEquals(1, e.zoomBy(-45f))
        e.zoomEnd()
        assertEquals((-1).toByte(), t.mouse.last()[3])
    }

    @Test
    fun cancellingTypingNeverLeavesAKeyHeld() {
        val t = FakeTransport()
        val e = HidEngine(t, CoroutineScope(Dispatchers.Default))
        e.typeText("the quick brown fox jumps over the lazy dog")
        Thread.sleep(60) // a few keys are out, many are still queued
        e.cancelTyping()
        val deadline = System.currentTimeMillis() + 2_000
        while (e.typing.value && System.currentTimeMillis() < deadline) Thread.sleep(5)
        assertEquals(false, e.typing.value)
        val sent = t.keyboard.size
        assertTrue(sent in 2 until 80) // stopped early, not run to the end
        assertEquals(List(8) { 0.toByte() }, t.keyboard.last()) // all keys released
        Thread.sleep(100)
        assertEquals(sent, t.keyboard.size) // and nothing more arrives afterwards
    }

    @Test
    fun flingGlidesInTheFlickDirectionThenStopsWhenCancelled() {
        val t = FakeTransport()
        val e = HidEngine(t, CoroutineScope(Dispatchers.Default))
        e.naturalScroll = true
        e.scrollSpeed = 1f
        e.fling(0f, -2000f) // a fast flick upwards: natural scroll turns the wheel down
        Thread.sleep(350)
        assertTrue("glided for several frames", t.mouse.size >= 3)
        assertTrue("wheel only ever turns one way", t.mouse.all { it[3] <= 0 })
        e.cancelFling()
        Thread.sleep(40)
        val n = t.mouse.size
        Thread.sleep(120)
        assertEquals("nothing is sent after cancel", n, t.mouse.size)
    }

    @Test
    fun slowReleaseAndDisabledSettingDoNotGlide() {
        val slow = FakeTransport()
        HidEngine(slow, CoroutineScope(Dispatchers.Default)).fling(0f, -50f)
        val off = FakeTransport()
        HidEngine(off, CoroutineScope(Dispatchers.Default)).apply { flingEnabled = false }.fling(0f, -3000f)
        Thread.sleep(150)
        assertTrue(slow.mouse.isEmpty())
        assertTrue(off.mouse.isEmpty())
    }

    @Test
    fun touchingThePadStopsTheGlide() {
        val t = FakeTransport()
        val e = HidEngine(t, CoroutineScope(Dispatchers.Default))
        e.fling(0f, -3000f)
        Thread.sleep(120)
        e.touchStart()
        e.touchEnd()
        Thread.sleep(40)
        val n = t.mouse.size
        Thread.sleep(150)
        assertEquals(n, t.mouse.size)
    }
}
