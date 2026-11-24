package com.yashraj.glidr.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yashraj.glidr.R
import com.yashraj.glidr.companion.COMPANION_DOWNLOAD_URL
import com.yashraj.glidr.companion.companionInstallCommand
import com.yashraj.glidr.hid.ConnectionState
import com.yashraj.glidr.hid.PcDevice
import com.yashraj.glidr.ui.pad.KeyboardSheet
import com.yashraj.glidr.ui.pad.MouseButtons
import com.yashraj.glidr.ui.pad.PadSurface
import com.yashraj.glidr.ui.theme.Glidr
import com.yashraj.glidr.ui.theme.LocalReduceMotion
import com.yashraj.glidr.ui.theme.Radius
import com.yashraj.glidr.ui.theme.Space
import com.yashraj.glidr.update.UpdateState
import kotlinx.coroutines.delay

private enum class Page { Pad, Media, Settings }

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
    val ctx = LocalContext.current
    var page by rememberSaveable { mutableIntStateOf(Page.Pad.ordinal) }
    var kb by rememberSaveable { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<PcDevice>?>(null) }
    var replayWelcome by rememberSaveable { mutableStateOf(false) }
    var toastText by remember { mutableStateOf("") }
    var toastOn by remember { mutableStateOf(false) }
    val toastAlpha by animateFloatAsState(if (toastOn) 1f else 0f, motionSpec(), label = "toast")
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

    BackHandler(enabled = showWelcome || devices != null || page != Page.Pad.ordinal) {
        when {
            showWelcome -> { replayWelcome = false; vm.update { it.copy(onboarded = true) } }
            devices != null -> devices = null
            else -> page = Page.Pad.ordinal
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val compact = maxHeight < COMPACT_HEIGHT
        when (Page.entries[page]) {
            Page.Settings -> SettingsScreen(
                s, companion, conn == ConnectionState.Connected,
                { done -> vm.engine.runOnPc(companionInstallCommand(COMPANION_DOWNLOAD_URL), done) },
                vm::update, vm::forgetPc, { page = Page.Pad.ordinal },
                appVersion = vm.appVersion, updateState = update, onCheckUpdate = vm::checkForUpdates,
                onOpenUpdate = { url -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } },
                onReset = vm::resetSettings, onShowWelcome = { replayWelcome = true },
            )
            else -> Column(Modifier.fillMaxSize()) {
                TopBar(
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
                val keyboardOnly = compact && kb && page == Page.Pad.ordinal
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        keyboardOnly -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.BottomCenter) {
                            KeyboardSheet(vm.engine, s, vm.stt, hasMic, onRequestMic)
                        }
                        page == Page.Pad.ordinal -> Column(Modifier.fillMaxSize().padding(horizontal = Space.lg)) {
                            PadSurface(
                                vm.engine, s,
                                if (awake.active) {
                                    if (awake.paused) stringResource(R.string.awake_paused)
                                    else stringResource(R.string.awake_chip, "%d:%02d".format(awake.nextInSec / 60, awake.nextInSec % 60))
                                } else null,
                                vm::setTouching, Modifier.weight(1f).fillMaxWidth(),
                            )
                            Gap(Space.md)
                            MouseButtons(vm.engine, s)
                        }
                        else -> MediaScreen(vm.engine, s.haptics, nowPlaying, companion)
                    }
                    if (toastAlpha > 0.01f) Box(Modifier.align(Alignment.TopCenter).padding(top = Space.sm).alpha(toastAlpha)) { Toast(toastText) }
                }
                AnimatedVisibility(kb && page == Page.Pad.ordinal && !compact) { KeyboardSheet(vm.engine, s, vm.stt, hasMic, onRequestMic) }
                Dock(
                    media = page == Page.Media.ordinal,
                    kbOn = kb,
                    compact = compact,
                    onPad = { page = Page.Pad.ordinal },
                    onMedia = { page = Page.Media.ordinal },
                    onKb = { page = Page.Pad.ordinal; kb = !kb },
                )
            }
        }

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
        Modifier.clip(RoundedCornerShape(Radius.card)).background(c.text).padding(horizontal = Space.lg, vertical = 10.dp)
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
    val pulse by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dotAlpha",
    )
    val alpha = if (busy && !LocalReduceMotion.current) pulse else if (busy) 0.6f else 1f
    val color = when (state) {
        ConnectionState.Connected, ConnectionState.Connecting, ConnectionState.Reconnecting, ConnectionState.Registering -> c.accent
        ConnectionState.BluetoothOff, ConnectionState.Unsupported, ConnectionState.Failed, null -> c.danger
        ConnectionState.Ready -> c.dim
    }
    Box(Modifier.size(8.dp).alpha(alpha).clip(CircleShape).background(color))
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
            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusDot(state)
                Label(label)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (showAwake) RoundIconButton(Ico.Awake, stringResource(R.string.keep_pc_awake), onAwake, active = awakeOn)
            RoundIconButton(Ico.Settings, stringResource(R.string.settings), onSettings, active = updateWaiting) // lit while an update waits
        }
    }
}

/** Pad / Media tabs plus the keyboard button. The tab pill's inner radius is its outer radius minus the 6dp frame. */
@Composable
private fun Dock(media: Boolean, kbOn: Boolean, compact: Boolean, onPad: () -> Unit, onMedia: () -> Unit, onKb: () -> Unit) {
    val c = Glidr.colors
    val h = if (compact) 56.dp else 64.dp
    val outer = h / 2
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = if (compact) Space.sm else Space.lg).height(h),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(outer)).background(c.s2).border(1.dp, c.line, RoundedCornerShape(outer)).padding(6.dp)) {
            DockTab(Modifier.weight(1f), stringResource(R.string.pad), Ico.Pad, !media, outer - 6.dp, onPad)
            DockTab(Modifier.weight(1f), stringResource(R.string.media), Ico.Media, media, outer - 6.dp, onMedia)
        }
        PressBox(onKb, Modifier.size(h), stringResource(R.string.keyboard), CircleShape, background = if (kbOn) c.text else c.s2) {
            Icon(Ico.Keyboard, if (kbOn) c.bg else c.text)
        }
    }
}

@Composable
private fun DockTab(modifier: Modifier, label: String, icon: Ico, on: Boolean, radius: Dp, onClick: () -> Unit) {
    val c = Glidr.colors
    Row(
        modifier.fillMaxSize().clip(RoundedCornerShape(radius)).background(if (on) c.accent else Color.Transparent)
            .selectable(selected = on, role = Role.Tab, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, if (on) c.ink else c.dim, 20.dp)
        Box(Modifier.size(Space.sm))
        Label(label, color = if (on) c.ink else c.dim, style = Glidr.type.body)
    }
}

@Composable
private fun DeviceSheet(
    list: List<PcDevice>, connected: String?, state: ConnectionState, pairStep: Int,
    onPick: (PcDevice) -> Unit, onDisconnect: () -> Unit, onPair: () -> Unit, onBtSettings: () -> Unit, onClose: () -> Unit,
) {
    val c = Glidr.colors
    BoxWithConstraints(Modifier.fillMaxSize().background(Scrim).clickable(onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.92f)
                .clip(RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet)).background(c.s1)
                .clickable(enabled = false) {}.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Label(stringResource(R.string.choose_pc), color = c.text, style = Glidr.type.heading)
            if (pairStep < 4) PairGuide(pairStep, onPair)
            if (list.isEmpty()) Label(stringResource(R.string.no_paired), color = c.dim, style = Glidr.type.body)
            Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
