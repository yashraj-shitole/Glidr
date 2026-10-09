package com.yashraj.glidr.ui.pad

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.yashraj.glidr.ui.theme.GlidrColors
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Makes the trackpad behave like a sheet of glass being pressed in. [depth] springs to 1 under the finger and
 * back past 0 on release (a small jelly bounce); [center] chases the finger a little behind it so the dent feels
 * like it has weight. The dot grid is drawn through [drawGlass], which pulls the dots towards the touch.
 */
@Stable
class GlassPress(private val reduceMotion: Boolean) {
    val depth = Animatable(0f)

    /** Smoothed position of the dent. */
    var center by mutableStateOf(Offset.Zero)
        private set
    private var target = Offset.Zero
    private var held by mutableStateOf(false)

    suspend fun press(pos: Offset) {
        target = pos
        center = pos
        held = true
        if (reduceMotion) depth.snapTo(1f) else depth.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 230f))
    }

    fun move(pos: Offset) {
        target = pos
    }

    suspend fun release() {
        held = false
        if (reduceMotion) depth.snapTo(0f) else depth.animateTo(0f, spring(dampingRatio = 0.36f, stiffness = 140f))
    }

    /** Frame loop that only runs while the glass is moving, so an idle pad costs nothing. */
    suspend fun run() {
        snapshotFlow { held || depth.value != 0f }.collectLatest { active ->
            if (!active) return@collectLatest
            var last = 0L
            while (true) {
                withFrameNanos { t ->
                    val dt = if (last == 0L) 0f else (t - last) / 1_000_000_000f
                    last = t
                    if (dt > 0f) {
                        val a = if (reduceMotion) 1f else 1f - exp(-dt * 15f)
                        center += (target - center) * a
                    }
                }
            }
        }
    }
}

/** Draws the dot grid bent around the touch, plus the shading that sells the glass. */
fun DrawScope.drawGlass(glass: GlassPress, colors: GlidrColors) {
    val step = 22.dp.toPx()
    val baseR = 1.dp.toPx()
    val d = glass.depth.value
    val c0 = glass.center
    val reach = 130.dp.toPx()
    val pull = 0.52f
    val active = d > 0.002f || d < -0.002f

    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            var px = x
            var py = y
            var r = baseR
            var alpha = colors.dot.alpha
            if (active) {
                val dx = x - c0.x
                val dy = y - c0.y
                val q = (dx * dx + dy * dy) / (reach * reach)
                if (q < 9f) {
                    val g = exp(-q)
                    // Squeeze the dots in towards the contact point, strongest right under the finger.
                    val f = 1f - pull * d * g
                    px = c0.x + dx * f
                    py = c0.y + dy * f
                    // The lens edge catches the light: dots on the rim get bigger and brighter.
                    val rim = 4f * g * (1f - g)
                    r = baseR * max(0.4f, 1f + 0.55f * d * rim - 0.35f * d * g)
                    alpha = min(1f, alpha * (1f + 4.2f * max(d, 0f) * rim))
                }
            }
            drawCircle(colors.dot.copy(alpha = alpha), r, Offset(px, py))
            x += step
        }
        y += step
    }

    val s = d.coerceIn(0f, 1.2f)
    if (s > 0.004f) {
        val radius = reach * 1.05f
        // Shadow pooling in the dent.
        val shade = if (colors.light) Color.Black else Color.Black
        drawCircle(
            Brush.radialGradient(
                0f to shade.copy(alpha = (if (colors.light) 0.10f else 0.42f) * s),
                0.55f to shade.copy(alpha = (if (colors.light) 0.04f else 0.16f) * s),
                1f to Color.Transparent,
                center = c0, radius = radius * 0.8f,
            ),
            radius * 0.8f, c0,
        )
        // Soft ring of light where the glass curves back up.
        val ring = if (colors.light) Color.Black.copy(alpha = 0.07f * s) else Color.White.copy(alpha = 0.10f * s)
        drawCircle(
            Brush.radialGradient(
                0.0f to Color.Transparent,
                0.58f to Color.Transparent,
                0.76f to ring,
                1.0f to Color.Transparent,
                center = c0, radius = radius,
            ),
            radius, c0,
        )
        // Accent light under the glass.
        drawCircle(
            Brush.radialGradient(
                listOf(colors.accent.copy(alpha = 0.30f * s), colors.accent.copy(alpha = 0.09f * s), Color.Transparent),
                c0, radius * 0.75f,
            ),
            radius * 0.75f, c0,
        )
    }
}
