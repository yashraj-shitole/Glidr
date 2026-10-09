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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yashraj.glidr.R
import com.yashraj.glidr.hid.HidEngine
import com.yashraj.glidr.hid.HidReports
import com.yashraj.glidr.hid.Keys
import com.yashraj.glidr.ui.Haptic
import com.yashraj.glidr.ui.Haptics
import com.yashraj.glidr.ui.Label
import com.yashraj.glidr.ui.Segmented
import com.yashraj.glidr.ui.theme.Glidr

private val ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

private class Shortcut(val name: String, val keys: List<String>, val usage: Int, val mods: Int)

private val SHORTCUTS = listOf(
    Shortcut("Copy", listOf("Ctrl", "C"), 0x06, HidReports.MOD_CTRL),
    Shortcut("Paste", listOf("Ctrl", "V"), 0x19, HidReports.MOD_CTRL),
    Shortcut("Cut", listOf("Ctrl", "X"), 0x1B, HidReports.MOD_CTRL),
    Shortcut("Undo", listOf("Ctrl", "Z"), 0x1D, HidReports.MOD_CTRL),
    Shortcut("Select all", listOf("Ctrl", "A"), 0x04, HidReports.MOD_CTRL),
    Shortcut("Save", listOf("Ctrl", "S"), 0x16, HidReports.MOD_CTRL),
    Shortcut("Switch app", listOf("Alt", "Tab"), Keys.TAB, HidReports.MOD_ALT),
    Shortcut("Task view", listOf("Win", "Tab"), Keys.TAB, HidReports.MOD_GUI),
    Shortcut("Desktop", listOf("Win", "D"), Keys.D, HidReports.MOD_GUI),
    Shortcut("Lock", listOf("Win", "L"), Keys.L, HidReports.MOD_GUI),
    Shortcut("Close window", listOf("Alt", "F4"), Keys.F4, HidReports.MOD_ALT),
    Shortcut("Screenshot", listOf("Win", "Shift", "S"), 0x16, HidReports.MOD_GUI or HidReports.MOD_SHIFT),
)

@Composable
fun KeyboardSheet(engine: HidEngine, haptic: Int, modifier: Modifier = Modifier) {
    val c = Glidr.colors
    val ctx = LocalContext.current
    val haptics = remember { Haptics(ctx) }
    var tab by remember { mutableIntStateOf(0) }
    var mods by remember { mutableIntStateOf(0) }
    var shiftLatched by remember { mutableStateOf(false) }

    fun tap(usage: Int, extra: Int = 0) {
        engine.pressKey(usage, mods or extra)
        haptics.play(Haptic.Click, haptic)
        mods = 0
        shiftLatched = false
    }

    fun typeChar(ch: Char) {
        val stroke = engine.layout.strokeFor(if (shiftLatched) ch.uppercaseChar() else ch) ?: return
        tap(stroke.usage, stroke.modifiers)
    }

    fun latch(bit: Int) {
        mods = mods xor bit
        haptics.play(Haptic.Tick, haptic)
    }

    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(c.s1)
            .border(1.dp, c.line, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Segmented(listOf(stringResource(R.string.tab_keys), stringResource(R.string.tab_shortcuts)), tab, { tab = it })
        if (tab == 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Key("Esc", Modifier.weight(1f), fn = true) { tap(Keys.ESC) }
                Key("Tab", Modifier.weight(1f), fn = true) { tap(Keys.TAB) }
                Key("Ctrl", Modifier.weight(1f), on = mods and HidReports.MOD_CTRL != 0, fn = true) { latch(HidReports.MOD_CTRL) }
                Key("Alt", Modifier.weight(1f), on = mods and HidReports.MOD_ALT != 0, fn = true) { latch(HidReports.MOD_ALT) }
                Key("Win", Modifier.weight(1f), on = mods and HidReports.MOD_GUI != 0, fn = true) { latch(HidReports.MOD_GUI) }
                Key("←", Modifier.weight(1f), fn = true) { tap(Keys.LEFT) }
                Key("↓", Modifier.weight(1f), fn = true) { tap(Keys.DOWN) }
                Key("↑", Modifier.weight(1f), fn = true) { tap(Keys.UP) }
                Key("→", Modifier.weight(1f), fn = true) { tap(Keys.RIGHT) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (ch in "1234567890") Key(ch.toString(), Modifier.weight(1f)) { typeChar(ch) }
            }
            for ((idx, row) in ROWS.withIndex()) {
                Row(Modifier.padding(horizontal = if (idx == 1) 16.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (idx == 2) Key("⇧", Modifier.weight(1.5f), on = shiftLatched, fn = true) { shiftLatched = !shiftLatched; haptics.play(Haptic.Tick, haptic) }
                    for (ch in row) Key(if (shiftLatched) ch.uppercase() else ch.toString(), Modifier.weight(1f)) { typeChar(ch) }
                    if (idx == 2) Key("⌫", Modifier.weight(1.5f), fn = true) { tap(Keys.BACKSPACE) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Key(",", Modifier.weight(1f)) { typeChar(',') }
                Key(stringResource(R.string.space), Modifier.weight(4.4f)) { tap(Keys.SPACE) }
                Key(".", Modifier.weight(1f)) { typeChar('.') }
                Key("↵", Modifier.weight(1.6f), go = true) { tap(Keys.ENTER) }
            }
        } else {
            for (rowItems in SHORTCUTS.chunked(3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (s in rowItems) {
                        Column(
                            Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(18.dp)).background(c.s2)
                                .border(1.dp, c.line, RoundedCornerShape(18.dp))
                                .clickable { engine.pressKey(s.usage, s.mods); haptics.play(Haptic.Confirm, haptic) }
                                .padding(10.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Label(s.name)
                            Label(s.keys.joinToString(" + "), color = c.dim, style = Glidr.type.mono)
                        }
                    }
                }
            }
        }
    }
}

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
