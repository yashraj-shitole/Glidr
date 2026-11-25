package com.yashraj.glidr

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yashraj.glidr.companion.CompanionState
import com.yashraj.glidr.companion.NowPlaying
import com.yashraj.glidr.data.Settings
import com.yashraj.glidr.hid.ConnectionState
import com.yashraj.glidr.hid.HidEngine
import com.yashraj.glidr.hid.HidTransport
import com.yashraj.glidr.hid.PcDevice
import com.yashraj.glidr.ui.MediaScreen
import com.yashraj.glidr.ui.pad.QuickKeys
import com.yashraj.glidr.ui.theme.GlidrTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Screens in states that need a PC, driven through a transport that only says it is connected. This checks what the
 * UI does while its animations run; it says nothing about Bluetooth.
 */
@RunWith(AndroidJUnit4::class)
class ConnectedUiTest {
    @get:Rule
    val rule = createComposeRule()

    private class PretendConnected : HidTransport {
        override val state = MutableStateFlow(ConnectionState.Connected)
        override val host = MutableStateFlow<PcDevice?>(PcDevice("Test PC", "00:00:00:00:00:00"))
        override fun start() = Unit
        override fun stop() = Unit
        override fun retry() = Unit
        override fun bondedHosts() = emptyList<PcDevice>()
        override fun connect(device: PcDevice) = Unit
        override fun disconnect() = Unit
        override fun sendMouse(buf: ByteArray) = true
        override fun sendKeyboard(buf: ByteArray) = true
        override fun sendConsumer(buf: ByteArray) = true
    }

    private val engine = HidEngine(PretendConnected(), CoroutineScope(Dispatchers.Default))

    private fun track(title: String, playing: Boolean) =
        NowPlaying(title, "Artist", "Album", "Player", playing, 1_000, 200_000, null, 0)

    @Test
    fun heldModifierLatchesAndReleases() {
        rule.setContent { GlidrTheme(light = false, accent = 0, reduceMotion = false) { QuickKeys(engine, Settings(haptics = 0)) } }
        val ctrl = rule.onNodeWithContentDescription("Ctrl held for the next click")
        ctrl.assertIsOff()
        ctrl.performClick()
        ctrl.assertIsOn()
        ctrl.performClick()
        ctrl.assertIsOff()
    }

    @Test
    fun nowPlayingFollowsTrackAndStateChanges() {
        var now by mutableStateOf<NowPlaying?>(null)
        rule.setContent {
            GlidrTheme(light = false, accent = 0, reduceMotion = false) { MediaScreen(engine, 0, now, CompanionState.Connected) }
        }
        for (next in listOf(track("First song", true), track("First song", false), track("Second song", true))) {
            rule.runOnUiThread { now = next }
            rule.onNodeWithText(next.title).assertExists()
        }
        rule.onNodeWithText("First song").assertDoesNotExist()
        rule.runOnUiThread { now = null }
        rule.onNodeWithText("Second song").assertDoesNotExist()
    }
}
