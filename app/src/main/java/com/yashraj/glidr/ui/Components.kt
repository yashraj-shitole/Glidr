package com.yashraj.glidr.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
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
fun Icon(kind: Ico, tint: Color, size: Dp = 22.dp) {
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

/** Segmented control with a springy selected pill. Each segment is at least 48dp tall. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Glidr.colors
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.s2).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) c.accent else Color.Transparent, motionSpec(), label = "seg")
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(11.dp)).background(bg)
                    .clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Label(label, color = if (on) c.ink else c.dim) }
        }
    }
}

@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit, description: String) {
    val c = Glidr.colors
    val bg by animateColorAsState(if (on) c.accent else c.s3, motionSpec(), label = "sw")
    val x by animateFloatAsState(if (on) 20f else 0f, motionSpec(), label = "swx")
    Box(
        Modifier.size(width = 52.dp, height = 48.dp).clickable(role = Role.Switch) { onChange(!on) }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(52.dp, 32.dp).clip(CircleShape).background(bg)) {
            Box(Modifier.padding(start = (3 + x).dp, top = 3.dp).size(26.dp).clip(CircleShape).background(if (on) c.ink else c.dim))
        }
    }
}

/** Draggable slider; min touch height 48dp. */
@Composable
fun Slider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, description: String, step: Float = 0.1f) {
    val c = Glidr.colors
    val frac = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    fun at(x: Float, w: Float): Float {
        val f = (x / w).coerceIn(0f, 1f)
        val raw = range.start + f * (range.endInclusive - range.start)
        return (Math.round(raw / step) * step).coerceIn(range.start, range.endInclusive)
    }
    Box(
        Modifier.fillMaxWidth().height(48.dp)
            .semantics { contentDescription = "$description %.1f".format(value) }
            .pointerInput(range) { detectTapGestures { onChange(at(it.x, size.width.toFloat())) } }
            .pointerInput(range) { detectDragGestures { ch, _ -> ch.consume(); onChange(at(ch.position.x, size.width.toFloat())) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(c.s3))
        Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(CircleShape).background(c.accent))
        Box(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                Box(Modifier.padding(start = ((maxWidth - 28.dp) * frac)).size(28.dp).clip(CircleShape).background(c.text))
            }
        }
    }
}
