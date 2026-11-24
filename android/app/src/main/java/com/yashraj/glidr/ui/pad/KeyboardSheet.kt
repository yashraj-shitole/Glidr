package com.yashraj.glidr.ui.pad

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yashraj.glidr.R
import com.yashraj.glidr.data.Settings
import com.yashraj.glidr.hid.HidEngine
import com.yashraj.glidr.hid.HidReports
import com.yashraj.glidr.hid.KeyboardLayout
import com.yashraj.glidr.hid.Keys
import com.yashraj.glidr.ui.Haptic
import com.yashraj.glidr.ui.Haptics
import com.yashraj.glidr.ui.Ico
import com.yashraj.glidr.ui.Icon
import com.yashraj.glidr.ui.Label
import com.yashraj.glidr.ui.PressBox
import com.yashraj.glidr.ui.Segmented
import com.yashraj.glidr.ui.theme.Glidr
import com.yashraj.glidr.ui.theme.Radius
import com.yashraj.glidr.ui.theme.Space
import com.yashraj.glidr.voice.SttEngine
import com.yashraj.glidr.voice.SttState
import kotlinx.coroutines.withTimeoutOrNull

private val LETTER_ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
private val SYMBOL_ROWS = listOf("1234567890", "@#$%&-+()/", "*\":;!?=_^'", "[]{}<>\\|~`")

/** Every key is 44dp tall. Six rows plus five 8dp gaps make the 304dp working area all four tabs share. */
private val KEY_HEIGHT = 44.dp
private val KEYS_AREA = KEY_HEIGHT * 6 + Space.sm * 5

/** A held key fires once on press, again after this delay, then at the repeat interval. */
private const val REPEAT_DELAY_MS = 400L
private const val REPEAT_MS = 45L

/** Holding the mic this long makes it push-to-talk: letting go ends dictation. A shorter tap toggles it. */
private const val HOLD_TO_TALK_MS = 500L

/**
 * A shortcut is either a fixed key ([usage]) or a [letter]. Windows matches letter shortcuts by the character the
 * PC's layout puts on the key, so letters go through the layout table (on DE, Z sits where US has Y).
 */
private class Shortcut(val nameRes: Int, val keys: List<String>, val mods: Int, val usage: Int = 0, val letter: Char? = null) {
    fun usageFor(layout: KeyboardLayout): Int = letter?.let { layout.strokeFor(it)?.usage } ?: usage
}

private val CTRL = HidReports.MOD_CTRL
private val WIN = HidReports.MOD_GUI

private val SHORTCUTS = listOf(
    Shortcut(R.string.sc_copy, listOf("Ctrl", "C"), CTRL, letter = 'c'),
    Shortcut(R.string.sc_paste, listOf("Ctrl", "V"), CTRL, letter = 'v'),
    Shortcut(R.string.sc_cut, listOf("Ctrl", "X"), CTRL, letter = 'x'),
    Shortcut(R.string.sc_undo, listOf("Ctrl", "Z"), CTRL, letter = 'z'),
    Shortcut(R.string.sc_redo, listOf("Ctrl", "Y"), CTRL, letter = 'y'),
    Shortcut(R.string.sc_select_all, listOf("Ctrl", "A"), CTRL, letter = 'a'),
    Shortcut(R.string.sc_save, listOf("Ctrl", "S"), CTRL, letter = 's'),
    Shortcut(R.string.sc_find, listOf("Ctrl", "F"), CTRL, letter = 'f'),
    Shortcut(R.string.sc_close_tab, listOf("Ctrl", "W"), CTRL, letter = 'w'),
    Shortcut(R.string.sc_switch_app, listOf("Alt", "Tab"), HidReports.MOD_ALT, usage = Keys.TAB),
    Shortcut(R.string.sc_task_view, listOf("Win", "Tab"), WIN, usage = Keys.TAB),
    Shortcut(R.string.sc_desktop, listOf("Win", "D"), WIN, letter = 'd'),
    Shortcut(R.string.sc_explorer, listOf("Win", "E"), WIN, letter = 'e'),
    Shortcut(R.string.sc_lock, listOf("Win", "L"), WIN, letter = 'l'),
    Shortcut(R.string.sc_close_window, listOf("Alt", "F4"), HidReports.MOD_ALT, usage = Keys.F4),
    Shortcut(R.string.sc_task_manager, listOf("Ctrl", "Shift", "Esc"), CTRL or HidReports.MOD_SHIFT, usage = Keys.ESC),
    Shortcut(R.string.sc_screenshot, listOf("Win", "Shift", "S"), WIN or HidReports.MOD_SHIFT, letter = 's'),
    Shortcut(R.string.sc_run, listOf("Win", "R"), WIN, letter = 'r'),
)

private enum class Tab { Keys, Shortcuts, More, Voice }

@Composable
fun KeyboardSheet(
    engine: HidEngine,
    settings: Settings,
    stt: SttEngine,
    hasMic: Boolean,
    onRequestMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Glidr.colors
    val ctx = LocalContext.current
    val haptics = remember { Haptics(ctx) }
    val level = if (settings.hapticKeys) settings.haptics else 0
    // Saved, so closing and reopening the sheet (or rotating) keeps the tab, the symbols layer and any latched keys.
    var tab by rememberSaveable { mutableIntStateOf(Tab.Keys.ordinal) }
    var mods by rememberSaveable { mutableIntStateOf(0) }
    var shiftLatched by rememberSaveable { mutableStateOf(false) }
    var symbols by rememberSaveable { mutableStateOf(false) }

    fun send(usage: Int, extra: Int = 0) {
        if (!engine.connected) {
            haptics.play(Haptic.Reject, settings.haptics)
            return
        }
        engine.pressKey(usage, mods or extra)
        haptics.play(Haptic.Click, level)
        mods = 0
        shiftLatched = false
    }

    fun typeChar(ch: Char) {
        val stroke = engine.layout.strokeFor(if (shiftLatched) ch.uppercaseChar() else ch)
        if (stroke == null) haptics.play(Haptic.Reject, settings.haptics) else send(stroke.usage, stroke.modifiers)
    }

    fun latch(bit: Int) {
        mods = mods xor bit
        haptics.play(Haptic.Tick, level)
    }

    val shiftBit = if (shiftLatched) HidReports.MOD_SHIFT else 0
    val live = { engine.connected }
    val sheetShape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet)

    Column(
        modifier.fillMaxWidth().clip(sheetShape).background(c.s1).border(1.dp, c.line, sheetShape).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Segmented(
            listOf(stringResource(R.string.tab_keys), stringResource(R.string.tab_shortcuts), stringResource(R.string.tab_more), stringResource(R.string.tab_voice)),
            tab, { tab = it },
        )

        /** Esc, Tab, the latching Ctrl / Alt / Win and the arrows: shared by Keys and More so combos like Ctrl+Home work. */
        @Composable
        fun ModifierRow() {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                Key("Esc", Modifier.weight(1f), fn = true) { send(Keys.ESC) }
                Key("Tab", Modifier.weight(1f), fn = true) { send(Keys.TAB) }
                Key("Ctrl", Modifier.weight(1f), on = mods and HidReports.MOD_CTRL != 0, fn = true) { latch(HidReports.MOD_CTRL) }
                Key("Alt", Modifier.weight(1f), on = mods and HidReports.MOD_ALT != 0, fn = true) { latch(HidReports.MOD_ALT) }
                Key("Win", Modifier.weight(1f), on = mods and HidReports.MOD_GUI != 0, fn = true) { latch(HidReports.MOD_GUI) }
                Key("←", Modifier.weight(1f), stringResource(R.string.key_left), fn = true, repeat = true, repeatWhile = live) { send(Keys.LEFT) }
                Key("↓", Modifier.weight(1f), stringResource(R.string.key_down), fn = true, repeat = true, repeatWhile = live) { send(Keys.DOWN) }
                Key("↑", Modifier.weight(1f), stringResource(R.string.key_up), fn = true, repeat = true, repeatWhile = live) { send(Keys.UP) }
                Key("→", Modifier.weight(1f), stringResource(R.string.key_right), fn = true, repeat = true, repeatWhile = live) { send(Keys.RIGHT) }
            }
        }

        when (Tab.entries[tab]) {
            Tab.Keys -> {
                ModifierRow()
                if (symbols) {
                    for (row in SYMBOL_ROWS) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                            for (ch in row) Key(ch.toString(), Modifier.weight(1f)) { typeChar(ch) }
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        for (ch in "1234567890") Key(ch.toString(), Modifier.weight(1f)) { typeChar(ch) }
                    }
                    for ((idx, row) in LETTER_ROWS.withIndex()) {
                        Row(Modifier.padding(horizontal = if (idx == 1) 16.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                            if (idx == 2) Key("⇧", Modifier.weight(1.5f), stringResource(R.string.key_shift), on = shiftLatched, fn = true) { shiftLatched = !shiftLatched; haptics.play(Haptic.Tick, level) }
                            for (ch in row) Key(if (shiftLatched) ch.uppercase() else ch.toString(), Modifier.weight(1f)) { typeChar(ch) }
                            if (idx == 2) Key("⌫", Modifier.weight(1.5f), stringResource(R.string.key_backspace), fn = true, repeat = true, repeatWhile = live) { send(Keys.BACKSPACE) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Key(
                        if (symbols) "ABC" else "123", Modifier.weight(1.4f),
                        stringResource(if (symbols) R.string.key_letters else R.string.key_symbols), fn = true,
                    ) { symbols = !symbols; haptics.play(Haptic.Tick, level) }
                    if (symbols) {
                        Key("⌫", Modifier.weight(1f), stringResource(R.string.key_backspace), fn = true, repeat = true, repeatWhile = live) { send(Keys.BACKSPACE) }
                    } else {
                        Key(",", Modifier.weight(1f)) { typeChar(',') }
                    }
                    Key(stringResource(R.string.space), Modifier.weight(3.6f), repeat = true, repeatWhile = live) { send(Keys.SPACE) }
                    Key(".", Modifier.weight(1f)) { typeChar('.') }
                    Key("↵", Modifier.weight(1.6f), stringResource(R.string.key_enter), go = true) { send(Keys.ENTER) }
                }
            }

            // The grid scrolls inside the same 304dp the key layers use, so the sheet never changes height between tabs.
            // Cards are 72dp, so the fourth row peeks 8dp past the edge to show there is more below.
            Tab.Shortcuts -> Column(Modifier.height(KEYS_AREA).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                for (rowItems in SHORTCUTS.chunked(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        for (s in rowItems) {
                            val name = stringResource(s.nameRes)
                            Column(
                                Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(Radius.control)).background(c.s2)
                                    .border(1.dp, c.line, RoundedCornerShape(Radius.control))
                                    .clickable {
                                        if (engine.connected) {
                                            engine.pressKey(s.usageFor(engine.layout), s.mods)
                                            haptics.play(Haptic.Confirm, level)
                                        } else {
                                            haptics.play(Haptic.Reject, settings.haptics)
                                        }
                                    }
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Label(name, maxLines = 1)
                                Label(s.keys.joinToString(" + "), color = c.dim, style = Glidr.type.mono, maxLines = 1)
                            }
                        }
                    }
                }
            }

            Tab.More -> {
                ModifierRow()
                for (fRow in 0..1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        for (i in 0 until 6) {
                            val n = fRow * 6 + i
                            Key("F${n + 1}", Modifier.weight(1f), fn = true) { send(Keys.F1 + n, shiftBit) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Key("Home", Modifier.weight(1f), fn = true) { send(Keys.HOME, shiftBit) }
                    Key("End", Modifier.weight(1f), fn = true) { send(Keys.END, shiftBit) }
                    Key("PgUp", Modifier.weight(1f), fn = true, repeat = true, repeatWhile = live) { send(Keys.PAGE_UP, shiftBit) }
                    Key("PgDn", Modifier.weight(1f), fn = true, repeat = true, repeatWhile = live) { send(Keys.PAGE_DOWN, shiftBit) }
                    Key("Ins", Modifier.weight(1f), fn = true) { send(Keys.INSERT, shiftBit) }
                    Key("Del", Modifier.weight(1f), fn = true, repeat = true, repeatWhile = live) { send(Keys.DELETE, shiftBit) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Key("Caps", Modifier.weight(1f), fn = true) { send(Keys.CAPS_LOCK) }
                    Key("PrtSc", Modifier.weight(1f), fn = true) { send(Keys.PRINT_SCREEN, shiftBit) }
                    Key("ScrLk", Modifier.weight(1f), fn = true) { send(Keys.SCROLL_LOCK) }
                    Key("Pause", Modifier.weight(1f), fn = true) { send(Keys.PAUSE) }
                    Key("Menu", Modifier.weight(1f), fn = true) { send(Keys.MENU) }
                    Key("NumLk", Modifier.weight(1f), fn = true) { send(Keys.NUM_LOCK) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Key("⇧", Modifier.weight(1.5f), stringResource(R.string.key_shift), on = shiftLatched, fn = true) { shiftLatched = !shiftLatched; haptics.play(Haptic.Tick, level) }
                    Key(stringResource(R.string.space), Modifier.weight(3.6f), repeat = true, repeatWhile = live) { send(Keys.SPACE) }
                    Key("⌫", Modifier.weight(1.5f), stringResource(R.string.key_backspace), fn = true, repeat = true, repeatWhile = live) { send(Keys.BACKSPACE) }
                    Key("↵", Modifier.weight(1.6f), stringResource(R.string.key_enter), go = true) { send(Keys.ENTER) }
                }
            }

            Tab.Voice -> VoicePanel(engine, settings, stt, hasMic, onRequestMic, haptics)
        }
    }
}

@Composable
private fun VoicePanel(engine: HidEngine, settings: Settings, stt: SttEngine, hasMic: Boolean, onRequestMic: () -> Unit, haptics: Haptics) {
    val c = Glidr.colors
    val state by stt.state.collectAsStateWithLifecycle()
    val text by stt.text.collectAsStateWithLifecycle()
    val micLevel by stt.level.collectAsStateWithLifecycle()
    val typing by engine.typing.collectAsStateWithLifecycle()
    var note by remember { mutableStateOf<String?>(null) }
    val listening = state == SttState.Listening
    val notConnected = stringResource(R.string.not_connected)
    val dictate = stringResource(R.string.dictate)
    val res = LocalContext.current.resources

    val hint = when {
        state == SttState.Unavailable -> stringResource(R.string.voice_unavailable)
        state == SttState.Error -> stringResource(R.string.voice_error)
        note != null -> note!!
        text.isEmpty() -> stringResource(if (listening) R.string.voice_listening else R.string.voice_tap)
        else -> text
    }

    // The mic gesture reads the latest state without restarting, so a press never sees a stale value.
    val listeningNow by rememberUpdatedState(listening)
    val unavailableNow by rememberUpdatedState(state == SttState.Unavailable)
    val hasMicNow by rememberUpdatedState(hasMic)
    val micTap: () -> Unit = {
        note = null
        when {
            unavailableNow -> Unit
            !hasMicNow -> onRequestMic()
            listeningNow -> stt.stop()
            else -> { stt.start(); haptics.play(Haptic.Click, settings.haptics) }
        }
    }

    Column(Modifier.height(KEYS_AREA), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(Radius.control)).background(c.s2).border(1.dp, c.line, RoundedCornerShape(Radius.control)).padding(Space.md)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Label(hint, color = if (text.isEmpty() || note != null || state == SttState.Error) c.dim else c.text, style = Glidr.type.body)
            }
        }
        // Plain text, not chips: nothing here can be tapped, so it must not look like it can.
        Label(stringResource(R.string.voice_info, stt.language, engine.layout.label), color = c.dim, style = Glidr.type.small, maxLines = 1)
        Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Meter(micLevel, reversed = true)
            Box(Modifier.size(Space.xl))
            Box(
                Modifier.size(64.dp).scale(1f + 0.12f * micLevel).clip(CircleShape).background(if (listening) c.accent else c.s3)
                    .semantics {
                        role = Role.Button
                        contentDescription = dictate
                        onClick(dictate) { micTap(); true }
                    }
                    .pointerInput(Unit) {
                        // Tap toggles dictation. Press and hold is push-to-talk: it listens while the finger is down.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            if (unavailableNow) { waitForUpOrCancellation(); return@awaitEachGesture }
                            if (!hasMicNow) { waitForUpOrCancellation(); micTap(); return@awaitEachGesture }
                            val wasListening = listeningNow
                            note = null
                            if (!wasListening) { stt.start(); haptics.play(Haptic.Click, settings.haptics) }
                            val t0 = SystemClock.uptimeMillis()
                            val up = waitForUpOrCancellation()
                            val held = SystemClock.uptimeMillis() - t0
                            if (up == null || wasListening || held >= HOLD_TO_TALK_MS) stt.stop()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Ico.Mic, if (listening) c.ink else c.text, 26.dp) }
            Box(Modifier.size(Space.xl))
            Meter(micLevel, reversed = false)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            PressBox(
                onClick = { if (text.isNotEmpty() && !typing) { stt.stop(); stt.clear(); note = null } },
                modifier = Modifier.weight(1f).height(48.dp),
                description = stringResource(R.string.clear),
                shape = CircleShape,
                background = c.s2,
            ) { Label(stringResource(R.string.clear), color = if (text.isEmpty() || typing) c.dim else c.text, style = Glidr.type.body) }
            PressBox(
                onClick = {
                    when {
                        typing -> engine.cancelTyping()
                        text.isBlank() -> Unit
                        !engine.connected -> { note = notConnected; haptics.play(Haptic.Reject, settings.haptics) }
                        else -> {
                            val skipped = engine.layoutUnsupported(text).length
                            stt.stop()
                            engine.typeText(text)
                            stt.clear()
                            note = if (skipped > 0) res.getQuantityString(R.plurals.voice_skipped, skipped, skipped) else null
                            haptics.play(Haptic.Confirm, settings.haptics)
                        }
                    }
                },
                modifier = Modifier.weight(2f).height(48.dp),
                description = stringResource(if (typing) R.string.stop_typing else R.string.send_to_pc),
                shape = CircleShape,
                background = if (typing) c.s3 else if (text.isBlank()) c.s3 else c.accent,
                border = false,
            ) {
                Label(
                    stringResource(if (typing) R.string.stop_typing else R.string.send_to_pc),
                    color = if (typing) c.text else if (text.isBlank()) c.dim else c.ink, style = Glidr.type.body,
                )
            }
        }
    }
}

@Composable
private fun Meter(level: Float, reversed: Boolean) {
    val c = Glidr.colors
    val shape = listOf(0.35f, 0.6f, 0.9f, 0.7f, 1f, 0.55f, 0.8f, 0.4f).let { if (reversed) it.reversed() else it }
    Row(Modifier.size(width = 56.dp, height = 40.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        for (s in shape) {
            Box(Modifier.size(3.dp, (4 + 32 * level * s).dp).clip(CircleShape).background(if (level > 0.02f) c.accent else c.s3))
        }
    }
}

/**
 * One key. Fires on press (not on release) so typing feels immediate; with [repeat] a held key fires again after
 * [REPEAT_DELAY_MS] and then every [REPEAT_MS] while [repeatWhile] stays true (so a dropped link does not buzz).
 * Pressed keys darken. Single-glyph function keys (arrows, shift, backspace) use the larger body size so they stay legible. The semantics make it a button for TalkBack, which cannot see the raw touch handling.
 */
@Composable
private fun Key(
    label: String, modifier: Modifier, desc: String? = null,
    on: Boolean = false, fn: Boolean = false, go: Boolean = false,
    repeat: Boolean = false, repeatWhile: () -> Boolean = { true }, press: () -> Unit,
) {
    val c = Glidr.colors
    val fire by rememberUpdatedState(press)
    val keepGoing by rememberUpdatedState(repeatWhile)
    var pressed by remember { mutableStateOf(false) }
    val bg = when {
        on || go -> c.accent
        pressed -> c.s3
        else -> c.s2
    }
    val fg = if (on || go) c.ink else if (fn) c.dim else c.text
    val name = desc ?: label
    Box(
        modifier.height(KEY_HEIGHT).clip(RoundedCornerShape(Radius.key)).background(bg)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = name
                onClick(name) { fire(); true }
            }
            .pointerInput(repeat) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    try {
                        fire()
                        if (!repeat) {
                            waitForUpOrCancellation()
                        } else {
                            var nextAt = SystemClock.uptimeMillis() + REPEAT_DELAY_MS
                            while (true) {
                                val remaining = nextAt - SystemClock.uptimeMillis()
                                val ev = if (remaining > 0) withTimeoutOrNull(remaining) { awaitPointerEvent() } else null
                                if (ev == null) {
                                    if (!keepGoing()) { waitForUpOrCancellation(); break }
                                    fire()
                                    nextAt = SystemClock.uptimeMillis() + REPEAT_MS
                                } else if (ev.changes.none { it.pressed } || ev.changes.any { it.isConsumed }) {
                                    break
                                }
                            }
                        }
                    } finally {
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) { Label(label, color = fg, style = if (fn && label.length > 1) Glidr.type.small else if (fn) Glidr.type.body.copy(fontSize = 18.sp) else Glidr.type.body, maxLines = 1) }
}
