package com.yashraj.glidr.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.ceil
import kotlin.math.abs
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.focusable
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import com.yashraj.glidr.ui.theme.Radius
import com.yashraj.glidr.ui.theme.Space
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yashraj.glidr.ui.theme.Glidr
import com.yashraj.glidr.ui.theme.LocalReduceMotion

/** Springs, not tweens; no animation at all when Reduce motion is on. */
@Composable
fun <T> motionSpec(stiffness: Float = Spring.StiffnessMedium) =
    if (LocalReduceMotion.current) snap<T>() else spring<T>(dampingRatio = 0.65f, stiffness = stiffness)

/** Minimum 48dp touch target, spring press feedback, accessible description. */
@Composable
fun PressBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(Radius.control),
    background: Color = Glidr.colors.s2,
    border: Boolean = true,
    content: @Composable () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.94f else 1f, motionSpec(), label = "press")
    Box(
        modifier
            .scale(s)
            .clip(shape)
            .background(background, shape)
            .then(if (border) Modifier.border(1.dp, Glidr.colors.line, shape) else Modifier)
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Glidr.colors.text, style: TextStyle = Glidr.type.label, maxLines: Int = Int.MAX_VALUE) {
    BasicText(text, modifier, style = style.copy(color = color), maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
}

enum class Ico { Mic, Settings, Awake, Keyboard, Back, Down, Pad, Media, Play, Pause, Prev, Next, Stop, Mute, VolUp, VolDown }

@Composable
fun Icon(kind: Ico, tint: Color, size: Dp = 24.dp) {
    val hole = Glidr.colors.s2
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val sw = w * 0.085f
        val st = Stroke(sw, cap = StrokeCap.Round)
        fun p(x: Float, y: Float) = Offset(x * w / 24f, y * w / 24f)
        when (kind) {
            Ico.Mic -> {
                drawRoundRect(tint, p(9f, 3f), Size(6f * w / 24f, 11f * w / 24f), androidx.compose.ui.geometry.CornerRadius(w * 0.125f), style = st)
                drawArc(tint, 0f, 180f, false, p(5.5f, 7f), Size(13f * w / 24f, 10f * w / 24f), style = st)
                drawLine(tint, p(12f, 17f), p(12f, 21f), sw, StrokeCap.Round)
            }
            Ico.Settings -> {
                drawLine(tint, p(4f, 7f), p(20f, 7f), sw, StrokeCap.Round)
                drawLine(tint, p(4f, 17f), p(20f, 17f), sw, StrokeCap.Round)
                drawCircle(hole, w * 0.1f, p(15f, 7f)); drawCircle(tint, w * 0.1f, p(15f, 7f), style = st)
                drawCircle(hole, w * 0.1f, p(9f, 17f)); drawCircle(tint, w * 0.1f, p(9f, 17f), style = st)
            }
            Ico.Awake -> { // coffee cup
                drawRoundRect(tint, p(5f, 8f), Size(11f * w / 24f, 10f * w / 24f), androidx.compose.ui.geometry.CornerRadius(w * 0.12f), style = st)
                drawArc(tint, -90f, 180f, false, p(14f, 9.5f), Size(5f * w / 24f, 5f * w / 24f), style = st)
                drawLine(tint, p(9f, 3f), p(9f, 5f), sw, StrokeCap.Round)
            }
            Ico.Keyboard -> {
                drawRoundRect(tint, p(3f, 6f), Size(18f * w / 24f, 12f * w / 24f), androidx.compose.ui.geometry.CornerRadius(w * 0.12f), style = st)
                for (x in listOf(7f, 12f, 17f)) drawCircle(tint, w * 0.03f, p(x, 10f))
                drawLine(tint, p(8f, 14.5f), p(16f, 14.5f), sw, StrokeCap.Round)
            }
            Ico.Back -> { drawLine(tint, p(15f, 5f), p(8f, 12f), sw, StrokeCap.Round); drawLine(tint, p(8f, 12f), p(15f, 19f), sw, StrokeCap.Round) }
            Ico.Down -> { drawLine(tint, p(6f, 9f), p(12f, 15f), sw, StrokeCap.Round); drawLine(tint, p(12f, 15f), p(18f, 9f), sw, StrokeCap.Round) }
            Ico.Pad -> drawRoundRect(tint, p(4f, 5f), Size(16f * w / 24f, 14f * w / 24f), androidx.compose.ui.geometry.CornerRadius(w * 0.2f), style = st)
            Ico.Media -> { drawCircle(tint, w * 0.34f, p(12f, 12f), style = st); drawCircle(tint, w * 0.07f, p(12f, 12f)) }
            Ico.Play -> {
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(p(8f, 5.5f).x, p(8f, 5.5f).y); lineTo(p(19f, 12f).x, p(19f, 12f).y); lineTo(p(8f, 18.5f).x, p(8f, 18.5f).y); close() }
                drawPath(path, tint)
            }
            Ico.Pause -> { drawRect(tint, p(6f, 5f), Size(4.2f * w / 24f, 14f * w / 24f)); drawRect(tint, p(13.8f, 5f), Size(4.2f * w / 24f, 14f * w / 24f)) }
            Ico.Prev -> { drawLine(tint, p(6f, 6f), p(6f, 18f), sw, StrokeCap.Round); val q = androidx.compose.ui.graphics.Path().apply { moveTo(p(18f, 6.5f).x, p(18f, 6.5f).y); lineTo(p(9f, 12f).x, p(9f, 12f).y); lineTo(p(18f, 17.5f).x, p(18f, 17.5f).y); close() }; drawPath(q, tint) }
            Ico.Next -> { drawLine(tint, p(18f, 6f), p(18f, 18f), sw, StrokeCap.Round); val q = androidx.compose.ui.graphics.Path().apply { moveTo(p(6f, 6.5f).x, p(6f, 6.5f).y); lineTo(p(15f, 12f).x, p(15f, 12f).y); lineTo(p(6f, 17.5f).x, p(6f, 17.5f).y); close() }; drawPath(q, tint) }
            Ico.Stop -> drawRoundRect(tint, p(6f, 6f), Size(12f * w / 24f, 12f * w / 24f), androidx.compose.ui.geometry.CornerRadius(w * 0.1f))
            Ico.Mute -> { val q = androidx.compose.ui.graphics.Path().apply { moveTo(p(4f, 9.5f).x, p(4f, 9.5f).y); lineTo(p(8f, 9.5f).x, p(8f, 9.5f).y); lineTo(p(13f, 5.5f).x, p(13f, 5.5f).y); lineTo(p(13f, 18.5f).x, p(13f, 18.5f).y); lineTo(p(8f, 14.5f).x, p(8f, 14.5f).y); lineTo(p(4f, 14.5f).x, p(4f, 14.5f).y); close() }; drawPath(q, tint, style = st); drawLine(tint, p(16f, 9f), p(21f, 15f), sw, StrokeCap.Round); drawLine(tint, p(21f, 9f), p(16f, 15f), sw, StrokeCap.Round) }
            Ico.VolUp -> { drawLine(tint, p(5f, 12f), p(19f, 12f), sw, StrokeCap.Round); drawLine(tint, p(12f, 5f), p(12f, 19f), sw, StrokeCap.Round) }
            Ico.VolDown -> drawLine(tint, p(5f, 12f), p(19f, 12f), sw, StrokeCap.Round)
        }
    }
}

@Composable
fun RoundIconButton(kind: Ico, description: String, onClick: () -> Unit, active: Boolean = false, size: Dp = 48.dp) {
    val c = Glidr.colors
    PressBox(
        onClick, Modifier.size(size), description, CircleShape,
        background = if (active) c.accent else c.s2, border = !active,
    ) { Icon(kind, if (active) c.ink else c.text) }
}

/** Dim backdrop behind sheets and dialogs. */
val Scrim = Color(0x99000000)

/** Vertical gap inside a column. */
@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

/** Segmented control with a springy selected pill. Each segment is 48dp tall; the 4dp frame keeps radii concentric (16 → 12). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Glidr.colors
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.chip)).background(c.s2).padding(Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.hair)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) c.accent else Color.Transparent, motionSpec(), label = "seg")
            Box(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(Radius.chip - Space.xs)).background(bg)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) { Label(label, color = if (on) c.ink else c.dim, maxLines = 1) }
        }
    }
}

/** The switch graphic only. The row around it is the toggleable, so the whole row is the touch target. */
@Composable
fun ToggleSwitch(on: Boolean) {
    val c = Glidr.colors
    val bg by animateColorAsState(if (on) c.accent else c.s3, motionSpec(), label = "sw")
    val x by animateFloatAsState(if (on) 20f else 0f, motionSpec(), label = "swx")
    Box(Modifier.size(width = 52.dp, height = 32.dp).clip(CircleShape).background(bg).clearAndSetSemantics {}) {
        Box(Modifier.padding(start = (4 + x).dp, top = 4.dp).size(24.dp).clip(CircleShape).background(if (on) c.ink else c.dim))
    }
}

/**
 * Draggable slider, 48dp tall. TalkBack can adjust it (range info + set progress) and the arrow keys step it.
 * [tickEvery] draws a detent mark every that many units.
 */
@Composable
fun Slider(
    value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, description: String,
    step: Float = 0.1f, tickEvery: Float = 0f,
) {
    val c = Glidr.colors
    val span = range.endInclusive - range.start
    val frac = ((value - range.start) / span).coerceIn(0f, 1f)
    val change by rememberUpdatedState(onChange)
    fun snap(raw: Float) = (Math.round(raw / step) * step).coerceIn(range.start, range.endInclusive)
    fun at(x: Float, w: Float) = snap(range.start + (x / w).coerceIn(0f, 1f) * span)
    Box(
        Modifier.fillMaxWidth().height(48.dp)
            .semantics {
                contentDescription = description
                stateDescription = "%.1f".format(value)
                progressBarRangeInfo = ProgressBarRangeInfo(value, range)
                setProgress { target -> change(snap(target)); true }
            }
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft, Key.DirectionDown -> { change(snap(value - step)); true }
                    Key.DirectionRight, Key.DirectionUp -> { change(snap(value + step)); true }
                    else -> false
                }
            }
            .pointerInput(range) { detectTapGestures { change(at(it.x, size.width.toFloat())) } }
            .pointerInput(range) { detectDragGestures { ch, _ -> ch.consume(); change(at(ch.position.x, size.width.toFloat())) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(c.s3))
        Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(CircleShape).background(c.accent))
        if (tickEvery > 0f) {
            // Same inset as the thumb centre (14dp = half the thumb), so marks line up with where the thumb stops.
            Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                val inset = 14.dp.toPx()
                var t = range.start + tickEvery
                while (t < range.endInclusive - tickEvery / 2) {
                    val x = inset + (size.width - 2 * inset) * ((t - range.start) / span)
                    drawCircle(c.bg.copy(alpha = 0.55f), 1.5.dp.toPx(), Offset(x, size.height / 2))
                    t += tickEvery
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.padding(start = (maxWidth - 28.dp) * frac).size(28.dp).clip(CircleShape).background(c.text))
        }
    }
}

/**
 * Draws ticks as if they were on a rotating drum: spacing bunches up and ticks fade and shorten towards both ends,
 * so a strip reads as a dial. [offset] is how far the drum has been turned (px along the strip).
 */
fun DrawScope.dialTicks(vertical: Boolean, offset: Float, color: Color, tickLen: Float, step: Float, stroke: Float) {
    val length = if (vertical) size.height else size.width
    val cross = if (vertical) size.width else size.height
    val half = length / 2f
    val thetaMax = 1.45f
    val radius = half / thetaMax
    val norm = sin(thetaMax)
    var u = ((offset % step) + step) % step - ceil(half / step) * step
    while (u <= half) {
        val theta = u / radius
        if (abs(theta) < thetaMax) {
            val c = cos(theta)
            val pos = half + half * (sin(theta) / norm)
            val a = (c * c).coerceIn(0f, 1f)
            val len = tickLen * (0.4f + 0.6f * c)
            val from = (cross - len) / 2f
            val tint = color.copy(alpha = color.alpha * a)
            if (vertical) {
                drawLine(tint, Offset(from, pos), Offset(from + len, pos), strokeWidth = stroke)
            } else {
                drawLine(tint, Offset(pos, from), Offset(pos, from + len), strokeWidth = stroke)
            }
        }
        u += step
    }
}
