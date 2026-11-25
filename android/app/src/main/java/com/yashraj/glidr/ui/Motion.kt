package com.yashraj.glidr.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yashraj.glidr.ui.theme.LocalReduceMotion

/*
 * Every micro-animation goes through this file, so there is one place that makes them springs and one place that
 * turns them off. All of them are no-ops (or snap) when Reduce motion is on; MainActivity folds the system's
 * "remove animations" setting into the same flag.
 *
 * Scale, alpha and offset are read inside graphicsLayer blocks: a running animation redraws a layer without
 * recomposing or re-laying out anything, which matters next to the trackpad.
 */

/** Springs, not tweens; no animation at all when Reduce motion is on. */
@Composable
fun <T> motionSpec(stiffness: Float = Spring.StiffnessMedium, damping: Float = 0.65f) =
    if (LocalReduceMotion.current) snap<T>() else spring<T>(dampingRatio = damping, stiffness = stiffness)

/** For colours and anything else that must not overshoot. */
@Composable
fun <T> settleSpec(stiffness: Float = Spring.StiffnessMedium) = motionSpec<T>(stiffness, Spring.DampingRatioNoBouncy)

/** How far controls shrink under a finger. Small targets move more than large ones, so the change looks the same. */
object PressScale {
    const val KEY = 0.9f
    const val CONTROL = 0.94f
    const val CARD = 0.96f
    const val LARGE = 0.98f
}

/** Springs down to [to] while [pressed], and back with a little bounce on release. */
@Composable
fun Modifier.pressScale(pressed: Boolean, to: Float = PressScale.CONTROL): Modifier {
    val scale = animateFloatAsState(if (pressed) to else 1f, motionSpec(), label = "press")
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** [pressScale] for a clickable, selectable or toggleable that was given [source]. */
@Composable
fun Modifier.pressScale(source: InteractionSource, to: Float = PressScale.CONTROL): Modifier {
    val pressed by source.collectIsPressedAsState()
    return pressScale(pressed, to)
}

/**
 * Runs 0 to 1 once each time [key] changes, never on first composition, and stays at 1 under Reduce motion. The new
 * value is created during composition, so the first frame of new content is already at the start of the movement.
 */
@Composable
private fun <T : Any> rememberChange(key: T, damping: Float, stiffness: Float, onChange: (from: T) -> Unit = {}): Animatable<Float, *> {
    val reduce = LocalReduceMotion.current
    val last = remember { mutableListOf(key) }
    val progress = remember(key) {
        val from = last[0]
        last[0] = key
        if (from != key) onChange(from)
        Animatable(if (from == key || reduce) 1f else 0f)
    }
    LaunchedEffect(progress) {
        if (progress.value != 1f) progress.animateTo(1f, spring(dampingRatio = damping, stiffness = stiffness))
    }
    return progress
}

/** Content that just changed (a new [key]) fades in while sliding into place from ([x], [y]). */
@Composable
fun Modifier.enterOnChange(key: Any, x: Dp = 0.dp, y: Dp = 0.dp): Modifier {
    val progress = rememberChange(key, Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
    return graphicsLayer {
        val p = progress.value
        if (p < 1f) {
            alpha = p.coerceIn(0f, 1f)
            translationX = (1f - p) * x.toPx()
            translationY = (1f - p) * y.toPx()
        }
    }
}

/** [enterOnChange] for tabs: the new content comes from the side of the tab that was picked. */
@Composable
fun Modifier.enterFromSide(index: Int, distance: Dp): Modifier {
    val direction = remember { floatArrayOf(1f) }
    val progress = rememberChange(index, Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow) { from ->
        direction[0] = if (index > from) 1f else -1f
    }
    return graphicsLayer {
        val p = progress.value
        if (p < 1f) {
            alpha = p.coerceIn(0f, 1f)
            translationX = (1f - p) * direction[0] * distance.toPx()
        }
    }
}

/** A quick bounce from [from] back to full size each time [key] changes. */
@Composable
fun Modifier.popOnChange(key: Any, from: Float = 0.8f): Modifier {
    val progress = rememberChange(key, Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
    return graphicsLayer {
        val s = from + (1f - from) * progress.value
        scaleX = s
        scaleY = s
    }
}

/** One entrance when this first appears: for dialogs and sheets. Fades in, rising [y] and growing from [scale]. */
@Composable
fun Modifier.appear(y: Dp = 0.dp, scale: Float = 1f, fade: Boolean = true): Modifier {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    }
    return graphicsLayer {
        val p = progress.value
        if (p != 1f) {
            if (fade) alpha = p.coerceIn(0f, 1f)
            translationY = (1f - p) * y.toPx()
            val s = scale + (1f - scale) * p
            scaleX = s
            scaleY = s
        }
    }
}

/** A block that unfolds into a column and folds away again, instead of jumping the rows below it. */
@Composable
fun Reveal(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduce = LocalReduceMotion.current
    val size = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow, IntSize.VisibilityThreshold)
    val fade = spring<Float>(stiffness = Spring.StiffnessMedium)
    AnimatedVisibility(
        visible, modifier,
        enter = if (reduce) EnterTransition.None else expandVertically(size) + fadeIn(fade),
        exit = if (reduce) ExitTransition.None else shrinkVertically(size) + fadeOut(fade),
    ) { content() }
}
