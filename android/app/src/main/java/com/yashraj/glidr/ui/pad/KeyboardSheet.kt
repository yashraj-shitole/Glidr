package com.yashraj.glidr.ui.pad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.yashraj.glidr.voice.SttEngine
import com.yashraj.glidr.voice.SttState

private val LETTER_ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
private val SYMBOL_ROWS = listOf("1234567890", "@#$%&-+()/", "*\":;!?=_^'", "[]{}<>\\|~`")

/**
 * A shortcut is either a fixed key ([usage]) or a [letter]. Windows matches letter shortcuts by the character the
 * PC's layout puts on the key, so letters go through the layout table (on DE, Z sits where US has Y).
 */
private class Shortcut(val name: String, val keys: List<String>, val mods: Int, val usage: Int = 0, val letter: Char? = null) {
    fun usageFor(layout: KeyboardLayout): Int = letter?.let { layout.strokeFor(it)?.usage } ?: usage
}

private val SHORTCUTS = listOf(
    Shortcut("Copy", listOf("Ctrl", "C"), HidReports.MOD_CTRL, letter = 'c'),
    Shortcut("Paste", listOf("Ctrl", "V"), HidReports.MOD_CTRL, letter = 'v'),
    Shortcut("Cut", listOf("Ctrl", "X"), HidReports.MOD_CTRL, letter = 'x'),
    Shortcut("Undo", listOf("Ctrl", "Z"), HidReports.MOD_CTRL, letter = 'z'),
    Shortcut("Select all", listOf("Ctrl", "A"), HidReports.MOD_CTRL, letter = 'a'),
    Shortcut("Save", listOf("Ctrl", "S"), HidReports.MOD_CTRL, letter = 's'),
    Shortcut("Switch app", listOf("Alt", "Tab"), HidReports.MOD_ALT, usage = Keys.TAB),
    Shortcut("Task view", listOf("Win", "Tab"), HidReports.MOD_GUI, usage = Keys.TAB),
    Shortcut("Desktop", listOf("Win", "D"), HidReports.MOD_GUI, letter = 'd'),
    Shortcut("Lock", listOf("Win", "L"), HidReports.MOD_GUI, letter = 'l'),
    Shortcut("Close window", listOf("Alt", "F4"), HidReports.MOD_ALT, usage = Keys.F4),
    Shortcut("Screenshot", listOf("Win", "Shift", "S"), HidReports.MOD_GUI or HidReports.MOD_SHIFT, letter = 's'),
)

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
    var tab by remember { mutableIntStateOf(0) }
    var mods by remember { mutableIntStateOf(0) }
    var shiftLatched by remember { mutableStateOf(false) }
    var symbols by remember { mutableStateOf(false) }

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

    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(c.s1)
            .border(1.dp, c.line, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Segmented(
            listOf(stringResource(R.string.tab_keys), stringResource(R.string.tab_shortcuts), stringResource(R.string.tab_voice)),
            tab, { tab = it },
        )
        when (tab) {
            0 -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Key("Esc", Modifier.weight(1f), fn = true) { send(Keys.ESC) }
                    Key("Tab", Modifier.weight(1f), fn = true) { send(Keys.TAB) }
                    Key("Ctrl", Modifier.weight(1f), on = mods and HidReports.MOD_CTRL != 0, fn = true) { latch(HidReports.MOD_CTRL) }
                    Key("Alt", Modifier.weight(1f), on = mods and HidReports.MOD_ALT != 0, fn = true) { latch(HidReports.MOD_ALT) }
                    Key("Win", Modifier.weight(1f), on = mods and HidReports.MOD_GUI != 0, fn = true) { latch(HidReports.MOD_GUI) }
                    Key("←", Modifier.weight(1f), fn = true) { send(Keys.LEFT) }
                    Key("↓", Modifier.weight(1f), fn = true) { send(Keys.DOWN) }
                    Key("↑", Modifier.weight(1f), fn = true) { send(Keys.UP) }
                    Key("→", Modifier.weight(1f), fn = true) { send(Keys.RIGHT) }
                }
                if (symbols) {
                    for (row in SYMBOL_ROWS) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (ch in row) Key(ch.toString(), Modifier.weight(1f)) { typeChar(ch) }
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (ch in "1234567890") Key(ch.toString(), Modifier.weight(1f)) { typeChar(ch) }
                    }
                    for ((idx, row) in LETTER_ROWS.withIndex()) {
                        Row(Modifier.padding(horizontal = if (idx == 1) 16.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (idx == 2) Key("⇧", Modifier.weight(1.5f), on = shiftLatched, fn = true) { shiftLatched = !shiftLatched; haptics.play(Haptic.Tick, level) }
                            for (ch in row) Key(if (shiftLatched) ch.uppercase() else ch.toString(), Modifier.weight(1f)) { typeChar(ch) }
                            if (idx == 2) Key("⌫", Modifier.weight(1.5f), fn = true) { send(Keys.BACKSPACE) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Key(if (symbols) "ABC" else "123", Modifier.weight(1.4f), fn = true) { symbols = !symbols; haptics.play(Haptic.Tick, level) }
                    if (!symbols) Key(",", Modifier.weight(1f)) { typeChar(',') } else Key("⌫", Modifier.weight(1f), fn = true) { send(Keys.BACKSPACE) }
                    Key(stringResource(R.string.space), Modifier.weight(3.6f)) { send(Keys.SPACE) }
                    if (!symbols) Key(".", Modifier.weight(1f)) { typeChar('.') } else Key(".", Modifier.weight(1f)) { typeChar('.') }
                    Key("↵", Modifier.weight(1.6f), go = true) { send(Keys.ENTER) }
                }
            }
            1 -> for (rowItems in SHORTCUTS.chunked(3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (s in rowItems) {
                        Column(
                            Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(18.dp)).background(c.s2)
                                .border(1.dp, c.line, RoundedCornerShape(18.dp))
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
                            Label(s.name)
                            Label(s.keys.joinToString(" + "), color = c.dim, style = Glidr.type.mono)
                        }
                    }
                }
            }
            else -> VoicePanel(engine, settings, stt, hasMic, onRequestMic, haptics)
        }
    }
}

@Composable
private fun VoicePanel(engine: HidEngine, settings: Settings, stt: SttEngine, hasMic: Boolean, onRequestMic: () -> Unit, haptics: Haptics) {
    val c = Glidr.colors
    val state by stt.state.collectAsStateWithLifecycle()
    val text by stt.text.collectAsStateWithLifecycle()
    val micLevel by stt.level.collectAsStateWithLifecycle()
    var note by remember { mutableStateOf<String?>(null) }
    val listening = state == SttState.Listening
    val notConnected = stringResource(R.string.not_connected)
    val res = LocalContext.current.resources

    val hint = when {
        state == SttState.Unavailable -> stringResource(R.string.voice_unavailable)
        state == SttState.Error -> stringResource(R.string.voice_error)
        note != null -> note!!
        text.isEmpty() -> stringResource(if (listening) R.string.voice_listening else R.string.voice_tap)
        else -> text
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 68.dp, max = 96.dp).clip(RoundedCornerShape(18.dp)).background(c.s2).border(1.dp, c.line, RoundedCornerShape(18.dp)).padding(12.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Label(hint, color = if (text.isEmpty() || note != null || state == SttState.Error) c.dim else c.text, style = Glidr.type.body)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stt.language)
            Chip(stringResource(R.string.auto_punct))
            Chip(engine.layout.label)
        }
        Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Meter(micLevel, reversed = true)
            Box(Modifier.size(24.dp))
            PressBox(
                onClick = {
                    note = null
                    when {
                        state == SttState.Unavailable -> Unit
                        !hasMic -> onRequestMic()
                        listening -> stt.stop()
                        else -> { stt.start(); haptics.play(Haptic.Click, settings.haptics) }
                    }
                },
                modifier = Modifier.size(64.dp).scale(1f + 0.12f * micLevel),
                description = stringResource(R.string.dictate),
                shape = CircleShape,
                background = if (listening) c.accent else c.s3,
                border = false,
            ) { Icon(Ico.Mic, if (listening) c.ink else c.text, 26.dp) }
            Box(Modifier.size(24.dp))
            Meter(micLevel, reversed = false)
        }
        PressBox(
            onClick = {
                when {
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
            modifier = Modifier.fillMaxWidth().height(48.dp),
            description = stringResource(R.string.send_to_pc),
            shape = CircleShape,
            background = if (text.isBlank()) c.s3 else c.accent,
            border = false,
        ) { Label(stringResource(R.string.send_to_pc), color = if (text.isBlank()) c.dim else c.ink, style = Glidr.type.body) }
    }
}

@Composable
private fun Chip(text: String) {
    val c = Glidr.colors
    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(c.s2).border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(horizontal = 11.dp, vertical = 6.dp)) {
        Label(text, color = c.dim, style = Glidr.type.small)
    }
}

@Composable
private fun Meter(level: Float, reversed: Boolean) {
    val c = Glidr.colors
    val shape = listOf(0.35f, 0.6f, 0.9f, 0.7f, 1f, 0.55f, 0.8f, 0.4f).let { if (reversed) it.reversed() else it }
    Row(Modifier.width96(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        for (s in shape) {
            Box(Modifier.size(3.dp, (4 + 32 * level * s).dp).clip(CircleShape).background(if (level > 0.02f) c.accent else c.s3))
        }
    }
}

private fun Modifier.width96() = this.then(Modifier.size(width = 56.dp, height = 40.dp))

@Composable
private fun Key(label: String, modifier: Modifier, on: Boolean = false, fn: Boolean = false, go: Boolean = false, onClick: () -> Unit) {
    val c = Glidr.colors
    val bg = if (on || go) c.accent else c.s2
    val fg = if (on || go) c.ink else if (fn) c.dim else c.text
    Box(
        modifier.height(if (fn) 40.dp else 44.dp).clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Label(label, color = fg, style = if (fn) Glidr.type.small else Glidr.type.body) }
}
