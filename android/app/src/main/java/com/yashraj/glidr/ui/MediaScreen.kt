package com.yashraj.glidr.ui

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.unit.Dp
import com.yashraj.glidr.ui.theme.Radius
import com.yashraj.glidr.ui.theme.Space
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yashraj.glidr.R
import com.yashraj.glidr.companion.CompanionState
import com.yashraj.glidr.companion.NowPlaying
import com.yashraj.glidr.hid.HidEngine
import com.yashraj.glidr.hid.Keys
import com.yashraj.glidr.ui.theme.Glidr
import kotlinx.coroutines.delay

@Composable
fun MediaScreen(
    engine: HidEngine,
    haptic: Int,
    now: NowPlaying?,
    link: CompanionState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val c = Glidr.colors
    val ctx = LocalContext.current
    val haptics = remember { Haptics(ctx) }
    fun send(usage: Int) {
        if (!engine.connected) {
            haptics.play(Haptic.Reject, haptic)
            return
        }
        engine.consumer(usage)
        haptics.play(Haptic.Click, haptic)
    }
    val transport: @Composable () -> Unit = {
        val side = if (compact) 56.dp else 68.dp
        val centre = if (compact) 80.dp else 104.dp
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) Space.lg else 22.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            PressBox({ send(Keys.CONSUMER_PREV) }, Modifier.size(side), stringResource(R.string.prev_track), CircleShape) { Icon(Ico.Prev, c.text, 26.dp) }
            PressBox({ send(Keys.CONSUMER_PLAY_PAUSE) }, Modifier.size(centre), stringResource(R.string.play_pause), CircleShape, background = c.accent, border = false) {
                Icon(if (now?.playing == true) Ico.Pause else Ico.Play, c.ink, if (compact) 30.dp else 38.dp)
            }
            PressBox({ send(Keys.CONSUMER_NEXT) }, Modifier.size(side), stringResource(R.string.next_track), CircleShape) { Icon(Ico.Next, c.text, 26.dp) }
        }
    }
    val tileH = if (compact) 60.dp else 76.dp
    val tiles: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Tile(Modifier.weight(1f), tileH, Ico.Mute, stringResource(R.string.mute)) { send(Keys.CONSUMER_MUTE) }
            Tile(Modifier.weight(1f), tileH, Ico.Stop, stringResource(R.string.stop)) { send(Keys.CONSUMER_STOP) }
        }
    }
    if (compact) {
        // Landscape: the card on the left, every control stacked on the right, nothing scrolls.
        Row(modifier.fillMaxSize().padding(horizontal = Space.lg), horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
            NowPlayingCard(now, link, Modifier.weight(1f).fillMaxHeight().padding(bottom = Space.sm))
            Column(Modifier.weight(1f).fillMaxHeight().padding(bottom = Space.sm), verticalArrangement = Arrangement.SpaceBetween) {
                transport()
                tiles()
                VolumeStrip(engine, haptic, tileH)
            }
        }
    } else {
        Column(modifier.fillMaxSize().padding(horizontal = Space.lg), verticalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Label(stringResource(R.string.media), color = c.text, style = Glidr.type.title)
                Label(stringResource(R.string.media_sub), color = c.dim, style = Glidr.type.small)
            }
            NowPlayingCard(now, link, Modifier.weight(1f).fillMaxWidth().padding(vertical = Space.lg))
            transport()
            Gap(Space.lg)
            tiles()
            VolumeStrip(engine, haptic, tileH, Modifier.padding(top = Space.sm))
        }
    }
}

/** Live card: cover art, title, artist, source app and a progress bar that keeps moving between updates. */
@Composable
private fun NowPlayingCard(now: NowPlaying?, link: CompanionState, modifier: Modifier) {
    val c = Glidr.colors
    val shape = RoundedCornerShape(Radius.sheet)
    Column(
        modifier.clip(shape).background(c.s1).border(1.dp, c.line, shape).padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (now == null) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Ico.Media, c.dim, 44.dp)
                Box(Modifier.height(14.dp))
                Label(
                    stringResource(if (link == CompanionState.Connected) R.string.nothing_playing else R.string.companion_missing),
                    color = c.text, style = Glidr.type.body,
                )
                Box(Modifier.height(6.dp))
                Label(
                    stringResource(if (link == CompanionState.Connected) R.string.nothing_playing_sub else R.string.companion_missing_sub),
                    color = c.dim, style = Glidr.type.small,
                )
            }
        } else {
            var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
            LaunchedEffect(now.playing, now.receivedAtMs) {
                while (now.playing) {
                    tick = SystemClock.elapsedRealtime()
                    delay(500)
                }
                tick = SystemClock.elapsedRealtime()
            }
            val elapsed = (now.positionMs + if (now.playing) (tick - now.receivedAtMs).coerceAtLeast(0) else 0)
                .coerceIn(0, if (now.durationMs > 0) now.durationMs else Long.MAX_VALUE)

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val art = now.art
                if (art != null) {
                    // Show the artwork in its own shape (square album cover or 16:9 video banner), uncropped.
                    val ratio = (art.width.toFloat() / art.height).coerceIn(0.6f, 2.2f)
                    Image(
                        art, contentDescription = stringResource(R.string.cover_art),
                        contentScale = ContentScale.Fit, filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(Radius.control)).background(c.s2),
                    )
                } else {
                    Box(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(Radius.control)).background(c.s2), contentAlignment = Alignment.Center) {
                        Icon(Ico.Media, c.dim, 56.dp)
                    }
                }
            }
            Box(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Label(now.title.ifEmpty { stringResource(R.string.unknown_title) }, color = c.text, style = Glidr.type.bodyLarge, maxLines = 1)
                val sub = listOf(now.artist, now.app).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotEmpty()) Label(sub, color = c.dim, style = Glidr.type.small, maxLines = 1)
            }
            Box(Modifier.height(14.dp))
            val frac = if (now.durationMs > 0) (elapsed.toFloat() / now.durationMs).coerceIn(0f, 1f) else 0f
            val progressText = stringResource(R.string.track_progress, fmt(elapsed), if (now.durationMs > 0) fmt(now.durationMs) else "--:--")
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(c.s3).semantics { contentDescription = progressText }) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(CircleShape).background(c.accent))
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Label(fmt(elapsed), color = c.dim, style = Glidr.type.mono)
                Label(if (now.durationMs > 0) fmt(now.durationMs) else "--:--", color = c.dim, style = Glidr.type.mono)
            }
        }
    }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun Tile(modifier: Modifier, height: Dp, icon: Ico, label: String, onClick: () -> Unit) {
    val c = Glidr.colors
    PressBox(onClick, modifier.height(height), label, RoundedCornerShape(Radius.tile)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, c.text)
            Label(label, color = c.dim, style = Glidr.type.small)
        }
    }
}

/**
 * Horizontal volume strip, the scroll strip turned sideways: ticks move with the finger, the pill grows and takes
 * the accent border while held, and every [STEP_DP] of drag sends one volume step (right = up, left = down).
 * Same thickness as the tiles above it.
 */
@Composable
private fun VolumeStrip(engine: HidEngine, haptic: Int, height: Dp, modifier: Modifier = Modifier) {
    val c = Glidr.colors
    val ctx = LocalContext.current
    val haptics = remember { Haptics(ctx) }
    val density = LocalDensity.current
    var pressed by remember { mutableStateOf(false) }
    var offset by remember { mutableFloatStateOf(0f) }
    val grow by animateFloatAsState(if (pressed) 1.12f else 1f, motionSpec(), label = "volGrow")
    val border by animateColorAsState(if (pressed) c.accent else c.line, motionSpec(), label = "volBorder")
    val shape = RoundedCornerShape(Radius.tile)
    val desc = stringResource(R.string.volume_strip)
    val upLabel = stringResource(R.string.vol_up)
    val downLabel = stringResource(R.string.vol_down)
    // Steps sent in this drag ("+3"), shown briefly in the middle. The real volume is unknown, so this says what was sent.
    var steps by remember { mutableIntStateOf(0) }
    var stamp by remember { mutableLongStateOf(0L) }
    LaunchedEffect(stamp) {
        if (stamp != 0L) {
            delay(900)
            steps = 0
        }
    }
    Box(
        modifier.fillMaxWidth().height(height)
            .semantics {
                contentDescription = desc
                // A drag cannot be done with TalkBack, so offer the same steps as actions.
                customActions = listOf(
                    CustomAccessibilityAction(upLabel) { if (engine.connected) engine.consumer(Keys.CONSUMER_VOL_UP); true },
                    CustomAccessibilityAction(downLabel) { if (engine.connected) engine.consumer(Keys.CONSUMER_VOL_DOWN); true },
                )
            }
            // Keep the edge back gesture from stealing a drag that starts near the screen edge.
            .systemGestureExclusion()
            .pointerInput(haptic) {
                val step = with(density) { STEP_DP.dp.toPx() }
                val tick = with(density) { 12.dp.toPx() }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    var last = down.position.x
                    var carry = 0f
                    var held = true
                    while (held) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id }
                        if (ch == null || !ch.pressed) {
                            held = false
                        } else {
                            val dx = ch.position.x - last
                            last = ch.position.x
                            offset = (offset + dx) % tick
                            carry += dx
                            while (carry >= step || carry <= -step) {
                                val up = carry > 0
                                carry += if (up) -step else step
                                if (engine.connected) {
                                    engine.consumer(if (up) Keys.CONSUMER_VOL_UP else Keys.CONSUMER_VOL_DOWN)
                                    haptics.play(Haptic.Tick, haptic)
                                    steps += if (up) 1 else -1
                                    stamp = SystemClock.elapsedRealtime()
                                } else {
                                    haptics.play(Haptic.Reject, haptic)
                                    carry = 0f
                                }
                            }
                            ch.consume()
                        }
                    }
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxWidth().fillMaxHeight().graphicsLayer { scaleY = grow }
                .clip(shape).background(c.s2).border(1.dp, border, shape)
                .drawBehind { dialTicks(false, offset, c.dim, 30.dp.toPx(), 12.dp.toPx(), 1.5.dp.toPx()) },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Icon(Ico.VolDown, c.text, 24.dp)
            if (steps != 0) Label(if (steps > 0) "+$steps" else "−${-steps}", color = c.accentText, style = Glidr.type.mono)
            Icon(Ico.VolUp, c.text, 24.dp)
        }
    }
}

private const val STEP_DP = 16
