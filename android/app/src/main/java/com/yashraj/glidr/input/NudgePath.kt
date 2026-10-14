package com.yashraj.glidr.input

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Builds the relative mouse steps for one "roam" nudge: a smooth glide in one direction, fast in the middle. */
object NudgePath {
    /** Returns [steps] pairs of (dx, dy) whose sum is the straight line of [distance] counts at [angle] radians. */
    fun roam(angle: Double, distance: Double, steps: Int): List<IntArray> {
        require(steps > 0)
        // Ease in and out so the cursor accelerates and settles like a hand moving, instead of jumping.
        val weights = DoubleArray(steps) { sin(PI * (it + 0.5) / steps) }
        val total = weights.sum()
        val tx = cos(angle) * distance
        val ty = sin(angle) * distance
        var sx = 0.0
        var sy = 0.0
        var doneX = 0
        var doneY = 0
        val out = ArrayList<IntArray>(steps)
        for (w in weights) {
            sx += tx * w / total
            sy += ty * w / total
            // Round the running total, not each step, so no distance is lost to rounding.
            val nx = sx.roundToInt()
            val ny = sy.roundToInt()
            out += intArrayOf(nx - doneX, ny - doneY)
            doneX = nx
            doneY = ny
        }
        return out
    }
}
