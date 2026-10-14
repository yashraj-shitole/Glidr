package com.yashraj.glidr.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yashraj.glidr.GlidrApp
import com.yashraj.glidr.data.Settings
import com.yashraj.glidr.hid.HidEngine
import com.yashraj.glidr.hid.KeyboardLayout
import com.yashraj.glidr.hid.PcDevice
import com.yashraj.glidr.service.GlidrService
import com.yashraj.glidr.update.GitHubReleases
import com.yashraj.glidr.update.RELEASES_REPO
import com.yashraj.glidr.update.UpdateState
import com.yashraj.glidr.update.Versions
import com.yashraj.glidr.update.appVersion
import com.yashraj.glidr.update.installedFromPlay
import com.yashraj.glidr.voice.AndroidSttEngine
import com.yashraj.glidr.voice.SttEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GlidrViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as GlidrApp
    val engine: HidEngine = app.engine
    val connection = app.transport.state
    val host = app.transport.host
    val keepAwake = app.keepAwake.state
    val nowPlaying = app.companion.nowPlaying
    val companionState = app.companion.state

    val settings: StateFlow<Settings> = app.settings.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    // ------------------------------------------------------------------ updates

    val appVersion: String = appVersion(app)
    private val fromStore = installedFromPlay(app)
    private val checked = MutableStateFlow<UpdateState>(UpdateState.Idle)

    /** The phone's own GitHub check, overridden by a newer release the PC companion reported. */
    val update: StateFlow<UpdateState> = combine(checked, app.companion.release) { own, reported ->
        val viaPc = reported?.takeIf { Versions.compare(it.version, appVersion) > 0 }
        when {
            fromStore -> UpdateState.ViaStore
            viaPc == null -> own
            own is UpdateState.Available && Versions.compare(own.release.version, viaPc.version) >= 0 -> own
            else -> UpdateState.Available(viaPc)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UpdateState.Idle)

    fun checkForUpdates() {
        if (fromStore || checked.value == UpdateState.Checking) return
        checked.value = UpdateState.Checking
        viewModelScope.launch { checked.value = GitHubReleases.check(RELEASES_REPO, appVersion) }
    }

    init {
        checkForUpdates() // once per app start
    }

    init {
        viewModelScope.launch {
            app.settings.flow.collect { s ->
                engine.motion.sensitivity = s.sensitivity
                engine.motion.acceleration = s.acceleration
                engine.scrollSpeed = s.scrollSpeed
                engine.naturalScroll = s.naturalScroll
                engine.layout = KeyboardLayout.byId(s.layoutId)
            }
        }
    }

    fun update(block: (Settings) -> Settings) {
        viewModelScope.launch { app.settings.update(block) }
    }

    fun startTransport() {
        app.transport.start()
        app.companion.start()
    }
    fun retryTransport() = app.transport.retry()
    fun bondedHosts(): List<PcDevice> = app.transport.bondedHosts()

    fun connect(device: PcDevice) {
        app.transport.connect(device)
        update { it.copy(lastHost = device.address) }
    }

    fun disconnect() = app.transport.disconnect()

    fun setTouching(touching: Boolean) {
        app.keepAwake.touching = touching
    }

    fun toggleKeepAwake() {
        if (keepAwake.value.active) GlidrService.stopKeepAwake(app) else GlidrService.startKeepAwake(app)
    }

    /** Keeps the Bluetooth link alive while the app is in the background. */
    fun ensureServiceRunning() = GlidrService.ensureRunning(app)

    private var sttEngine: SttEngine? = null
    val stt: SttEngine get() = sttEngine ?: AndroidSttEngine(app).also { sttEngine = it }

    /** Forget the remembered PC so the app stops reconnecting to it. Unpair in Windows/Android to fully remove it. */
    fun forgetPc() {
        disconnect()
        update { it.copy(lastHost = null) }
    }

    override fun onCleared() {
        sttEngine?.release()
        super.onCleared()
    }

    /** Re-pair the last PC when the transport is ready. */
    fun reconnectLast() {
        if (!settings.value.autoReconnect) return
        val last = settings.value.lastHost ?: return
        bondedHosts().firstOrNull { it.address == last }?.let { app.transport.connect(it) }
    }
}
