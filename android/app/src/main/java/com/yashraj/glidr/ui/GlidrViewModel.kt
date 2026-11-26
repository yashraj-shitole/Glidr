package com.yashraj.glidr.ui

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yashraj.glidr.GlidrApp
import com.yashraj.glidr.data.Settings
import com.yashraj.glidr.hid.HidEngine
import com.yashraj.glidr.hid.KeyboardLayout
import com.yashraj.glidr.hid.Keys
import com.yashraj.glidr.hid.PcDevice
import com.yashraj.glidr.service.GlidrService
import com.yashraj.glidr.update.GitHubReleases
import com.yashraj.glidr.update.RELEASES_REPO
import com.yashraj.glidr.update.Release
import com.yashraj.glidr.update.UpdatePrompt
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

/** How old the last update check may be before coming back to the app asks again. */
private const val RECHECK_MS = 6 * 60 * 60 * 1000L

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

    /** True once the saved settings have been read, so first-run UI is not shown to returning users while they load. */
    private val _loaded = MutableStateFlow(false)
    val settingsLoaded: StateFlow<Boolean> = _loaded

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

    private var checking = false
    private var checkedAt = 0L

    /** Moves each time the app comes forward, so a "Later" that has run out is noticed. */
    private val foregrounded = MutableStateFlow(0)

    /** A [quiet] check keeps what is on screen until it has something new to say. */
    fun checkForUpdates(quiet: Boolean = false) {
        if (fromStore || checking) return
        checking = true
        if (!quiet) checked.value = UpdateState.Checking
        viewModelScope.launch {
            val result = GitHubReleases.check(RELEASES_REPO, appVersion)
            if (!quiet || result !is UpdateState.Failed) checked.value = result
            if (result !is UpdateState.Failed) checkedAt = SystemClock.elapsedRealtime()
            checking = false
        }
    }

    /** The app is on screen again: look for a new release if the last answer is old or never came. */
    fun onForeground() {
        foregrounded.value++
        if (checkedAt == 0L || SystemClock.elapsedRealtime() - checkedAt > RECHECK_MS) checkForUpdates(quiet = true)
    }

    /** The release to ask about in a popup, or null. */
    val updatePrompt: StateFlow<Release?> = combine(update, app.settings.flow, foregrounded) { state, s, _ ->
        (state as? UpdateState.Available)?.release?.takeIf {
            UpdatePrompt.shouldAsk(it.version, s.updateSnoozedVersion, s.updateSnoozedUntil, System.currentTimeMillis())
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The update being downloaded or installed, or null. */
    val install = app.updater.state

    /** Downloads and installs inside the app; false when the release has no APK, so the caller should open its page. */
    fun installUpdate(release: Release): Boolean {
        if (!release.hasApk) return false
        app.updater.install(release, "Glidr-Android/$appVersion")
        return true
    }

    /** "Later": drop any download and leave the popup for this version alone for a day. */
    fun postponeUpdate(version: String) {
        app.updater.cancel()
        update { it.copy(updateSnoozedVersion = version, updateSnoozedUntil = System.currentTimeMillis() + UpdatePrompt.SNOOZE_MS) }
    }

    /** Android's install prompt, handed out once so a recreated screen does not open it again. */
    fun takeInstallPrompt(): Intent? = app.updater.takeConfirmation()

    init {
        checkForUpdates() // at app start
    }

    init {
        viewModelScope.launch {
            app.settings.flow.collect { s ->
                engine.motion.sensitivity = s.sensitivity
                engine.motion.acceleration = s.acceleration
                engine.scrollSpeed = s.scrollSpeed
                engine.naturalScroll = s.naturalScroll
                engine.flingEnabled = s.flingScroll
                engine.layout = KeyboardLayout.byId(s.layoutId)
                _loaded.value = true
            }
        }
    }

    fun update(block: (Settings) -> Settings) {
        viewModelScope.launch { app.settings.update(block) }
    }

    /** Back to defaults. The remembered PC and the welcome flag are kept. */
    fun resetSettings() = update { Settings(lastHost = it.lastHost, onboarded = it.onboarded) }

    // ---- presenter: timer, slide keys

    private val presentHaptics by lazy { Haptics(app) }
    private val _presentElapsed = MutableStateFlow(0)
    val presentElapsed: StateFlow<Int> = _presentElapsed
    private val _presentRunning = MutableStateFlow(false)
    val presentRunning: StateFlow<Boolean> = _presentRunning
    private val _buzzIndex = MutableStateFlow(0)
    val buzzIndex: StateFlow<Int> = _buzzIndex
    private val _volumeKeys = MutableStateFlow(true)
    val volumeKeys: StateFlow<Boolean> = _volumeKeys
    private val presenting = MutableStateFlow(false)
    private var presentJob: kotlinx.coroutines.Job? = null

    /** Minutes between buzzes for each choice of the Buzz every control; 0 is off. */
    private val buzzMinutes = intArrayOf(0, 5, 10, 15)

    fun setBuzz(index: Int) { _buzzIndex.value = index.coerceIn(0, buzzMinutes.lastIndex) }
    fun setVolumeKeys(on: Boolean) { _volumeKeys.value = on }

    fun presentToggleTimer() {
        if (_presentRunning.value) {
            presentJob?.cancel()
            _presentRunning.value = false
            return
        }
        _presentRunning.value = true
        presentJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1_000)
                val t = _presentElapsed.value + 1
                _presentElapsed.value = t
                val every = buzzMinutes[_buzzIndex.value] * 60
                // A cue at each interval, strong so it is felt in a pocket or a hand; off only if haptics are off.
                if (every > 0 && t % every == 0 && settings.value.haptics > 0) presentHaptics.play(Haptic.Confirm, 3)
            }
        }
    }

    fun presentResetTimer() {
        presentJob?.cancel()
        _presentRunning.value = false
        _presentElapsed.value = 0
    }

    /** Next slide is Right arrow, previous is Left arrow: what PowerPoint, Slides, Keynote-on-web and PDF viewers use. */
    fun slide(next: Boolean) {
        if (!engine.connected) {
            presentHaptics.play(Haptic.Reject, settings.value.haptics)
            return
        }
        engine.pressKey(if (next) Keys.RIGHT else Keys.LEFT)
        presentHaptics.play(Haptic.Click, settings.value.haptics)
    }

    fun volumeKeysCaptured(): Boolean = presenting.value && _volumeKeys.value

    /** RootScreen tells us when the presenter screen is showing. */
    fun setPresenting(on: Boolean) { presenting.value = on }

    /** Volume keys turn slides, but only on the presenter screen and only if the switch is on. True if handled. */
    fun handleVolumeKey(up: Boolean, repeating: Boolean): Boolean {
        if (!presenting.value || !_volumeKeys.value) return false
        if (!repeating) slide(next = !up)
        return true
    }

    // ---- launch requests from shortcuts and the Quick Settings tile

    private val _pageRequest = MutableStateFlow<String?>(null)
    val pageRequest: StateFlow<String?> = _pageRequest
    fun requestPage(page: String) { _pageRequest.value = page }
    fun pageHandled() { _pageRequest.value = null }

    private var connectRequested = false

    /** Connect to the last PC even if auto-reconnect is off, as soon as the transport is ready. */
    fun requestConnect() {
        if (connection.value == com.yashraj.glidr.hid.ConnectionState.Ready) reconnectLast(force = true) else connectRequested = true
    }

    /** True once if a connect was requested before the transport was ready. */
    fun consumeConnectRequest(): Boolean = connectRequested.also { connectRequested = false }

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
    fun reconnectLast(force: Boolean = false) {
        if (!force && !settings.value.autoReconnect) return
        val last = settings.value.lastHost ?: return
        bondedHosts().firstOrNull { it.address == last }?.let { app.transport.connect(it) }
    }
}
