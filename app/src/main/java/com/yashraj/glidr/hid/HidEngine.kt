package com.yashraj.glidr.hid

import com.yashraj.glidr.input.MotionPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Turns gestures and key presses into HID reports on a [HidTransport]. Mouse movement is batched
 * (~125 Hz) and the hot path reuses its buffers.
 */
class HidEngine(private val transport: HidTransport, private val scope: CoroutineScope) {
    val motion = MotionPipeline()
    private val scroll = MotionPipeline.ScrollAccumulator()
    private val panScroll = MotionPipeline.ScrollAccumulator()

    var naturalScroll = true
    var scrollSpeed = 1.2f
    var layout: KeyboardLayout = KeyboardLayout.US

    private val mouseBuf = ByteArray(HidDescriptor.MOUSE_SIZE)
    private val keyBuf = ByteArray(HidDescriptor.KEYBOARD_SIZE)
    private val consumerBuf = ByteArray(HidDescriptor.CONSUMER_SIZE)

    private val keyLock = Mutex()
    private val consumerLock = Mutex()

    private var buttons = 0
    private var pendingX = 0
    private var pendingY = 0
    private var pendingWheel = 0
    private var pendingPan = 0
    private var flushJob: Job? = null
    private var lastMoveNs = 0L

    /** Start batching while a finger is down. */
    fun touchStart() {
        motion.reset()
        scroll.reset()
        panScroll.reset()
        lastMoveNs = 0L
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            while (isActive) {
                delay(FLUSH_MS)
                flushNow()
            }
        }
    }

    fun touchEnd() {
        flushNow()
        flushJob?.cancel()
        flushJob = null
    }

    fun move(dxPx: Float, dyPx: Float, nowNs: Long) {
        val dtMs = if (lastMoveNs == 0L) FLUSH_MS.toFloat() else (nowNs - lastMoveNs) / 1_000_000f
        lastMoveNs = nowNs
        motion.process(dxPx, dyPx, dtMs)
        pendingX += motion.outX
        pendingY += motion.outY
    }

    val connected: Boolean get() = transport.state.value == ConnectionState.Connected

    /** Returns the number of whole scroll detents produced, so the UI can tick. */
    fun scrollBy(dxPx: Float, dyPx: Float): Int {
        // Natural: content follows the fingers (finger up -> wheel down). HID wheel > 0 scrolls up, pan > 0 right.
        val sign = if (naturalScroll) 1 else -1
        val px = PX_PER_DETENT / scrollSpeed
        val v = scroll.add(dyPx, px)
        val h = panScroll.add(dxPx, px)
        pendingWheel += sign * v
        pendingPan -= sign * h
        return kotlin.math.abs(v) + kotlin.math.abs(h)
    }

    /** Sends whatever has accumulated; returns true if a report went out. */
    fun flushNow(): Boolean {
        if (pendingX == 0 && pendingY == 0 && pendingWheel == 0 && pendingPan == 0) return false
        // A report carries at most +-127 per axis; send the remainder in follow-up reports.
        while (pendingX != 0 || pendingY != 0 || pendingWheel != 0 || pendingPan != 0) {
            val x = pendingX.coerceIn(-127, 127)
            val y = pendingY.coerceIn(-127, 127)
            val w = pendingWheel.coerceIn(-127, 127)
            val p = pendingPan.coerceIn(-127, 127)
            HidReports.mouse(mouseBuf, buttons, x, y, w, p)
            transport.sendMouse(mouseBuf)
            pendingX -= x
            pendingY -= y
            pendingWheel -= w
            pendingPan -= p
        }
        return true
    }

    fun buttonDown(mask: Int) {
        flushNow()
        buttons = buttons or mask
        sendButtons()
    }

    fun buttonUp(mask: Int) {
        flushNow()
        buttons = buttons and mask.inv()
        sendButtons()
    }

    fun click(mask: Int) {
        buttonDown(mask)
        scope.launch {
            delay(CLICK_MS)
            buttonUp(mask)
        }
    }

    private fun sendButtons() {
        HidReports.mouse(mouseBuf, buttons, 0, 0)
        transport.sendMouse(mouseBuf)
    }

    /** A tiny relative move and back, used by keep awake. */
    fun nudge() {
        HidReports.mouse(mouseBuf, buttons, NUDGE_PX, 0)
        transport.sendMouse(mouseBuf)
        scope.launch {
            delay(NUDGE_BACK_MS)
            HidReports.mouse(mouseBuf, buttons, -NUDGE_PX, 0)
            transport.sendMouse(mouseBuf)
        }
    }

    fun pressKey(usage: Int, modifiers: Int = 0) {
        scope.launch {
            keyLock.withLock {
                HidReports.keyboard(keyBuf, modifiers, usage)
                transport.sendKeyboard(keyBuf)
                delay(KEY_MS)
                HidReports.keyboard(keyBuf, 0)
                transport.sendKeyboard(keyBuf)
                delay(KEY_MS)
            }
        }
    }

    /** Types [text] through the current layout. Characters the layout cannot produce are skipped. */
    fun typeText(text: String) {
        scope.launch {
            keyLock.withLock {
                for (c in text) {
                    val stroke = layout.strokeFor(c) ?: continue
                    HidReports.keyboard(keyBuf, stroke.modifiers, stroke.usage)
                    transport.sendKeyboard(keyBuf)
                    delay(KEY_MS)
                    HidReports.keyboard(keyBuf, 0)
                    transport.sendKeyboard(keyBuf)
                    delay(KEY_MS)
                }
            }
        }
    }

    /** Characters in [text] that [layout] cannot type. */
    fun layoutUnsupported(text: String): String = text.filter { layout.strokeFor(it) == null }

    /** Presses and releases a media key. Calls queue up, so several volume steps in a row all register. */
    fun consumer(usage: Int) {
        scope.launch {
            consumerLock.withLock {
                HidReports.consumer(consumerBuf, usage)
                transport.sendConsumer(consumerBuf)
                delay(KEY_MS)
                HidReports.consumer(consumerBuf, 0)
                transport.sendConsumer(consumerBuf)
                delay(KEY_MS)
            }
        }
    }

    private companion object {
        const val FLUSH_MS = 8L // ~125 Hz
        const val CLICK_MS = 40L
        const val KEY_MS = 15L
        const val NUDGE_PX = 3
        const val NUDGE_BACK_MS = 30L
        const val PX_PER_DETENT = 28f
    }
}
