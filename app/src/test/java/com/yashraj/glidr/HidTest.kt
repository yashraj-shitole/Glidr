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
    val mouse = mutableListOf<List<Byte>>()
    override fun start() {}
    override fun stop() {}
    override fun bondedHosts() = emptyList<PcDevice>()
    override fun connect(device: PcDevice) {}
    override fun disconnect() {}
    override fun sendMouse(buf: ByteArray): Boolean { mouse += buf.toList(); return true }
    override fun sendKeyboard(buf: ByteArray) = true
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
}
