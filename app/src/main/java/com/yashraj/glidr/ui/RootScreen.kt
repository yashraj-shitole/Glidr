package com.yashraj.glidr.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
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

private enum class Page { Pad, Media, Settings }

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
    val conn by vm.connection.collectAsStateWithLifecycle()
    val host by vm.host.collectAsStateWithLifecycle()
    val awake by vm.keepAwake.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val companion by vm.companionState.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(Page.Pad.ordinal) }
    var kb by rememberSaveable { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<PcDevice>?>(null) }

    BackHandler(enabled = devices != null || page != Page.Pad.ordinal) {
        if (devices != null) devices = null else page = Page.Pad.ordinal
    }

    Box(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        when (Page.entries[page]) {
            Page.Settings -> SettingsScreen(
                s, companion, conn == ConnectionState.Connected,
                { done -> vm.engine.runOnPc(companionInstallCommand(COMPANION_DOWNLOAD_URL), done) },
                vm::update, vm::forgetPc, { page = Page.Pad.ordinal })
            else -> Column(Modifier.fillMaxSize()) {
                TopBar(
                    label = if (hasBluetoothPermission) connectionLabel(conn, host?.name) else stringResource(R.string.state_need_permission),
                    ready = conn == ConnectionState.Connected,
                    awakeOn = awake.active,
                    showAwake = s.keepAwakeEnabled,
                    onPill = {
                        when {
                            !hasBluetoothPermission -> onRequestBluetooth()
                            conn == ConnectionState.Failed || conn == ConnectionState.Registering -> vm.retryTransport()
                            else -> devices = vm.bondedHosts()
                        }
                    },
                    onAwake = onToggleKeepAwake,
                    onSettings = { page = Page.Settings.ordinal },
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (page == Page.Pad.ordinal) {
                        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                            PadSurface(
                                vm.engine, s,
                                if (awake.active) {
                                    if (awake.paused) stringResource(R.string.awake_paused)
                                    else stringResource(R.string.awake_chip, "%d:%02d".format(awake.nextInSec / 60, awake.nextInSec % 60))
                                } else null,
                                vm::setTouching, Modifier.weight(1f).fillMaxWidth(),
                            )
                            Box(Modifier.height(12.dp))
                            MouseButtons(vm.engine, s)
                        }
                    } else {
                        MediaScreen(vm.engine, s.haptics, nowPlaying, companion)
                    }
                }
                AnimatedVisibility(kb && page == Page.Pad.ordinal) { KeyboardSheet(vm.engine, s, vm.stt, hasMic, onRequestMic) }
                Dock(
                    media = page == Page.Media.ordinal,
                    kbOn = kb,
                    onPad = { page = Page.Pad.ordinal },
                    onMedia = { page = Page.Media.ordinal },
                    onKb = { page = Page.Pad.ordinal; kb = !kb },
                )
            }
        }

        devices?.let { list ->
            DeviceSheet(
                list, host?.address, conn,
                onPick = { vm.connect(it); devices = null },
                onDisconnect = { vm.disconnect(); devices = null },
                onPair = onMakeDiscoverable,
                onBtSettings = onOpenBluetoothSettings,
                onClose = { devices = null },
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

@Composable
private fun TopBar(label: String, ready: Boolean, awakeOn: Boolean, showAwake: Boolean, onPill: () -> Unit, onAwake: () -> Unit, onSettings: () -> Unit) {
    val c = Glidr.colors
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        PressBox(onPill, Modifier.height(48.dp), stringResource(R.string.connection_status, label), RoundedCornerShape(24.dp)) {
            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (ready) c.accent else c.dim))
                Label(label)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showAwake) RoundIconButton(Ico.Awake, stringResource(R.string.keep_pc_awake), onAwake, active = awakeOn)
            RoundIconButton(Ico.Settings, stringResource(R.string.settings), onSettings)
        }
    }
}

@Composable
private fun Dock(media: Boolean, kbOn: Boolean, onPad: () -> Unit, onMedia: () -> Unit, onKb: () -> Unit) {
    val c = Glidr.colors
    Row(Modifier.fillMaxWidth().padding(16.dp).height(64.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(32.dp)).background(c.s2).border(1.dp, c.line, RoundedCornerShape(32.dp)).padding(6.dp)) {
            DockTab(Modifier.weight(1f), stringResource(R.string.pad), Ico.Pad, !media, onPad)
            DockTab(Modifier.weight(1f), stringResource(R.string.media), Ico.Media, media, onMedia)
        }
        PressBox(onKb, Modifier.size(64.dp), stringResource(R.string.keyboard), CircleShape, background = if (kbOn) c.text else c.s2) {
            Icon(Ico.Keyboard, if (kbOn) c.bg else c.text)
        }
    }
}

@Composable
private fun DockTab(modifier: Modifier, label: String, icon: Ico, on: Boolean, onClick: () -> Unit) {
    val c = Glidr.colors
    Row(
        modifier.fillMaxSize().clip(RoundedCornerShape(26.dp)).background(if (on) c.accent else androidx.compose.ui.graphics.Color.Transparent).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, if (on) c.ink else c.dim, 20.dp)
        Box(Modifier.size(8.dp))
        Label(label, color = if (on) c.ink else c.dim, style = Glidr.type.body)
    }
}

@Composable
private fun DeviceSheet(
    list: List<PcDevice>, connected: String?, state: ConnectionState,
    onPick: (PcDevice) -> Unit, onDisconnect: () -> Unit, onPair: () -> Unit, onBtSettings: () -> Unit, onClose: () -> Unit,
) {
    val c = Glidr.colors
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x99000000)).clickable(onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(c.s1)
                .clickable(enabled = false) {}.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Label(stringResource(R.string.choose_pc), color = c.text, style = Glidr.type.title.copy(fontSize = androidx.compose.ui.unit.TextUnit(22f, androidx.compose.ui.unit.TextUnitType.Sp)))
            Label(stringResource(R.string.pair_help), color = c.dim, style = Glidr.type.small)
            if (list.isEmpty()) Label(stringResource(R.string.no_paired), color = c.dim, style = Glidr.type.body)
            Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (d in list) {
                    PressBox({ onPick(d) }, Modifier.fillMaxWidth().height(56.dp), d.name) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Label(d.name, color = c.text, style = Glidr.type.body)
                            if (d.address == connected && state == ConnectionState.Connected) Label(stringResource(R.string.connected), color = c.accentText, style = Glidr.type.small)
                        }
                    }
                }
            }
            PressBox(onPair, Modifier.fillMaxWidth().height(52.dp), null, background = c.accent, border = false) { Label(stringResource(R.string.make_discoverable), color = c.ink) }
            PressBox(onBtSettings, Modifier.fillMaxWidth().height(52.dp)) { Label(stringResource(R.string.bt_settings)) }
            if (connected != null && state == ConnectionState.Connected) {
                PressBox(onDisconnect, Modifier.fillMaxWidth().height(52.dp)) { Label(stringResource(R.string.disconnect)) }
            }
        }
    }
}
