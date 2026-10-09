package com.yashraj.glidr.ui.pad

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.systemGestureExclusion
import com.yashraj.glidr.ui.motionSpec
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yashraj.glidr.R
import com.yashraj.glidr.data.Settings
import com.yashraj.glidr.hid.HidEngine
import com.yashraj.glidr.hid.HidReports
import com.yashraj.glidr.hid.Keys
import com.yashraj.glidr.ui.Haptic
import com.yashraj.glidr.ui.Haptics
import com.yashraj.glidr.ui.Label
import com.yashraj.glidr.ui.PressBox
import com.yashraj.glidr.ui.theme.Glidr
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val TAP_SLOP_PX = 14f
private const val TAP_MAX_MS = 220L
private const val SWIPE_PX = 90f

/** Touch again within this long after a tap and the finger drags with the left button held. */
private const val DRAG_WINDOW_MS = 320L

@Composable
fun PadSurface(engine: HidEngine, settings: Settings, keepAwakeChip: String?, onTouch: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Glidr.colors
    val ctx = LocalContext.current
    val haptics = remember { Haptics(ctx) }
    val scope = rememberCoroutineScope()
    var glow by remember { mutableStateOf<Offset?>(null) }
    val glowAlpha = remember { Animatable(0f) }
    var tapPoint by remember { mutableStateOf(Offset.Zero) }
    val ripple = remember { Animatable(1f) }
    val level = settings.haptics
    val lastTapUp = remember { longArrayOf(0L) }

    Box(
        modifier
            .clip(RoundedCornerShape(32.dp))
            .background(c.s1)
            .border(1.dp, c.line, RoundedCornerShape(32.dp))
            .pointerInput(settings.tapToClick, level, settings.hapticScroll) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    val zone = STRIP_ZONE_DP.dp.toPx()
                    val inStripX = if (settings.leftHanded) first.position.x < zone else first.position.x > size.width - zone
                    val inStripY = first.position.y > size.height * 0.2f && first.position.y < size.height * 0.8f
                    if (inStripX && inStripY) return@awaitEachGesture
                    onTouch(true)
                    engine.touchStart()
                    glow = first.position
                    scope.launch { glowAlpha.animateTo(1f) }
                    val startMs = first.uptimeMillis
                    // Tap, then touch again and move = click and hold, so windows and selections can be dragged.
                    val dragging = settings.tapToClick && lastTapUp[0] != 0L && startMs - lastTapUp[0] <= DRAG_WINDOW_MS
                    lastTapUp[0] = 0L
                    if (dragging) {
                        engine.buttonDown(HidReports.BUTTON_LEFT)
                        haptics.play(Haptic.Click, level)
                    }
                    var maxPointers = 1
                    var travel = 0f
                    var swipeX = 0f
                    var swipeY = 0f
                    var swiped = false
                    var lastMs = startMs
                    try {
                    do {
                        val ev = awaitPointerEvent()
                        val down = ev.changes.filter { it.pressed }
                        lastMs = ev.changes.first().uptimeMillis
                        if (down.size > maxPointers) maxPointers = down.size
                        if (ev.type == PointerEventType.Move && down.isNotEmpty()) {
                            var dx = 0f
                            var dy = 0f
                            for (ch in down) {
                                val d = ch.positionChange()
                                dx += d.x
                                dy += d.y
                            }
                            dx /= down.size
                            dy /= down.size
                            travel += abs(dx) + abs(dy)
                            when {
                                down.size == 1 && maxPointers == 1 -> {
                                    glow = down[0].position
                                    engine.move(dx, dy, System.nanoTime())
                                }
                                down.size == 2 && maxPointers == 2 -> {
                                    if (engine.scrollBy(dx, dy) > 0 && settings.hapticScroll) haptics.play(Haptic.Tick, level)
                                }
                                down.size >= 3 -> {
                                    swipeX += dx
                                    swipeY += dy
                                }
                            }
                        }
                        ev.changes.forEach { it.consume() }
                    } while (ev.changes.any { it.pressed })
                    } finally {
                        // Never leave the button stuck down, even if the gesture is cancelled.
                        if (dragging) engine.buttonUp(HidReports.BUTTON_LEFT)
                    }

                    engine.touchEnd()
                    onTouch(false)
                    scope.launch { glowAlpha.animateTo(0f) }
                    val quick = lastMs - startMs <= TAP_MAX_MS && travel < TAP_SLOP_PX * maxPointers
                    if (maxPointers >= 3 && !swiped) {
                        if (abs(swipeX) > SWIPE_PX && abs(swipeX) > abs(swipeY)) {
                            engine.pressKey(Keys.TAB, HidReports.MOD_ALT)
                            haptics.play(Haptic.Tick, level)
                        } else if (swipeY < -SWIPE_PX) {
                            engine.pressKey(Keys.TAB, HidReports.MOD_GUI)
                            haptics.play(Haptic.Tick, level)
                        }
                        swiped = true
                    } else if (dragging) {
                        haptics.play(Haptic.Tick, level)
                    } else if (quick && settings.tapToClick) {
                        tapPoint = first.position
                        scope.launch { ripple.snapTo(0f); ripple.animateTo(1f) }
                        when (maxPointers) {
                            1 -> {
                                engine.click(HidReports.BUTTON_LEFT)
                                haptics.play(Haptic.Click, level)
                                lastTapUp[0] = lastMs
                            }
                            2 -> { engine.click(HidReports.BUTTON_RIGHT); haptics.play(Haptic.Thud, level) }
                        }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 22.dp.toPx()
            var y = step / 2
            while (y < size.height) {
                var x = step / 2
                while (x < size.width) {
                    drawCircle(c.dot, 1.dp.toPx(), Offset(x, y))
                    x += step
                }
                y += step
            }
            glow?.let {
                val a = glowAlpha.value
                if (a > 0.01f) {
                    drawCircle(
                        Brush.radialGradient(listOf(c.accent.copy(alpha = 0.34f * a), c.accent.copy(alpha = 0.11f * a), androidx.compose.ui.graphics.Color.Transparent), it, 100.dp.toPx()),
                        100.dp.toPx(), it,
                    )
                }
            }
            val r = ripple.value
            if (r < 1f) {
                drawCircle(c.accent.copy(alpha = 1f - r), (8 + 47 * r).dp.toPx(), tapPoint, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            }
        }
        Box(Modifier.align(Alignment.TopStart).padding(14.dp).clip(RoundedCornerShape(14.dp)).background(c.s2).padding(horizontal = 11.dp, vertical = 6.dp)) {
            Label("%.1f× speed".format(settings.sensitivity), color = c.dim, style = Glidr.type.mono)
        }
        if (keepAwakeChip != null) {
            Box(Modifier.align(Alignment.TopEnd).padding(14.dp).clip(RoundedCornerShape(14.dp)).background(c.s2).padding(horizontal = 11.dp, vertical = 6.dp)) {
                Label(keepAwakeChip, color = c.dim, style = Glidr.type.mono)
            }
        }
        Label(stringResource(R.string.pad_hint), Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), c.dim, Glidr.type.small)

        // Scroll strip, 10dp from the edge of the dominant hand, like the mockup.
        ScrollStrip(
            engine, settings, onTouch,
            Modifier.align(if (settings.leftHanded) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 1.dp).fillMaxHeight(0.6f),
        )
    }
}

/**
 * Vertical scroll strip: ticks move with the finger, the pill widens and takes the accent border while held.
 * It is a sibling target of the pad, so the pad ignores touches that start inside [STRIP_ZONE_DP].
 */
@Composable
private fun ScrollStrip(engine: HidEngine, settings: Settings, onTouch: (Boolean) -> Unit, modifier: Modifier) {
    val c = Glidr.colors
    val ctx = LocalContext.current
    val haptics = remember { Haptics(ctx) }
    val level = settings.haptics
    var pressed by remember { mutableStateOf(false) }
    var offset by remember { mutableFloatStateOf(0f) }
    val widen by animateFloatAsState(if (pressed) 1.18f else 1f, motionSpec(), label = "strip")
    val borderColor by animateColorAsState(if (pressed) c.accent else c.line, motionSpec(), label = "stripBorder")
    val shape = RoundedCornerShape(15.dp)
    val density = LocalDensity.current
    Box(
        modifier.width(STRIP_ZONE_DP.dp).semantics { contentDescription = "Scroll strip" }
            // Keep Android's edge back gesture off the strip. The system honours at most ~200dp per edge,
            // so the exclusion covers the middle of the strip.
            .systemGestureExclusion { coords ->
                val max = with(density) { 200.dp.toPx() }
                val h = coords.size.height.toFloat()
                val top = ((h - max) / 2f).coerceAtLeast(0f)
                Rect(0f, top, coords.size.width.toFloat(), top + minOf(h, max))
            }
            .pointerInput(level, settings.hapticScroll) {
                val tick = 12.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    onTouch(true)
                    engine.touchStart()
                    var lastY = down.position.y
                    var held = true
                    while (held) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id }
                        if (ch == null || !ch.pressed) {
                            held = false
                        } else {
                            val dy = ch.position.y - lastY
                            lastY = ch.position.y
                            if (dy != 0f) {
                                offset = (offset + dy) % tick
                                if (engine.scrollBy(0f, dy) > 0 && settings.hapticScroll) haptics.play(Haptic.Tick, level)
                            }
                            ch.consume()
                        }
                    }
                    pressed = false
                    engine.touchEnd()
                    onTouch(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.width(30.dp).fillMaxHeight().graphicsLayer { scaleX = widen }
                .clip(shape).background(c.s2).border(1.dp, borderColor, shape)
                .drawBehind {
                    val step = 12.dp.toPx()
                    val len = 10.dp.toPx()
                    val x0 = (size.width - len) / 2f
                    var y = ((offset % step) + step) % step
                    while (y < size.height) {
                        drawLine(c.dim, Offset(x0, y), Offset(x0 + len, y), strokeWidth = 1.5.dp.toPx())
                        y += step
                    }
                },
        )
    }
}

private const val STRIP_ZONE_DP = 48

@Composable
fun MouseButtons(engine: HidEngine, settings: Settings, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val haptics = remember { Haptics(ctx) }
    val level = settings.haptics
    val order = if (settings.leftHanded) listOf(2, 1, 0) else listOf(0, 1, 2)
    Row(modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (i in order) {
            val (label, mask, w) = when (i) {
                0 -> Triple(stringResource(R.string.left), HidReports.BUTTON_LEFT, 3f)
                1 -> Triple("", HidReports.BUTTON_MIDDLE, 1.1f)
                else -> Triple(stringResource(R.string.right), HidReports.BUTTON_RIGHT, 2f)
            }
            val desc = if (i == 1) stringResource(R.string.middle_click) else label
            Box(
                Modifier.weight(w).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(Glidr.colors.s2)
                    .border(1.dp, Glidr.colors.line, RoundedCornerShape(20.dp))
                    .androidxDesc(desc)
                    .pointerInput(mask, level) {
                        detectTapGestures(onPress = {
                            engine.buttonDown(mask)
                            haptics.play(if (mask == HidReports.BUTTON_RIGHT) Haptic.Thud else Haptic.Click, level)
                            tryAwaitRelease()
                            engine.buttonUp(mask)
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (i == 1) {
                    Box(Modifier.width(12.dp).height(24.dp).border(1.5.dp, Glidr.colors.text, RoundedCornerShape(6.dp)))
                } else {
                    Label(label)
                }
            }
        }
    }
}

private fun Modifier.androidxDesc(d: String) = this.then(
    Modifier.semantics { contentDescription = d },
)

@Composable
fun StatusLine(text: String, modifier: Modifier = Modifier) {
    Column(modifier) { Label(text, color = Glidr.colors.dim, style = Glidr.type.small) }
}
