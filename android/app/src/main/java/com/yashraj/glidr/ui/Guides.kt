package com.yashraj.glidr.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.yashraj.glidr.R
import com.yashraj.glidr.ui.theme.Glidr
import com.yashraj.glidr.ui.theme.MAX_DIALOG_WIDTH
import com.yashraj.glidr.ui.theme.Radius
import com.yashraj.glidr.ui.theme.Space
import androidx.compose.ui.unit.dp

/** A numbered step: filled accent circle while it is the current step, a tick once done, grey while still ahead. */
@Composable
fun NumberedStep(n: Int, text: String, done: Boolean, active: Boolean, extra: (@Composable ColumnScope.() -> Unit)? = null) {
    val c = Glidr.colors
    val lit = done || active
    val badge by animateColorAsState(if (lit) c.accent else c.s3, settleSpec(Spring.StiffnessMediumLow), label = "step")
    Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(24.dp).popOnChange(done, from = 0.5f).clip(CircleShape).background(badge), contentAlignment = Alignment.Center) {
            Label(if (done) "✓" else "$n", color = if (lit) c.ink else c.dim, style = Glidr.type.small)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Label(text, color = if (lit) c.text else c.dim, style = Glidr.type.body)
            extra?.invoke(this)
        }
    }
}

/**
 * The three pairing steps. [step] is where the user is now: 1 and 2 until a PC is paired, 3 once one is paired,
 * 4 (all done) while connected.
 */
@Composable
fun PairGuide(step: Int, onDiscoverable: () -> Unit) {
    val c = Glidr.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        NumberedStep(1, stringResource(R.string.pair_step_1), done = step > 2, active = step <= 2) {
            if (step <= 2) {
                PressBox(onDiscoverable, Modifier.fillMaxWidth().height(48.dp), null, background = c.accent, border = false) {
                    Label(stringResource(R.string.make_discoverable), color = c.ink)
                }
            }
        }
        NumberedStep(2, stringResource(R.string.pair_step_2), done = step > 2, active = step <= 2)
        NumberedStep(3, stringResource(R.string.pair_step_3), done = step > 3, active = step == 3)
        Label(stringResource(R.string.pair_help_forget), color = c.dim, style = Glidr.type.small)
    }
}

private val GESTURES = listOf(
    R.string.g_tap to R.string.g_tap_do,
    R.string.g_two_tap to R.string.g_two_tap_do,
    R.string.g_hold to R.string.g_hold_do,
    R.string.g_two_drag to R.string.g_two_drag_do,
    R.string.g_pinch to R.string.g_pinch_do,
    R.string.g_three_side to R.string.g_three_side_do,
    R.string.g_three_up to R.string.g_three_up_do,
    R.string.g_three_down to R.string.g_three_down_do,
    R.string.g_strip to R.string.g_strip_do,
)

/** Every pad gesture and what it does on the PC. */
@Composable
fun GestureList() {
    val c = Glidr.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        for ((gesture, effect) in GESTURES) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.Top) {
                Label(stringResource(gesture), Modifier.weight(1f), c.text, Glidr.type.small)
                Label(stringResource(effect), Modifier.weight(1f), c.dim, Glidr.type.small)
            }
        }
    }
}

/** First-run card: pair a PC, then learn the gestures. [pairStep] is the live pairing progress (see [PairGuide]). */
@Composable
fun WelcomeCard(pairStep: Int, onDiscoverable: () -> Unit, onClose: () -> Unit) {
    val c = Glidr.colors
    var page by rememberSaveable { mutableIntStateOf(0) }
    val last = page == 1
    Box(Modifier.fillMaxSize().appear().background(Scrim).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(Space.xl).widthIn(max = MAX_DIALOG_WIDTH).fillMaxWidth().appear(y = Space.xl, scale = 0.94f, fade = false)
                .clip(RoundedCornerShape(Radius.sheet)).background(c.s1)
                .border(1.dp, c.line, RoundedCornerShape(Radius.sheet)).animateContentSize(motionSpec(Spring.StiffnessMediumLow, 1f))
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            // The second page comes in from the side; the buttons stay put.
            Column(Modifier.enterFromSide(page, Space.xl), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Label(stringResource(if (last) R.string.gestures_title else R.string.welcome_title), color = c.text, style = Glidr.type.heading)
                Label(stringResource(if (last) R.string.gestures_sub else R.string.welcome_sub), color = c.dim, style = Glidr.type.small)
                if (last) GestureList() else PairGuide(pairStep, onDiscoverable)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PressBox(onClose, Modifier.weight(1f).height(52.dp)) { Label(stringResource(R.string.skip)) }
                PressBox({ if (last) onClose() else page = 1 }, Modifier.weight(1f).height(52.dp), null, background = c.accent, border = false) {
                    Label(stringResource(if (last) R.string.done else R.string.next), color = c.ink)
                }
            }
        }
    }
}
