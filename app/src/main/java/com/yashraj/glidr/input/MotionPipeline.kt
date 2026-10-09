package com.yashraj.glidr.input

import kotlin.math.sqrt

/**
 * Turns raw touch deltas (px) into integer HID counts: sensitivity, acceleration curve, fractional
 * carry so slow movement is never lost. Allocation-free; read [outX]/[outY] after [process].
 */
class MotionPipeline {
    var sensitivity = 1.6f

    /** 0 = off, 1..3 = low, medium, high. */
    var acceleration = 2

    var outX = 0
        private set
    var outY = 0
        private set

    private var carryX = 0f
    private var carryY = 0f

    fun reset() {
        carryX = 0f
        carryY = 0f
        outX = 0
        outY = 0
    }

    /** [dtMs] is the time since the previous sample; the speed term uses it to scale acceleration. */
    fun process(dxPx: Float, dyPx: Float, dtMs: Float) {
        val dt = if (dtMs < 1f) 1f else dtMs
        val speed = sqrt(dxPx * dxPx + dyPx * dyPx) / dt // px per ms
        val k = ACCEL_K[acceleration.coerceIn(0, 3)]
        val gain = sensitivity * (1f + k * (speed / SPEED_REF).coerceAtMost(MAX_SPEED_TERM))
        val x = dxPx * gain + carryX
        val y = dyPx * gain + carryY
        outX = x.toInt()
        outY = y.toInt()
        carryX = x - outX
        carryY = y - outY
    }

    /** Scroll: no acceleration, returns whole detents and carries the remainder. */
    class ScrollAccumulator {
        private var carry = 0f

        fun add(deltaPx: Float, pxPerDetent: Float): Int {
            val v = carry + deltaPx / pxPerDetent
            val whole = v.toInt()
            carry = v - whole
            return whole
        }

        fun reset() {
            carry = 0f
        }
    }

    private companion object {
        val ACCEL_K = floatArrayOf(0f, 0.6f, 1.2f, 2.0f)
        const val SPEED_REF = 1.5f
        const val MAX_SPEED_TERM = 3f
    }
}
