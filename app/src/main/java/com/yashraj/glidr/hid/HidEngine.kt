package com.yashraj.glidr.hid

import com.yashraj.glidr.input.MotionPipeline
import com.yashraj.glidr.input.NudgePath
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Turns gestures and key presses into HID reports on a [HidTransport]. Mouse movement goes out from a dedicated
 * 250 Hz sender thread, smoothed so uneven touch input still gives an even cursor, and reuses its buffers.
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

    private val nudgeBuf = ByteArray(HidDescriptor.MOUSE_SIZE)
    private val nudgeLock = Mutex()
    private val keyLock = Mutex()
    private val consumerLock = Mutex()

    /** Guards everything the 250 Hz sender thread and the UI thread share. */
    private val lock = Any()
    private var buttons = 0
    private var pendX = 0f
    private var pendY = 0f
    private var carryX = 0f
    private var carryY = 0f
    private var pendingWheel = 0
    private var pendingPan = 0
    private var ticker: ScheduledFuture<*>? = null
    private var lastMoveNs = 0L

    private val sender: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "glidr-mouse").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
        }
    }

    /** Start the 250 Hz sender while a finger is down. */
    fun touchStart() {
        synchronized(lock) {
            motion.reset()
            scroll.reset()
            panScroll.reset()
            lastMoveNs = 0L
            if (ticker?.isDone == false) return
            ticker = sender.scheduleWithFixedDelay({ runCatching { tick() } }, 0, TICK_US, TimeUnit.MICROSECONDS)
        }
    }

    fun touchEnd() {
        synchronized(lock) {
            ticker?.cancel(false)
            ticker = null
        }
        flushNow()
    }

    /**
     * Queues a movement. Touch samples (several per frame, using Android's historical points) arrive unevenly,
     * so [tick] sends a share of what is queued every 4 ms instead of dumping each batch at once; the cursor then
     * moves at an even rate even when input comes in bursts.
     */
    fun move(dxPx: Float, dyPx: Float, nowNs: Long) {
        val dtMs = if (lastMoveNs == 0L) TICK_US / 1000f else (nowNs - lastMoveNs) / 1_000_000f
        lastMoveNs = nowNs
        motion.process(dxPx, dyPx, dtMs)
        synchronized(lock) {
            pendX += motion.outX
            pendY += motion.outY
        }
    }

    val connected: Boolean get() = transport.state.value == ConnectionState.Connected

    /** Returns the number of whole scroll detents produced, so the UI can tick. */
    fun scrollBy(dxPx: Float, dyPx: Float): Int {
        // Natural: content follows the fingers (finger up -> wheel down). HID wheel > 0 scrolls up, pan > 0 right.
        val sign = if (naturalScroll) 1 else -1
        val px = PX_PER_DETENT / scrollSpeed
        val v = scroll.add(dyPx, px)
        val h = panScroll.add(dxPx, px)
        synchronized(lock) {
            pendingWheel += sign * v
            pendingPan -= sign * h
        }
        return kotlin.math.abs(v) + kotlin.math.abs(h)
    }

    /** One sender step: release a fraction of the queued movement (exponential catch-up), scroll goes out whole. */
    private fun tick() {
        synchronized(lock) {
            val a = SMOOTH_A
            val tx = pendX * a
            val ty = pendY * a
            pendX -= tx
            pendY -= ty
            carryX += tx
            carryY += ty
            val x = carryX.toInt()
            val y = carryY.toInt()
            carryX -= x
            carryY -= y
            sendLocked(x, y, pendingWheel, pendingPan)
            pendingWheel = 0
            pendingPan = 0
        }
    }

    /** Sends everything that is queued right now; returns true if a report went out. */
    fun flushNow(): Boolean = synchronized(lock) {
        val x = (pendX + carryX).roundToInt()
        val y = (pendY + carryY).roundToInt()
        pendX = 0f
        pendY = 0f
        carryX = 0f
        carryY = 0f
        val w = pendingWheel
        val p = pendingPan
        pendingWheel = 0
        pendingPan = 0
        sendLocked(x, y, w, p)
    }

    /** Sends a movement, split into follow-up reports because one report carries at most +-127 per axis. */
    private fun sendLocked(dx: Int, dy: Int, wheel: Int, pan: Int): Boolean {
        if (dx == 0 && dy == 0 && wheel == 0 && pan == 0) return false
        var x = dx
        var y = dy
        var w = wheel
        var p = pan
        while (x != 0 || y != 0 || w != 0 || p != 0) {
            val sx = x.coerceIn(-127, 127)
            val sy = y.coerceIn(-127, 127)
            val sw = w.coerceIn(-127, 127)
            val sp = p.coerceIn(-127, 127)
            HidReports.mouse(mouseBuf, buttons, sx, sy, sw, sp)
            transport.sendMouse(mouseBuf)
            x -= sx
            y -= sy
            w -= sw
            p -= sp
        }
        return true
    }

    fun buttonDown(mask: Int) {
        flushNow()
        synchronized(lock) {
            buttons = buttons or mask
            sendButtons()
        }
    }

    fun buttonUp(mask: Int) {
        flushNow()
        synchronized(lock) {
            buttons = buttons and mask.inv()
            sendButtons()
        }
    }

    fun click(mask: Int) {
        buttonDown(mask)
        scope.launch {
            delay(CLICK_MS)
            buttonUp(mask)
        }
    }

    /** Caller holds [lock]. */
    private fun sendButtons() {
        HidReports.mouse(mouseBuf, buttons, 0, 0)
        transport.sendMouse(mouseBuf)
    }

    /**
     * Keep awake nudge. Subtle: a tiny move and back. Roam: glide the cursor a few hundred counts in a random
     * direction and leave it there, so over time it wanders to different places on the screen. A Bluetooth mouse
     * is relative-only, so exact screen positions are not possible.
     */
    fun nudge(roam: Boolean = false) {
        scope.launch {
            nudgeLock.withLock {
                if (!roam) {
                    HidReports.mouse(nudgeBuf, buttons, NUDGE_PX, 0)
                    transport.sendMouse(nudgeBuf)
                    delay(NUDGE_BACK_MS)
                    HidReports.mouse(nudgeBuf, buttons, -NUDGE_PX, 0)
                    transport.sendMouse(nudgeBuf)
                    return@withLock
                }
                val angle = Random.nextDouble(0.0, 2 * Math.PI)
                val distance = Random.nextDouble(ROAM_MIN, ROAM_MAX)
                val steps = (distance / ROAM_STEP).toInt().coerceAtLeast(10)
                for (d in NudgePath.roam(angle, distance, steps)) {
                    HidReports.mouse(nudgeBuf, buttons, d[0], d[1])
                    transport.sendMouse(nudgeBuf)
                    delay(ROAM_FRAME_MS)
                }
            }
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
        const val TICK_US = 4_000L // 250 Hz
        /** Share of the queued movement released per tick: 1 - e^(-4ms / 9ms), a ~9 ms catch-up. */
        val SMOOTH_A = (1.0 - exp(-4.0 / 9.0)).toFloat()
        const val CLICK_MS = 40L
        const val KEY_MS = 15L
        const val ROAM_MIN = 250.0
        const val ROAM_MAX = 800.0
        const val ROAM_STEP = 22.0
        const val ROAM_FRAME_MS = 8L
        const val NUDGE_PX = 3
        const val NUDGE_BACK_MS = 30L
        const val PX_PER_DETENT = 28f
    }
}
