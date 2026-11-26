package com.yashraj.glidr.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yashraj.glidr.R
import com.yashraj.glidr.companion.COMPANION_DOWNLOAD_URL
import com.yashraj.glidr.companion.CompanionState
import com.yashraj.glidr.data.Settings
import com.yashraj.glidr.hid.KeyboardLayout
import com.yashraj.glidr.input.MotionPipeline
import com.yashraj.glidr.ui.theme.ACCENT_COLORS
import com.yashraj.glidr.ui.theme.Glidr
import com.yashraj.glidr.ui.theme.MAX_CONTENT_WIDTH
import com.yashraj.glidr.ui.theme.MAX_DIALOG_WIDTH
import com.yashraj.glidr.ui.theme.Radius
import com.yashraj.glidr.ui.theme.Space
import com.yashraj.glidr.update.Release
import com.yashraj.glidr.update.UpdateState

private val ACCENT_NAME_RES = listOf(R.string.accent_volt, R.string.accent_ember, R.string.accent_sky, R.string.accent_rose)

@Composable
fun SettingsScreen(
    s: Settings, companion: CompanionState, pcConnected: Boolean, onInstallOnPc: (onDone: () -> Unit) -> Boolean, update: ((Settings) -> Settings) -> Unit, onForget: () -> Unit, onBack: () -> Unit,
    appVersion: String, updateState: UpdateState, onCheckUpdate: () -> Unit, onUpdate: (Release) -> Unit,
    onReset: () -> Unit, onShowWelcome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Glidr.colors
    val ctx = LocalContext.current
    val haptics = remember { Haptics(ctx) }
    var showInstall by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf<Confirm?>(null) }
    BackHandler(enabled = showInstall || confirm != null) { if (confirm != null) confirm = null else showInstall = false }
    // A detent tick whenever a slider moves to a new step.
    fun tick() = haptics.play(Haptic.Tick, s.haptics)
    Box(modifier.fillMaxSize()) {
        // On a wide screen (landscape, tablet) the list stays a readable column instead of stretching edge to edge.
        Column(Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxSize().align(Alignment.TopCenter)) {
            Row(Modifier.padding(horizontal = Space.lg, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                RoundIconButton(Ico.Back, stringResource(R.string.back), onBack)
                Label(stringResource(R.string.settings), color = c.text, style = Glidr.type.title)
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.lg).padding(bottom = 32.dp)) {
                (updateState as? UpdateState.Available)?.let { UpdateBanner(it.release, onUpdate) }
                Section(stringResource(R.string.pointer)) {
                    SliderRow(stringResource(R.string.sensitivity), "%.1f×".format(s.sensitivity)) {
                        Slider(s.sensitivity, 0.5f..3f, { v -> if (v != s.sensitivity) tick(); update { it.copy(sensitivity = v) } }, stringResource(R.string.sensitivity), tickEvery = 0.5f)
                    }
                    Pad {
                        Label(stringResource(R.string.acceleration), color = c.text, style = Glidr.type.body)
                        Gap(Space.md)
                        Segmented(
                            listOf(stringResource(R.string.off), stringResource(R.string.accel_low), stringResource(R.string.accel_med), stringResource(R.string.accel_high)),
                            s.acceleration, { v -> update { it.copy(acceleration = v) } },
                        )
                        AccelCurve(s.sensitivity, s.acceleration)
                        Gap(Space.md)
                        TryPad(s.sensitivity, s.acceleration)
                    }
                    SliderRow(stringResource(R.string.scroll_speed), "%.1f×".format(s.scrollSpeed)) {
                        Slider(s.scrollSpeed, 0.5f..3f, { v -> if (v != s.scrollSpeed) tick(); update { it.copy(scrollSpeed = v) } }, stringResource(R.string.scroll_speed), tickEvery = 0.5f)
                    }
                    SwitchRow(stringResource(R.string.natural_scroll), stringResource(R.string.natural_scroll_sub), s.naturalScroll) { v -> update { it.copy(naturalScroll = v) } }
                    SwitchRow(stringResource(R.string.fling_scroll), stringResource(R.string.fling_scroll_sub), s.flingScroll) { v -> update { it.copy(flingScroll = v) } }
                    SwitchRow(stringResource(R.string.tap_to_click), stringResource(R.string.tap_to_click_sub), s.tapToClick) { v -> update { it.copy(tapToClick = v) } }
                    SwitchRow(stringResource(R.string.quick_keys), stringResource(R.string.quick_keys_sub), s.quickKeys) { v -> update { it.copy(quickKeys = v) } }
                    SwitchRow(stringResource(R.string.left_handed), stringResource(R.string.left_handed_sub), s.leftHanded) { v -> update { it.copy(leftHanded = v) } }
                }
                Section(stringResource(R.string.keyboard)) {
                    Pad {
                        Label(stringResource(R.string.layout), color = c.text, style = Glidr.type.body)
                        Label(stringResource(R.string.layout_sub), color = c.dim, style = Glidr.type.small)
                        Gap(Space.md)
                        val ids = KeyboardLayout.all.map { it.id }
                        Segmented(KeyboardLayout.all.map { it.label }, ids.indexOf(s.layoutId).coerceAtLeast(0), { i -> update { it.copy(layoutId = ids[i]) } })
                    }
                }
                Section(stringResource(R.string.keep_awake)) {
                    SwitchRow(stringResource(R.string.keep_awake_feature), stringResource(R.string.keep_awake_feature_sub), s.keepAwakeEnabled) { v -> update { it.copy(keepAwakeEnabled = v) } }
                    Reveal(s.keepAwakeEnabled) { Column {
                        SwitchRow(stringResource(R.string.pause_touching), stringResource(R.string.pause_touching_sub), s.pauseWhileTouching) { v -> update { it.copy(pauseWhileTouching = v) } }
                        Pad {
                            Label(stringResource(R.string.nudge_style), color = c.text, style = Glidr.type.body)
                            Label(stringResource(R.string.nudge_style_sub), color = c.dim, style = Glidr.type.small)
                            Gap(Space.md)
                            Segmented(listOf(stringResource(R.string.nudge_subtle), stringResource(R.string.nudge_roam)), if (s.nudgeRoam) 1 else 0, { v -> update { it.copy(nudgeRoam = v == 1) } })
                        }
                        Pad {
                            Label(stringResource(R.string.nudge_every), color = c.text, style = Glidr.type.body)
                            Gap(Space.md)
                            Segmented(
                                listOf(stringResource(R.string.every_15s), stringResource(R.string.every_30s), stringResource(R.string.every_1m), stringResource(R.string.every_2m)),
                                s.nudgeEvery, { v -> update { it.copy(nudgeEvery = v) } },
                            )
                        }
                        Pad {
                            Label(stringResource(R.string.auto_stop), color = c.text, style = Glidr.type.body)
                            Gap(Space.md)
                            Segmented(
                                listOf(stringResource(R.string.never), stringResource(R.string.stop_30m), stringResource(R.string.stop_1h), stringResource(R.string.stop_2h)),
                                s.autoStop, { v -> update { it.copy(autoStop = v) } },
                            )
                        }
                    } }
                }
                Section(stringResource(R.string.haptics)) {
                    Pad {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Label(stringResource(R.string.intensity), color = c.text, style = Glidr.type.body)
                            PressBox({ haptics.play(Haptic.Confirm, s.haptics) }, Modifier.height(48.dp), stringResource(R.string.feel_it), RoundedCornerShape(Radius.card)) {
                                Label(stringResource(R.string.feel_it), Modifier.padding(horizontal = 20.dp))
                            }
                        }
                        Gap(Space.md)
                        Segmented(
                            listOf(stringResource(R.string.off), stringResource(R.string.hap_light), stringResource(R.string.hap_medium), stringResource(R.string.hap_strong)),
                            s.haptics, { v -> update { it.copy(haptics = v) }; haptics.play(Haptic.Click, v) },
                        )
                    }
                    SwitchRow(stringResource(R.string.hap_scroll), stringResource(R.string.hap_scroll_sub), s.hapticScroll) { v -> update { it.copy(hapticScroll = v) } }
                    SwitchRow(stringResource(R.string.hap_keys), stringResource(R.string.hap_keys_sub), s.hapticKeys) { v -> update { it.copy(hapticKeys = v) } }
                }
                Section(stringResource(R.string.appearance)) {
                    Pad {
                        Label(stringResource(R.string.theme), color = c.text, style = Glidr.type.body)
                        Gap(Space.md)
                        Segmented(listOf(stringResource(R.string.theme_midnight), stringResource(R.string.theme_paper)), if (s.lightTheme) 1 else 0, { v -> update { it.copy(lightTheme = v == 1) } })
                    }
                    Pad {
                        Label(stringResource(R.string.accent), color = c.text, style = Glidr.type.body)
                        Gap(Space.md)
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                            ACCENT_COLORS.forEachIndexed { i, col ->
                                val name = stringResource(ACCENT_NAME_RES[i])
                                val on = i == s.accent
                                val src = remember { MutableInteractionSource() }
                                val ring by animateColorAsState(if (on) c.text else Color.Transparent, settleSpec(), label = "ring")
                                Box(
                                    Modifier.size(48.dp).popOnChange(on, from = 0.85f).pressScale(src).clip(CircleShape).background(col)
                                        .border(3.dp, ring, CircleShape)
                                        .selectable(selected = on, interactionSource = src, indication = LocalIndication.current, role = Role.RadioButton, onClick = { update { it.copy(accent = i) } })
                                        .semantics { contentDescription = name },
                                )
                            }
                        }
                    }
                    SwitchRow(stringResource(R.string.reduce_motion), stringResource(R.string.reduce_motion_sub), s.reduceMotion) { v -> update { it.copy(reduceMotion = v) } }
                }
                Section(stringResource(R.string.connection)) {
                    SwitchRow(stringResource(R.string.auto_reconnect), stringResource(R.string.auto_reconnect_sub), s.autoReconnect) { v -> update { it.copy(autoReconnect = v) } }
                    ActionRow(stringResource(R.string.forget_pc), stringResource(R.string.forget_pc_sub), stringResource(R.string.forget)) { confirm = Confirm.Forget }
                }
                Section(stringResource(R.string.sync_media)) {
                    ActionRow(
                        stringResource(R.string.install_pc_app),
                        stringResource(if (companion == CompanionState.Connected) R.string.pc_app_connected else R.string.install_pc_app_sub),
                        stringResource(R.string.install), subAccent = companion == CompanionState.Connected,
                    ) { showInstall = true }
                }
                Section(stringResource(R.string.updates)) {
                    UpdateRow(appVersion, updateState, onCheckUpdate)
                }
                Section(stringResource(R.string.how_to_use)) {
                    Pad { GestureList() }
                    ActionRow(stringResource(R.string.replay_welcome), stringResource(R.string.replay_welcome_sub), stringResource(R.string.show)) { onShowWelcome() }
                }
                Section(stringResource(R.string.about)) {
                    ActionRow(stringResource(R.string.reset_settings), stringResource(R.string.reset_settings_sub), stringResource(R.string.reset)) { confirm = Confirm.Reset }
                    Pad {
                        Label(stringResource(R.string.privacy_note), color = c.dim, style = Glidr.type.small)
                    }
                }
            }
        }
        if (showInstall) InstallPcAppDialog(pcConnected, onInstallOnPc) { showInstall = false }
        when (confirm) {
            Confirm.Forget -> ConfirmDialog(
                stringResource(R.string.forget_pc), stringResource(R.string.forget_confirm), stringResource(R.string.forget),
                onConfirm = { onForget(); confirm = null }, onClose = { confirm = null },
            )
            Confirm.Reset -> ConfirmDialog(
                stringResource(R.string.reset_settings), stringResource(R.string.reset_confirm), stringResource(R.string.reset),
                onConfirm = { onReset(); confirm = null }, onClose = { confirm = null },
            )
            null -> Unit
        }
    }
}

private enum class Confirm { Forget, Reset }

/** Centered confirmation card on a scrim. Cancel is the quiet choice, the action carries the accent. */
@Composable
private fun ConfirmDialog(title: String, body: String, action: String, onConfirm: () -> Unit, onClose: () -> Unit) {
    val c = Glidr.colors
    Box(Modifier.fillMaxSize().appear().background(Scrim).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(Space.xl).widthIn(max = MAX_DIALOG_WIDTH).fillMaxWidth().appear(y = Space.lg, scale = 0.92f, fade = false)
                .clip(RoundedCornerShape(Radius.sheet)).background(c.s1).border(1.dp, c.line, RoundedCornerShape(Radius.sheet))
                .clickable(enabled = false) {}.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Label(title, color = c.text, style = Glidr.type.heading)
            Label(body, color = c.dim, style = Glidr.type.body)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PressBox(onClose, Modifier.weight(1f).height(52.dp)) { Label(stringResource(R.string.cancel)) }
                PressBox(onConfirm, Modifier.weight(1f).height(52.dp), null, background = c.accent, border = false) { Label(action, color = c.ink) }
            }
        }
    }
}

@Composable
private fun InstallPcAppDialog(pcConnected: Boolean, onInstallOnPc: (onDone: () -> Unit) -> Boolean, onClose: () -> Unit) {
    val c = Glidr.colors
    val hosted = COMPANION_DOWNLOAD_URL.isNotBlank()
    var phase by rememberSaveable { mutableStateOf(InstallPhase.Idle) }
    val steps = listOf(R.string.install_step_1, R.string.install_step_2, R.string.install_step_3, R.string.install_step_4)
    Box(Modifier.fillMaxSize().appear().background(Scrim).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(Space.xl).widthIn(max = MAX_DIALOG_WIDTH).fillMaxWidth().appear(y = Space.lg, scale = 0.92f, fade = false)
                .clip(RoundedCornerShape(Radius.sheet)).background(c.s1).border(1.dp, c.line, RoundedCornerShape(Radius.sheet))
                .clickable(enabled = false) {}.animateContentSize(motionSpec(Spring.StiffnessMediumLow, 1f)).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Label(stringResource(R.string.install_pc_app), color = c.text, style = Glidr.type.heading)
            if (hosted) {
                Label(stringResource(R.string.auto_install_intro), color = c.dim, style = Glidr.type.small)
                steps.forEachIndexed { i, res -> NumberedStep(i + 1, stringResource(res), done = false, active = true) }
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

/** Shown at the top of Settings while a newer release exists. */
@Composable
private fun UpdateBanner(release: Release, onUpdate: (Release) -> Unit) {
    val c = Glidr.colors
    val action = stringResource(if (release.hasApk) R.string.update_now else R.string.download)
    Row(
        Modifier.fillMaxWidth().padding(top = Space.sm).clip(RoundedCornerShape(Radius.card)).background(c.s1)
            .border(1.dp, c.accent, RoundedCornerShape(Radius.card)).padding(Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Space.md)) {
            Label(stringResource(R.string.update_available, release.version), color = c.text, style = Glidr.type.body)
            Label(stringResource(if (release.hasApk) R.string.update_available_sub else R.string.update_download_sub), color = c.dim, style = Glidr.type.small)
        }
        PressBox({ onUpdate(release) }, Modifier.height(48.dp), action, RoundedCornerShape(Radius.card), background = c.accent, border = false) {
            Label(action, Modifier.padding(horizontal = 20.dp), c.ink)
        }
    }
}

@Composable
private fun UpdateRow(version: String, state: UpdateState, onCheck: () -> Unit) {
    val status = when (state) {
        UpdateState.Checking -> stringResource(R.string.update_checking)
        UpdateState.UpToDate -> stringResource(R.string.update_latest)
        UpdateState.NoReleases -> stringResource(R.string.update_none)
        UpdateState.ViaStore -> stringResource(R.string.update_via_store)
        is UpdateState.Available -> stringResource(R.string.update_available, state.release.version)
        is UpdateState.Failed -> stringResource(R.string.update_failed)
        UpdateState.Idle -> ""
    }
    ActionRow(
        stringResource(R.string.version_label, version), status, stringResource(R.string.check_updates),
        subAccent = state is UpdateState.Available, showAction = state != UpdateState.ViaStore,
        actionDescription = stringResource(R.string.check_updates),
    ) { if (state != UpdateState.Checking) onCheck() }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val c = Glidr.colors
    Label(title.uppercase(), Modifier.padding(start = Space.xs, top = Space.xl, bottom = Space.sm), c.dim, Glidr.type.small)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(c.s1).border(1.dp, c.line, RoundedCornerShape(Radius.card)).padding(horizontal = Space.lg)) { content() }
}

@Composable
private fun Pad(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = Space.lg)) { content() }
}

@Composable
private fun SliderRow(title: String, value: String, slider: @Composable () -> Unit) {
    val c = Glidr.colors
    Column(Modifier.fillMaxWidth().padding(top = Space.lg)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(title, color = c.text, style = Glidr.type.body)
            Label(value, color = c.accentText, style = Glidr.type.mono)
        }
        slider()
    }
}

/**
 * Title and hint on the left, a 48dp pill button on the right. At least 64dp tall, and it grows when the system
 * font is larger so two-line hints are never clipped.
 */
@Composable
private fun ActionRow(
    title: String, sub: String, action: String, subAccent: Boolean = false, showAction: Boolean = true,
    actionDescription: String = title, onClick: () -> Unit,
) {
    val c = Glidr.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = Space.sm), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = Space.md)) {
            Label(title, color = c.text, style = Glidr.type.body)
            // A status that changes (checking, up to date) rises into place.
            if (sub.isNotEmpty()) Label(sub, Modifier.enterOnChange(sub, y = Space.xs), if (subAccent) c.accentText else c.dim, Glidr.type.small)
        }
        if (showAction) {
            PressBox(onClick, Modifier.height(48.dp), actionDescription, RoundedCornerShape(Radius.card)) {
                Label(action, Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}

/** Preview of the speed-in, speed-out curve for the current sensitivity and acceleration. */
@Composable
private fun AccelCurve(sensitivity: Float, accel: Int) {
    val c = Glidr.colors
    val k = floatArrayOf(0f, 0.6f, 1.2f, 2.0f)[accel.coerceIn(0, 3)]
    Box(Modifier.fillMaxWidth().padding(top = Space.md)) {
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val w = size.width
            val h = size.height
            val path = Path()
            for (i in 0..24) {
                val x = i / 24f
                val y = (sensitivity * x * (1 + k * x) / 6f).coerceIn(0f, 1f)
                val px = 6f + x * (w - 12f)
                val py = h - 6f - y * (h - 12f)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawLine(c.s3, Offset(6f, h - 6f), Offset(w - 6f, 6f), 3f)
            drawPath(path, c.accentText, style = Stroke(6f, cap = StrokeCap.Round))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Label(stringResource(R.string.curve_slow).uppercase(), color = c.dim, style = Glidr.type.small)
        Label(stringResource(R.string.curve_fast).uppercase(), color = c.dim, style = Glidr.type.small)
    }
}

/**
 * A small practice area that runs touches through the same [MotionPipeline] as the real pad, so the sliders can be
 * judged without a PC. The dot moves at half scale; nothing is sent over Bluetooth.
 */
@Composable
private fun TryPad(sensitivity: Float, accel: Int) {
    val c = Glidr.colors
    val pipe = remember { MotionPipeline() }
    pipe.sensitivity = sensitivity
    pipe.acceleration = accel
    var dot by remember { mutableStateOf<Offset?>(null) }
    var active by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Radius.control)
    val edge by animateColorAsState(if (active) c.accent else c.line, settleSpec(), label = "tryEdge")
    Box(
        Modifier.fillMaxWidth().height(96.dp).clip(shape).background(c.s2).border(1.dp, edge, shape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pipe.reset()
                    active = true
                    var last = down.uptimeMillis
                    var p = dot ?: Offset(size.width / 2f, size.height / 2f)
                    dot = p
                    do {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.first()
                        val d = ch.positionChange()
                        pipe.process(d.x, d.y, (ch.uptimeMillis - last).toFloat())
                        last = ch.uptimeMillis
                        p = Offset((p.x + pipe.outX * 0.5f).coerceIn(0f, size.width.toFloat()), (p.y + pipe.outY * 0.5f).coerceIn(0f, size.height.toFloat()))
                        dot = p
                        ch.consume()
                    } while (ev.changes.any { it.pressed })
                    active = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Label(stringResource(R.string.try_pad), color = c.dim, style = Glidr.type.small)
        Canvas(Modifier.fillMaxSize()) {
            dot?.let { drawCircle(c.accent, 8.dp.toPx(), it) }
        }
    }
}
