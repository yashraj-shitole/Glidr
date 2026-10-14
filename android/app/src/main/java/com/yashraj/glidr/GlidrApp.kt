package com.yashraj.glidr

import android.app.Application
import com.yashraj.glidr.data.SettingsRepository
import com.yashraj.glidr.hid.BluetoothHidTransport
import com.yashraj.glidr.hid.HidEngine
import com.yashraj.glidr.hid.HidTransport
import com.yashraj.glidr.companion.CompanionLink
import com.yashraj.glidr.service.KeepAwake
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-wide singletons shared by the UI and the foreground service. */
class GlidrApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var settings: SettingsRepository
        private set
    lateinit var transport: HidTransport
        private set
    lateinit var engine: HidEngine
        private set
    val keepAwake = KeepAwake()
    lateinit var companion: CompanionLink
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        transport = BluetoothHidTransport(this)
        engine = HidEngine(transport, scope)
        companion = CompanionLink(this, scope)
    }
}
