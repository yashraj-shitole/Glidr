package com.yashraj.glidr.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yashraj.glidr.R
import com.yashraj.glidr.companion.COMPANION_DOWNLOAD_URL
import com.yashraj.glidr.companion.companionInstallCommand
import com.yashraj.glidr.hid.ConnectionState
import com.yashraj.glidr.hid.PcDevice
import com.yashraj.glidr.ui.pad.KeyboardSheet
import com.yashraj.glidr.ui.pad.MouseButtons
import com.yashraj.glidr.ui.pad.PadSurface
import com.yashraj.glidr.ui.pad.QuickKeys
import com.yashraj.glidr.ui.theme.Glidr
import com.yashraj.glidr.ui.theme.LocalReduceMotion
import com.yashraj.glidr.ui.theme.MAX_SHEET_WIDTH
import com.yashraj.glidr.ui.theme.Radius
import com.yashraj.glidr.ui.theme.Space
import com.yashraj.glidr.update.InstallStep
import com.yashraj.glidr.update.UpdateState
import kotlinx.coroutines.delay

private enum class Page { Pad, Media, Present, Settings }

/** Below this window height (a phone on its side) the chrome shrinks and the keyboard replaces the pad. */
private val COMPACT_HEIGHT = 500.dp

@Composable
fun RootScreen(
    vm: GlidrViewModel,
    onRequestBluetooth: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    hasBluetoothPermission: Boolean,
    onToggleKeepAwake: () -> Unit,
    hasMic: Boolean,
    onRequestMic: () -> Unit,
) {
    val c = Glidr.colors
    val s by vm.settings.collectAsStateWithLifecycle()
    val loaded by vm.settingsLoaded.collectAsStateWithLifecycle()
    val conn by vm.connection.collectAsStateWithLifecycle()
    val host by vm.host.collectAsStateWithLifecycle()
    val awake by vm.keepAwake.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val companion by vm.companionState.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val updatePrompt by vm.updatePrompt.collectAsStateWithLifecycle()
    val install by vm.install.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val openLink: (String) -> Unit = { url -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }
    var page by rememberSaveable { mutableIntStateOf(Page.Pad.ordinal) }
    var kb by rememberSaveable { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<PcDevice>?>(null) }
    var replayWelcome by rememberSaveable { mutableStateOf(false) }
    var toastText by remember { mutableStateOf("") }
    var toastOn by remember { mutableStateOf(false) }
    val showWelcome = replayWelcome || (loaded && !s.onboarded)
    val connectedText = stringResource(R.string.notif_connected, host?.name ?: stringResource(R.string.your_pc))

    // Pairing progress for the guide: 4 connected, 3 paired but not connected, 1 nothing paired yet.
    val paired = remember(conn, devices, hasBluetoothPermission) {
        hasBluetoothPermission && runCatching { vm.bondedHosts().isNotEmpty() }.getOrDefault(false)
    }
    val pairStep = when {
        conn == ConnectionState.Connected -> 4
        paired -> 3
        else -> 1
    }
    val discoverable = { if (hasBluetoothPermission) onMakeDiscoverable() else onRequestBluetooth() }

    // Launcher shortcuts ask for a page by name.
    val pageRequest by vm.pageRequest.collectAsStateWithLifecycle()
    LaunchedEffect(pageRequest) {
        when (pageRequest) {
            "media" -> page = Page.Media.ordinal
            "pad" -> page = Page.Pad.ordinal
            "present" -> page = Page.Present.ordinal
            else -> Unit
        }
        if (pageRequest != null) vm.pageHandled()
    }

    // The presenter screen takes over the volume keys while it is showing.
    LaunchedEffect(page) { vm.setPresenting(page == Page.Present.ordinal) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.setPresenting(false) } }
    val presentElapsed by vm.presentElapsed.collectAsStateWithLifecycle()
    val presentRunning by vm.presentRunning.collectAsStateWithLifecycle()
    val buzzIndex by vm.buzzIndex.collectAsStateWithLifecycle()
    val volumeKeys by vm.volumeKeys.collectAsStateWithLifecycle()

    // A short "Connected to X" note, because the status pill alone is easy to miss.
    LaunchedEffect(conn) {
        if (conn == ConnectionState.Connected) {
            toastText = connectedText
            toastOn = true
            delay(2_500)
            toastOn = false
        } else {
            toastOn = false
        }
    }

    // Look for a new release again when the app is brought back.
    LifecycleResumeEffect(Unit) {
        vm.onForeground()
        onPauseOrDispose {}
    }
    // Android wants its own confirmation before installing: open it as soon as it is ready.
    LaunchedEffect(install?.step) {
        if (install?.step is InstallStep.Confirm) vm.takeInstallPrompt()?.let { runCatching { ctx.startActivity(it) } }
    }

    BackHandler(enabled = showWelcome || devices != null || page != Page.Pad.ordinal) {
        when {
            showWelcome -> { replayWelcome = false; vm.update { it.copy(onboarded = true) } }
            devices != null -> devices = null
            else -> page = Page.Pad.ordinal
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val compact = maxHeight < COMPACT_HEIGHT
        // Settings comes in from the right and Back brings the main screen in from the left.
        Box(Modifier.fillMaxSize().enterFromSide(if (page == Page.Settings.ordinal) 1 else 0, Space.xl)) { when (Page.entries[page]) {
            Page.Settings -> SettingsScreen(
                s, companion, conn == ConnectionState.Connected,
                { done -> vm.engine.runOnPc(companionInstallCommand(COMPANION_DOWNLOAD_URL), done) },
                vm::update, vm::forgetPc, { page = Page.Pad.ordinal },
                appVersion = vm.appVersion, updateState = update, onCheckUpdate = { vm.checkForUpdates() },
                onUpdate = { if (!vm.installUpdate(it)) openLink(it.downloadUrl) },
                onReset = vm::resetSettings, onShowWelcome = { replayWelcome = true },
            )
            else -> Column(Modifier.fillMaxSize()) {
                val keyboardOnly = compact && kb && page == Page.Pad.ordinal
                // In landscape the open keyboard needs every row it can get, so the status bar steps aside.
                if (!keyboardOnly) TopBar(
                    label = if (hasBluetoothPermission) connectionLabel(conn, host?.name) else stringResource(R.string.state_need_permission),
                    state = if (hasBluetoothPermission) conn else null,
                    awakeOn = awake.active,
                    showAwake = s.keepAwakeEnabled,
                    compact = compact,
                    onPill = {
                        when {
                            !hasBluetoothPermission -> onRequestBluetooth()
                            conn == ConnectionState.Failed || conn == ConnectionState.Registering -> vm.retryTransport()
                            else -> devices = vm.bondedHosts()
                        }
                    },
                    onAwake = onToggleKeepAwake,
                    onSettings = { page = Page.Settings.ordinal },
                    updateWaiting = update is UpdateState.Available,
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Box(Modifier.fillMaxSize().enterFromSide(page, Space.xl)) { when {
                        keyboardOnly -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.BottomCenter) {
                            KeyboardSheet(vm.engine, s, vm.stt, hasMic, onRequestMic)
                        }
                        page == Page.Pad.ordinal -> Column(Modifier.fillMaxSize().padding(start = Space.lg, end = Space.lg, bottom = if (kb) Space.md else 0.dp)) {
                            PadSurface(
                                vm.engine, s, conn == ConnectionState.Connected,
                                if (awake.active) {
                                    if (awake.paused) stringResource(R.string.awake_paused)
                                    else stringResource(R.string.awake_chip, "%d:%02d".format(awake.nextInSec / 60, awake.nextInSec % 60))
                                } else null,
                                vm::setTouching, Modifier.weight(1f).fillMaxWidth(),
                            )
                            Gap(Space.md)
                            // No room for the extra row in landscape, and the open keyboard already has these keys.
                            Reveal(s.quickKeys && !compact && !kb) {
                                Column {
                                    QuickKeys(vm.engine, s)
                                    Gap(Space.md)
                                }
                            }
                            MouseButtons(vm.engine, s)
                        }
                        page == Page.Media.ordinal -> MediaScreen(vm.engine, s.haptics, nowPlaying, companion, compact = compact)
                        else -> PresentScreen(
                            vm.engine, s,
                            PresentState(
                                presentElapsed, presentRunning, buzzIndex, volumeKeys,
                                vm::presentToggleTimer, vm::presentResetTimer, vm::setBuzz, vm::setVolumeKeys, vm::slide,
                            ),
                            compact,
                        )
                    } }
                    Presence(toastOn, Modifier.align(Alignment.TopCenter).padding(top = Space.sm), fromAbove = true) { Toast(toastText) }
                }
                Reveal(kb && page == Page.Pad.ordinal && !compact) { KeyboardSheet(vm.engine, s, vm.stt, hasMic, onRequestMic) }
                Dock(
                    current = page,
                    kbOn = kb,
                    compact = compact,
                    onPad = { page = Page.Pad.ordinal },
                    onMedia = { page = Page.Media.ordinal },
                    onPresent = { page = Page.Present.ordinal },
                    onKb = { page = Page.Pad.ordinal; kb = !kb },
                )
            }
        } }

        devices?.let { list ->
            DeviceSheet(
                list, host?.address, conn, pairStep,
                onPick = { vm.connect(it); devices = null },
                onDisconnect = { vm.disconnect(); devices = null },
                onPair = discoverable,
                onBtSettings = onOpenBluetoothSettings,
                onClose = { devices = null },
            )
        }

        if (showWelcome) {
            WelcomeCard(pairStep, discoverable) { replayWelcome = false; vm.update { it.copy(onboarded = true) } }
        }

        // The popup stays off the welcome card, the PC picker and the presenter screen; an update already under way
        // is always shown.
        val asking = updatePrompt?.takeIf { loaded && !showWelcome && devices == null && page != Page.Present.ordinal }
        (install?.release ?: asking)?.let { release ->
            UpdateDialog(
                release.version, vm.appVersion, install?.step,
                onUpdate = {
                    if (!vm.installUpdate(release)) {
                        openLink(release.downloadUrl)
                        vm.postponeUpdate(release.version)
                    }
                },
                onInstall = { (install?.step as? InstallStep.Confirm)?.let { runCatching { ctx.startActivity(it.intent) } } },
                onLater = { vm.postponeUpdate(release.version) },
                onOpenPage = { openLink(release.pageUrl) },
            )
        }
    }
}

@Composable
private fun connectionLabel(state: ConnectionState, host: String?): String = when (state) {
    ConnectionState.Unsupported -> stringResource(R.string.state_unsupported)
    ConnectionState.BluetoothOff -> stringResource(R.string.state_bt_off)
    ConnectionState.Registering -> stringResource(R.string.state_registering)
    ConnectionState.Ready -> stringResource(R.string.state_ready)
    ConnectionState.Connecting -> stringResource(R.string.state_connecting)
    ConnectionState.Connected -> host ?: stringResource(R.string.your_pc)
    ConnectionState.Reconnecting -> stringResource(R.string.state_reconnecting)
    ConnectionState.Failed -> stringResource(R.string.state_failed)
}

/** Inverted pill, announced to TalkBack as a polite live region. */
@Composable
private fun Toast(text: String) {
    val c = Glidr.colors
    Box(
        Modifier.clip(RoundedCornerShape(Radius.card)).background(c.text).padding(horizontal = Space.lg, vertical = Space.md)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) { Label(text, color = c.bg, style = Glidr.type.small) }
}

/**
 * Connection state at a glance: solid accent = connected, pulsing accent = working on it, red = needs attention
 * (Bluetooth off, unsupported, failed or no permission), grey = idle.
 */
@Composable
private fun StatusDot(state: ConnectionState?) {
    val c = Glidr.colors
    val busy = state == ConnectionState.Connecting || state == ConnectionState.Reconnecting || state == ConnectionState.Registering
    // Only while there is something to pulse about: an idle infinite transition still asks for every frame.
    val pulse = if (busy && !LocalReduceMotion.current) {
        rememberInfiniteTransition(label = "dot").animateFloat(
            initialValue = 0.3f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dotAlpha",
        )
    } else null
    val color by animateColorAsState(
        when (state) {
            ConnectionState.Connected, ConnectionState.Connecting, ConnectionState.Reconnecting, ConnectionState.Registering -> c.accent
            ConnectionState.BluetoothOff, ConnectionState.Unsupported, ConnectionState.Failed, null -> c.danger
            ConnectionState.Ready -> c.dim
        },
        settleSpec(Spring.StiffnessMediumLow), label = "dotColor",
    )
    Box(
        Modifier.size(8.dp)
            // A bigger bounce when the link comes up or drops.
            .popOnChange(state == ConnectionState.Connected, from = 2f)
            .graphicsLayer { alpha = pulse?.value ?: if (busy) 0.6f else 1f }
            .clip(CircleShape).background(color),
    )
}

@Composable
private fun TopBar(
    label: String, state: ConnectionState?, awakeOn: Boolean, showAwake: Boolean, compact: Boolean,
    onPill: () -> Unit, onAwake: () -> Unit, onSettings: () -> Unit, updateWaiting: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = if (compact) Space.sm else Space.lg),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        PressBox(onPill, Modifier.height(48.dp), stringResource(R.string.connection_status, label), RoundedCornerShape(Radius.card)) {
            // The pill stretches to the new text, which rises into place.
            Row(
                Modifier.animateContentSize(motionSpec(Spring.StiffnessMediumLow, 0.8f)).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                StatusDot(state)
                Label(label, Modifier.enterOnChange(label, y = Space.sm), maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (showAwake) RoundIconButton(Ico.Awake, stringResource(R.string.keep_pc_awake), onAwake, active = awakeOn)
            RoundIconButton(Ico.Settings, stringResource(R.string.settings), onSettings, active = updateWaiting) // lit while an update waits
        }
    }
}

/** Pad / Media tabs plus the keyboard button. The tab pill's inner radius is its outer radius minus the 4dp frame. */
@Composable
private fun Dock(current: Int, kbOn: Boolean, compact: Boolean, onPad: () -> Unit, onMedia: () -> Unit, onPresent: () -> Unit, onKb: () -> Unit) {
    val c = Glidr.colors
    val h = if (compact) 56.dp else 64.dp
    val outer = h / 2
    val kbBg by animateColorAsState(if (kbOn) c.text else c.s2, settleSpec(), label = "kbBg")
    val kbFg by animateColorAsState(if (kbOn) c.bg else c.text, settleSpec(), label = "kbFg")
    // The accent pill slides between the tabs. Page.Pad, Media and Present are the first three ordinals.
    val position = animateFloatAsState(current.coerceAtMost(Page.Present.ordinal).toFloat(), motionSpec(Spring.StiffnessMediumLow, 0.8f), label = "dockPill")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = if (compact) Space.sm else Space.lg).height(h),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(outer)).background(c.s2).border(1.dp, c.line, RoundedCornerShape(outer)).padding(Space.xs)) {
            Row(Modifier.fillMaxSize().slidingPill(position, 3, c.accent, outer - Space.xs)) {
                DockTab(Modifier.weight(1f), stringResource(R.string.pad), Ico.Pad, current == Page.Pad.ordinal, outer - Space.xs, onPad)
                DockTab(Modifier.weight(1f), stringResource(R.string.media), Ico.Media, current == Page.Media.ordinal, outer - Space.xs, onMedia)
                DockTab(Modifier.weight(1f), stringResource(R.string.present), Ico.Present, current == Page.Present.ordinal, outer - Space.xs, onPresent)
            }
        }
        PressBox(onKb, Modifier.size(h), stringResource(R.string.keyboard), CircleShape, background = kbBg) {
            Icon(Ico.Keyboard, kbFg)
        }
    }
}

@Composable
private fun DockTab(modifier: Modifier, label: String, icon: Ico, on: Boolean, radius: Dp, onClick: () -> Unit) {
    val c = Glidr.colors
    val fg by animateColorAsState(if (on) c.ink else c.dim, settleSpec(), label = "tab")
    val src = remember { MutableInteractionSource() }
    Row(
        modifier.fillMaxSize().clip(RoundedCornerShape(radius))
            .selectable(selected = on, interactionSource = src, indication = LocalIndication.current, role = Role.Tab, onClick = onClick)
            .pressScale(src),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.popOnChange(on, from = 0.7f)) { Icon(icon, fg, 20.dp) }
        Box(Modifier.size(Space.sm))
        Label(label, color = fg, style = Glidr.type.body, maxLines = 1)
    }
}

@Composable
private fun DeviceSheet(
    list: List<PcDevice>, connected: String?, state: ConnectionState, pairStep: Int,
    onPick: (PcDevice) -> Unit, onDisconnect: () -> Unit, onPair: () -> Unit, onBtSettings: () -> Unit, onClose: () -> Unit,
) {
    val c = Glidr.colors
    BoxWithConstraints(Modifier.fillMaxSize().appear().background(Scrim).clickable(onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.widthIn(max = MAX_SHEET_WIDTH).fillMaxWidth().heightIn(max = maxHeight * 0.92f).slideUp()
                .clip(RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet)).background(c.s1)
                .clickable(enabled = false) {}.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Label(stringResource(R.string.choose_pc), color = c.text, style = Glidr.type.heading)
            if (pairStep < 4) PairGuide(pairStep, onPair)
            if (list.isEmpty()) Label(stringResource(R.string.no_paired), color = c.dim, style = Glidr.type.body)
            Column(Modifier.heightIn(max = 256.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                for (d in list) {
                    PressBox({ onPick(d) }, Modifier.fillMaxWidth().height(56.dp), d.name) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = Space.lg), horizontalArrangement = Arrangement.SpaceBetween) {
                            Label(d.name, color = c.text, style = Glidr.type.body)
                            if (d.address == connected && state == ConnectionState.Connected) Label(stringResource(R.string.connected), color = c.accentText, style = Glidr.type.small)
                        }
                    }
                }
            }
            // The guide carries the pairing button until a PC is paired; after that it is a quiet extra for a second PC.
            if (pairStep > 2) PressBox(onPair, Modifier.fillMaxWidth().height(52.dp)) { Label(stringResource(R.string.make_discoverable)) }
            PressBox(onBtSettings, Modifier.fillMaxWidth().height(52.dp)) { Label(stringResource(R.string.bt_settings)) }
            if (connected != null && state == ConnectionState.Connected) {
                PressBox(onDisconnect, Modifier.fillMaxWidth().height(52.dp)) { Label(stringResource(R.string.disconnect)) }
            }
        }
    }
}
