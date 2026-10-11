package com.yashraj.glidr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import com.yashraj.glidr.companion.COMPANION_DOWNLOAD_URL
import com.yashraj.glidr.companion.CompanionState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yashraj.glidr.R
import com.yashraj.glidr.data.Settings
import com.yashraj.glidr.hid.KeyboardLayout
import com.yashraj.glidr.ui.theme.ACCENT_COLORS
import com.yashraj.glidr.ui.theme.ACCENT_NAMES
import com.yashraj.glidr.ui.theme.Glidr

@Composable
fun SettingsScreen(
    s: Settings, companion: CompanionState, pcConnected: Boolean, onInstallOnPc: (onDone: () -> Unit) -> Boolean, update: ((Settings) -> Settings) -> Unit, onForget: () -> Unit, onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Glidr.colors
    val ctx = LocalContext.current
    val haptics = remember { Haptics(ctx) }
    var showInstall by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showInstall) { showInstall = false }
    Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundIconButton(Ico.Back, stringResource(R.string.back), onBack)
            Label(stringResource(R.string.settings), color = c.text, style = Glidr.type.title.copy(fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp)))
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Section(stringResource(R.string.pointer)) {
                SliderRow(stringResource(R.string.sensitivity), "%.1f×".format(s.sensitivity)) {
                    Slider(s.sensitivity, 0.5f..3f, { v -> update { it.copy(sensitivity = v) } }, stringResource(R.string.sensitivity))
                }
                Pad {
                    Label(stringResource(R.string.acceleration), color = c.text, style = Glidr.type.body)
                    Box(Modifier.height(10.dp))
                    Segmented(listOf("Off", "Low", "Med", "High"), s.acceleration, { v -> update { it.copy(acceleration = v) } })
                    AccelCurve(s.sensitivity, s.acceleration)
                }
                SliderRow(stringResource(R.string.scroll_speed), "%.1f×".format(s.scrollSpeed)) {
                    Slider(s.scrollSpeed, 0.5f..3f, { v -> update { it.copy(scrollSpeed = v) } }, stringResource(R.string.scroll_speed))
                }
                SwitchRow(stringResource(R.string.natural_scroll), stringResource(R.string.natural_scroll_sub), s.naturalScroll) { v -> update { it.copy(naturalScroll = v) } }
                SwitchRow(stringResource(R.string.tap_to_click), stringResource(R.string.tap_to_click_sub), s.tapToClick) { v -> update { it.copy(tapToClick = v) } }
                SwitchRow(stringResource(R.string.left_handed), stringResource(R.string.left_handed_sub), s.leftHanded) { v -> update { it.copy(leftHanded = v) } }
            }
            Section(stringResource(R.string.keyboard)) {
                Pad {
                    Label(stringResource(R.string.layout), color = c.text, style = Glidr.type.body)
                    Label(stringResource(R.string.layout_sub), color = c.dim, style = Glidr.type.small)
                    Box(Modifier.height(10.dp))
                    val ids = KeyboardLayout.all.map { it.id }
                    Segmented(KeyboardLayout.all.map { it.label }, ids.indexOf(s.layoutId).coerceAtLeast(0), { i -> update { it.copy(layoutId = ids[i]) } })
                }
            }
            Section(stringResource(R.string.keep_awake)) {
                SwitchRow(stringResource(R.string.keep_awake_feature), stringResource(R.string.keep_awake_feature_sub), s.keepAwakeEnabled) { v -> update { it.copy(keepAwakeEnabled = v) } }
                if (s.keepAwakeEnabled) {
                    SwitchRow(stringResource(R.string.pause_touching), stringResource(R.string.pause_touching_sub), s.pauseWhileTouching) { v -> update { it.copy(pauseWhileTouching = v) } }
                    Pad {
                        Label(stringResource(R.string.nudge_style), color = c.text, style = Glidr.type.body)
                        Label(stringResource(R.string.nudge_style_sub), color = c.dim, style = Glidr.type.small)
                        Box(Modifier.height(10.dp))
                        Segmented(listOf(stringResource(R.string.nudge_subtle), stringResource(R.string.nudge_roam)), if (s.nudgeRoam) 1 else 0, { v -> update { it.copy(nudgeRoam = v == 1) } })
                    }
                    Pad {
                        Label(stringResource(R.string.nudge_every), color = c.text, style = Glidr.type.body)
                        Box(Modifier.height(10.dp))
                        Segmented(listOf("15 s", "30 s", "1 min", "2 min"), s.nudgeEvery, { v -> update { it.copy(nudgeEvery = v) } })
                    }
                    Pad {
                        Label(stringResource(R.string.auto_stop), color = c.text, style = Glidr.type.body)
                        Box(Modifier.height(10.dp))
                        Segmented(listOf(stringResource(R.string.never), "30 min", "1 h", "2 h"), s.autoStop, { v -> update { it.copy(autoStop = v) } })
                    }
                }
            }
            Section(stringResource(R.string.haptics)) {
                Pad {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Label(stringResource(R.string.intensity), color = c.text, style = Glidr.type.body)
                        PressBox({ haptics.play(Haptic.Confirm, s.haptics) }, Modifier.height(48.dp), stringResource(R.string.feel_it), RoundedCornerShape(24.dp)) {
                            Label(stringResource(R.string.feel_it), Modifier.padding(horizontal = 18.dp))
                        }
                    }
                    Box(Modifier.height(10.dp))
                    Segmented(listOf(stringResource(R.string.off), "Light", "Medium", "Strong"), s.haptics, { v -> update { it.copy(haptics = v) }; haptics.play(Haptic.Click, v) })
                }
                SwitchRow(stringResource(R.string.hap_scroll), stringResource(R.string.hap_scroll_sub), s.hapticScroll) { v -> update { it.copy(hapticScroll = v) } }
                SwitchRow(stringResource(R.string.hap_keys), stringResource(R.string.hap_keys_sub), s.hapticKeys) { v -> update { it.copy(hapticKeys = v) } }
            }
            Section(stringResource(R.string.appearance)) {
                Pad {
                    Label(stringResource(R.string.theme), color = c.text, style = Glidr.type.body)
                    Box(Modifier.height(10.dp))
                    Segmented(listOf("Midnight", "Paper"), if (s.lightTheme) 1 else 0, { v -> update { it.copy(lightTheme = v == 1) } })
                }
                Pad {
                    Label(stringResource(R.string.accent), color = c.text, style = Glidr.type.body)
                    Box(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ACCENT_COLORS.forEachIndexed { i, col ->
                            Box(
                                Modifier.size(48.dp).clip(CircleShape).background(col)
                                    .then(if (i == s.accent) Modifier.border(3.dp, c.text, CircleShape) else Modifier)
                                    .clickable { update { it.copy(accent = i) } }
                                    .androidSem(ACCENT_NAMES[i]),
                            )
                        }
                    }
                }
                SwitchRow(stringResource(R.string.reduce_motion), stringResource(R.string.reduce_motion_sub), s.reduceMotion) { v -> update { it.copy(reduceMotion = v) } }
            }
            Section(stringResource(R.string.connection)) {
                SwitchRow(stringResource(R.string.auto_reconnect), stringResource(R.string.auto_reconnect_sub), s.autoReconnect) { v -> update { it.copy(autoReconnect = v) } }
                Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Label(stringResource(R.string.forget_pc), color = c.text, style = Glidr.type.body)
                        Label(stringResource(R.string.forget_pc_sub), color = c.dim, style = Glidr.type.small)
                    }
                    PressBox(onForget, Modifier.height(48.dp), stringResource(R.string.forget_pc), RoundedCornerShape(24.dp)) {
                        Label(stringResource(R.string.forget), Modifier.padding(horizontal = 18.dp))
                    }
                }
            }
            Section(stringResource(R.string.sync_media)) {
                Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Label(stringResource(R.string.install_pc_app), color = c.text, style = Glidr.type.body)
                        Label(
                            stringResource(if (companion == CompanionState.Connected) R.string.pc_app_connected else R.string.install_pc_app_sub),
                            color = if (companion == CompanionState.Connected) c.accentText else c.dim, style = Glidr.type.small,
                        )
                    }
                    PressBox({ showInstall = true }, Modifier.height(48.dp), stringResource(R.string.install_pc_app), RoundedCornerShape(24.dp)) {
                        Label(stringResource(R.string.install), Modifier.padding(horizontal = 18.dp))
                    }
                }
            }
            Section(stringResource(R.string.about)) {
                Pad {
                    Label(stringResource(R.string.privacy_note), color = c.dim, style = Glidr.type.small)
                }
            }
        }
    }
    if (showInstall) InstallPcAppDialog(pcConnected, onInstallOnPc) { showInstall = false }
    }
}

@Composable
private fun InstallPcAppDialog(pcConnected: Boolean, onInstallOnPc: (onDone: () -> Unit) -> Boolean, onClose: () -> Unit) {
    val c = Glidr.colors
    val hosted = COMPANION_DOWNLOAD_URL.isNotBlank()
    var phase by rememberSaveable { mutableStateOf(InstallPhase.Idle) }
    val steps = listOf(R.string.install_step_1, R.string.install_step_2, R.string.install_step_3, R.string.install_step_4)
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.s1).border(1.dp, c.line, RoundedCornerShape(28.dp))
                .clickable(enabled = false) {}.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Label(stringResource(R.string.install_pc_app), color = c.text, style = Glidr.type.title.copy(fontSize = TextUnit(22f, TextUnitType.Sp)))
            if (hosted) {
                Label(stringResource(R.string.auto_install_intro), color = c.dim, style = Glidr.type.small)
                steps.forEachIndexed { i, res ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                            Label("${i + 1}", color = c.ink, style = Glidr.type.small)
                        }
                        Label(stringResource(res), color = c.text, style = Glidr.type.body, modifier = Modifier.weight(1f))
                    }
                }
                when (phase) {
                    InstallPhase.Typing -> Label(stringResource(R.string.auto_install_typing), color = c.accentText, style = Glidr.type.body)
                    InstallPhase.Sent -> Label(stringResource(R.string.auto_install_sent), color = c.accentText, style = Glidr.type.body)
                    InstallPhase.Failed -> Label(stringResource(R.string.auto_install_failed), color = c.dim, style = Glidr.type.small)
                    InstallPhase.Idle -> if (!pcConnected) Label(stringResource(R.string.auto_install_need_pc), color = c.dim, style = Glidr.type.small)
                }
                if (phase == InstallPhase.Idle || phase == InstallPhase.Failed) {
                    PressBox(
                        { if (pcConnected) phase = if (onInstallOnPc { phase = InstallPhase.Sent }) InstallPhase.Typing else InstallPhase.Failed },
                        Modifier.fillMaxWidth().height(52.dp), null, background = if (pcConnected) c.accent else c.s2, border = false,
                    ) { Label(stringResource(R.string.install_on_my_pc), color = if (pcConnected) c.ink else c.dim) }
                }
            } else {
                Label(stringResource(R.string.auto_install_not_ready), color = c.dim, style = Glidr.type.body)
            }
            PressBox(onClose, Modifier.fillMaxWidth().height(52.dp)) { Label(stringResource(R.string.done)) }
        }
    }
}

private enum class InstallPhase { Idle, Typing, Sent, Failed }

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val c = Glidr.colors
    Label(title.uppercase(), Modifier.padding(start = 4.dp, top = 24.dp, bottom = 10.dp), c.dim, Glidr.type.small)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.s1).border(1.dp, c.line, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp)) { content() }
}

@Composable
private fun Pad(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) { content() }
}

@Composable
private fun SliderRow(title: String, value: String, slider: @Composable () -> Unit) {
    val c = Glidr.colors
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(title, color = c.text, style = Glidr.type.body)
            Label(value, color = c.accentText, style = Glidr.type.mono)
        }
        slider()
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val c = Glidr.colors
    Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Label(title, color = c.text, style = Glidr.type.body)
            Label(sub, color = c.dim, style = Glidr.type.small)
        }
        Toggle(on, onChange, title)
    }
}

private fun Modifier.androidSem(d: String) = this.then(
    Modifier.semantics { contentDescription = d },
)

/** Preview of the speed-in, speed-out curve for the current sensitivity and acceleration. */
@Composable
private fun AccelCurve(sensitivity: Float, accel: Int) {
    val c = Glidr.colors
    val k = floatArrayOf(0f, 0.6f, 1.2f, 2.0f)[accel.coerceIn(0, 3)]
    Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val w = size.width
            val h = size.height
            val path = androidx.compose.ui.graphics.Path()
            for (i in 0..24) {
                val x = i / 24f
                val y = (sensitivity * x * (1 + k * x) / 6f).coerceIn(0f, 1f)
                val px = 6f + x * (w - 12f)
                val py = h - 6f - y * (h - 12f)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawLine(c.s3, androidx.compose.ui.geometry.Offset(6f, h - 6f), androidx.compose.ui.geometry.Offset(w - 6f, 6f), 3f)
            drawPath(path, c.accentText, style = androidx.compose.ui.graphics.drawscope.Stroke(6f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Label(stringResource(R.string.curve_slow).uppercase(), color = c.dim, style = Glidr.type.small)
        Label(stringResource(R.string.curve_fast).uppercase(), color = c.dim, style = Glidr.type.small)
    }
}
