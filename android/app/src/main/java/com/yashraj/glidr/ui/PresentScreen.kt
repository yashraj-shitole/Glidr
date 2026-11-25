package com.yashraj.glidr.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.yashraj.glidr.R
import com.yashraj.glidr.data.Settings
import com.yashraj.glidr.hid.HidEngine
import com.yashraj.glidr.hid.HidReports
import com.yashraj.glidr.hid.Keys
import com.yashraj.glidr.ui.theme.Glidr
import com.yashraj.glidr.ui.theme.Radius
import com.yashraj.glidr.ui.theme.Space

/** m:ss, or h:mm:ss from one hour. */
internal fun formatElapsed(totalSec: Int): String {
    val s = totalSec.coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Everything the presenter screen shows and does, supplied by the view model. */
class PresentState(
    val elapsedSec: Int,
    val running: Boolean,
    val buzzEveryIndex: Int,
    val volumeKeys: Boolean,
    val onToggleTimer: () -> Unit,
    val onResetTimer: () -> Unit,
    val onBuzz: (Int) -> Unit,
    val onVolumeKeys: (Boolean) -> Unit,
    val onSlide: (next: Boolean) -> Unit,
)

/**
 * Slide remote: two large Previous / Next targets at thumb height, an elapsed-time timer that can buzz at intervals,
 * and one-tap slideshow keys (F5, Shift+F5, Esc, B, W). The hardware volume keys can turn slides while this screen is
 * open. The screen stays on while it is showing.
 */
@Composable
fun PresentScreen(engine: HidEngine, settings: Settings, state: PresentState, compact: Boolean, modifier: Modifier = Modifier) {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    if (compact) {
        Row(modifier.fillMaxSize().padding(horizontal = Space.lg).padding(bottom = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                TimerCard(state)
                ShowKeys(engine, settings)
                VolumeKeysRow(state)
            }
            SlideButtons(state, Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(modifier.fillMaxSize().padding(horizontal = Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Column(Modifier.padding(horizontal = Space.xs)) {
                Label(stringResource(R.string.present), color = Glidr.colors.text, style = Glidr.type.title)
                Label(stringResource(R.string.present_sub), color = Glidr.colors.dim, style = Glidr.type.small)
            }
            TimerCard(state)
            ShowKeys(engine, settings)
            VolumeKeysRow(state)
            SlideButtons(state, Modifier.weight(1f).fillMaxWidth().padding(bottom = Space.sm))
        }
    }
}

@Composable
private fun TimerCard(state: PresentState) {
    val c = Glidr.colors
    val shape = RoundedCornerShape(Radius.card)
    var resets by remember { mutableIntStateOf(0) }
    val toggleBg by animateColorAsState(if (state.running) c.s2 else c.accent, settleSpec(), label = "timerBg")
    val status by animateColorAsState(if (state.running) c.accentText else c.dim, settleSpec(), label = "timerStatus")
    Column(
        Modifier.fillMaxWidth().clip(shape).background(c.s1).border(1.dp, c.line, shape).padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Label(
                    formatElapsed(state.elapsedSec), Modifier.popOnChange(resets, from = 0.85f), color = c.text,
                    style = Glidr.type.mono.copy(fontSize = 40.sp, fontWeight = FontWeight.SemiBold),
                )
                Label(
                    stringResource(if (state.running) R.string.present_running else R.string.present_timer),
                    Modifier.enterOnChange(state.running, y = Space.xs), status, Glidr.type.small,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PressBox(
                    state.onToggleTimer, Modifier.height(48.dp).width(96.dp), stringResource(if (state.running) R.string.pause else R.string.present_start_timer),
                    RoundedCornerShape(Radius.card), background = toggleBg, border = state.running,
                ) {
                    Label(
                        stringResource(if (state.running) R.string.pause else R.string.present_start_timer),
                        Modifier.enterOnChange(state.running, y = Space.sm), if (state.running) c.text else c.ink,
                    )
                }
                PressBox({ resets++; state.onResetTimer() }, Modifier.height(48.dp), stringResource(R.string.present_reset), RoundedCornerShape(Radius.card)) {
                    Label(stringResource(R.string.present_reset), Modifier.padding(horizontal = 20.dp))
                }
            }
        }
        Column {
            Label(stringResource(R.string.present_buzz), color = c.dim, style = Glidr.type.small)
            Gap(Space.sm)
            Segmented(
                listOf(stringResource(R.string.off), stringResource(R.string.buzz_5), stringResource(R.string.buzz_10), stringResource(R.string.buzz_15)),
                state.buzzEveryIndex, state.onBuzz,
            )
        }
    }
}

private class ShowKey(val nameRes: Int, val descRes: Int, val key: String, val mods: Int, val usage: Int = 0, val letter: Char? = null)

private val SHOW_KEYS = listOf(
    ShowKey(R.string.show_start, R.string.show_start_d, "F5", 0, usage = Keys.F1 + 4),
    ShowKey(R.string.show_here, R.string.show_here_d, "⇧F5", HidReports.MOD_SHIFT, usage = Keys.F1 + 4),
    ShowKey(R.string.show_end, R.string.show_end_d, "Esc", 0, usage = Keys.ESC),
    ShowKey(R.string.show_black, R.string.show_black_d, "B", 0, letter = 'b'),
    ShowKey(R.string.show_white, R.string.show_white_d, "W", 0, letter = 'w'),
)

/** Slideshow keys in one row of five cards (the same card look as the keyboard's Shortcuts tab). */
@Composable
private fun ShowKeys(engine: HidEngine, settings: Settings) {
    val c = Glidr.colors
    val ctx = LocalContext.current
    val haptics = remember { Haptics(ctx) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        for (k in SHOW_KEYS) {
            val name = stringResource(k.nameRes)
            val desc = stringResource(k.descRes)
            val src = remember { MutableInteractionSource() }
            Column(
                Modifier.weight(1f).height(64.dp).pressScale(src, PressScale.CARD).clip(RoundedCornerShape(Radius.control)).background(c.s2)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.control))
                    .semantics { role = Role.Button; contentDescription = desc }
                    .clickable(interactionSource = src, indication = LocalIndication.current) {
                        val usage = k.letter?.let { engine.layout.strokeFor(it)?.usage } ?: k.usage
                        if (engine.connected && usage != 0) {
                            engine.pressKey(usage, k.mods)
                            haptics.play(Haptic.Confirm, settings.haptics)
                        } else {
                            haptics.play(Haptic.Reject, settings.haptics)
                        }
                    }
                    .padding(Space.sm),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Label(name, maxLines = 1, style = Glidr.type.small)
                Label(k.key, color = c.dim, style = Glidr.type.mono, maxLines = 1)
            }
        }
    }
}

@Composable
private fun VolumeKeysRow(state: PresentState) {
    val shape = RoundedCornerShape(Radius.card)
    val c = Glidr.colors
    Box(Modifier.fillMaxWidth().clip(shape).background(c.s1).border(1.dp, c.line, shape).padding(horizontal = Space.lg)) {
        SwitchRow(stringResource(R.string.present_volume), stringResource(R.string.present_volume_sub), state.volumeKeys, state.onVolumeKeys)
    }
}

/** Previous (quiet) and Next (accent, wider): the two targets a presenter hits blind, so they fill the free space. */
@Composable
private fun SlideButtons(state: PresentState, modifier: Modifier) {
    val c = Glidr.colors
    // While held, each chevron leans the way it will turn the slides.
    val prev = remember { MutableInteractionSource() }
    val next = remember { MutableInteractionSource() }
    val prevDown by prev.collectIsPressedAsState()
    val nextDown by next.collectIsPressedAsState()
    val prevLean = animateFloatAsState(if (prevDown) -1f else 0f, motionSpec(), label = "prevLean")
    val nextLean = animateFloatAsState(if (nextDown) 1f else 0f, motionSpec(), label = "nextLean")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        PressBox(
            { state.onSlide(false) }, Modifier.weight(1f).fillMaxHeight(), stringResource(R.string.present_prev),
            RoundedCornerShape(Radius.pad), pressedScale = PressScale.LARGE, source = prev,
        ) { Box(Modifier.graphicsLayer { translationX = prevLean.value * Space.md.toPx() }) { Icon(Ico.Back, c.text, 40.dp) } }
        PressBox(
            { state.onSlide(true) }, Modifier.weight(1.6f).fillMaxHeight(), stringResource(R.string.present_next),
            RoundedCornerShape(Radius.pad), background = c.accent, border = false, pressedScale = PressScale.LARGE, source = next,
        ) {
            // The chevron icon points left; turn it for "next".
            Box(Modifier.graphicsLayer { rotationZ = 180f; translationX = nextLean.value * Space.md.toPx() }) { Icon(Ico.Back, c.ink, 48.dp) }
        }
    }
}
