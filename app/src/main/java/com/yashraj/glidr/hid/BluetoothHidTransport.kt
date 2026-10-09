package com.yashraj.glidr.hid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/**
 * [HidTransport] over the Bluetooth HID Device profile. Only works on a physical phone, and only one app
 * can be registered as the HID device at a time.
 */
@SuppressLint("MissingPermission") // every call is guarded by [hasPermission] or catches SecurityException
class BluetoothHidTransport(context: Context) : HidTransport {
    private val app = context.applicationContext
    private val adapter: BluetoothAdapter? = app.getSystemService(BluetoothManager::class.java)?.adapter
    private val executor = Executors.newSingleThreadExecutor()

    private val _state = MutableStateFlow(
        if (adapter == null || !app.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            ConnectionState.Unsupported
        } else {
            ConnectionState.BluetoothOff
        },
    )
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()
    private val _host = MutableStateFlow<PcDevice?>(null)
    override val host: StateFlow<PcDevice?> = _host.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var registerTries = 0
    private var connectTries = 0

    private var hid: BluetoothHidDevice? = null
    private var connected: BluetoothDevice? = null
    private var registered = false
    private var wantConnected: BluetoothDevice? = null
    private var started = false

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            this@BluetoothHidTransport.registered = registered
            Log.d(TAG, "app registered=$registered")
            if (registered) {
                registerTries = 0
                _state.value = ConnectionState.Ready
                wantConnected?.let { hid?.connect(it) }
            } else if (started) {
                _state.value = ConnectionState.Registering
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.d(TAG, "connection ${device.address} state=$state")
            when (state) {
                BluetoothProfile.STATE_CONNECTING -> _state.value = ConnectionState.Connecting
                BluetoothProfile.STATE_CONNECTED -> {
                    connectTries = 0
                    connected = device
                    _host.value = PcDevice(safeName(device), device.address)
                    _state.value = ConnectionState.Connected
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connected?.address == device.address) connected = null
                    _host.value = null
                    if (!started || !registered) {
                        _state.value = ConnectionState.Registering
                    } else if (wantConnected != null && connectTries < MAX_CONNECT_TRIES) {
                        // The PC did not accept (or dropped) the link: retry a few times, then give up quietly.
                        connectTries++
                        _state.value = ConnectionState.Reconnecting
                        val d = wantConnected
                        main.postDelayed({
                            if (started && registered && wantConnected === d && connected == null) {
                                d?.let { hid?.connect(it) }
                            }
                        }, RETRY_DELAY_MS * connectTries)
                    } else {
                        wantConnected = null
                        connectTries = 0
                        _state.value = ConnectionState.Ready
                    }
                }
            }
        }
    }

    private val proxyListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            Log.d(TAG, "HID_DEVICE proxy connected")
            val h = proxy as BluetoothHidDevice
            hid = h
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "Glidr", "Phone as mouse and keyboard", "Glidr",
                BluetoothHidDevice.SUBCLASS1_COMBO, HidDescriptor.BYTES,
            )
            _state.value = ConnectionState.Registering
            val ok = h.registerApp(sdp, null, null, executor, callback)
            Log.d(TAG, "registerApp returned $ok")
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hid = null
                registered = false
                if (started) _state.value = ConnectionState.Registering
            }
        }
    }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                BluetoothAdapter.STATE_OFF -> {
                    tearDown()
                    _state.value = ConnectionState.BluetoothOff
                }
                BluetoothAdapter.STATE_ON -> if (started) openProxy()
            }
        }
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(app, android.Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private fun safeName(d: BluetoothDevice): String = try {
        d.name ?: d.address
    } catch (_: SecurityException) {
        d.address
    }

    override fun start() {
        Log.d(TAG, "start() state=${_state.value} started=$started")
        if (started || _state.value == ConnectionState.Unsupported) return
        started = true
        ContextCompat.registerReceiver(
            app, btReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        openProxy()
    }

    private fun openProxy() {
        val a = adapter ?: return
        if (!hasPermission() || !a.isEnabled) {
            _state.value = ConnectionState.BluetoothOff
            return
        }
        if (hid != null) return
        _state.value = ConnectionState.Registering
        scheduleRegisterWatchdog()
        val proxyOk = a.getProfileProxy(app, proxyListener, BluetoothProfile.HID_DEVICE)
        Log.d(TAG, "getProfileProxy returned $proxyOk")
        if (!proxyOk) {
            Log.d(TAG, "getProfileProxy failed")
            _state.value = ConnectionState.Unsupported
        }
    }

    /** If the profile never reports "registered" (some phones are slow or drop the proxy), start over. */
    private fun scheduleRegisterWatchdog() {
        main.postDelayed({
            if (started && !registered && _state.value == ConnectionState.Registering && registerTries < MAX_REGISTER_TRIES) {
                registerTries++
                Log.d(TAG, "registration stalled, retry $registerTries")
                tearDown()
                openProxy()
            }
        }, REGISTER_TIMEOUT_MS)
    }

    private fun tearDown() {
        try {
            hid?.unregisterApp()
        } catch (_: SecurityException) {
        }
        hid?.let { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        hid = null
        connected = null
        registered = false
        _host.value = null
    }

    override fun stop() {
        if (!started) return
        started = false
        wantConnected = null
        main.removeCallbacksAndMessages(null)
        try {
            app.unregisterReceiver(btReceiver)
        } catch (_: IllegalArgumentException) {
        }
        tearDown()
        if (_state.value != ConnectionState.Unsupported) {
            _state.value = if (adapter?.isEnabled == true) ConnectionState.Registering else ConnectionState.BluetoothOff
        }
    }

    override fun bondedHosts(): List<PcDevice> {
        if (!hasPermission()) return emptyList()
        // Only things that can be a PC: computers, plus devices that do not say what they are.
        // Headphones, controllers, watches and similar are hidden.
        return adapter?.bondedDevices.orEmpty().filter { d ->
            when (d.bluetoothClass?.majorDeviceClass) {
                null, BluetoothClass.Device.Major.COMPUTER, BluetoothClass.Device.Major.UNCATEGORIZED,
                BluetoothClass.Device.Major.MISC, BluetoothClass.Device.Major.PHONE -> true
                else -> false
            }
        }.map {
            PcDevice(safeName(it), it.address, it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.COMPUTER)
        }.sortedByDescending { it.isComputer }
    }

    override fun connect(device: PcDevice) {
        if (!hasPermission()) return
        val d = adapter?.getRemoteDevice(device.address) ?: return
        wantConnected = d
        connectTries = 0
        if (registered) hid?.connect(d)
    }

    override fun disconnect() {
        wantConnected = null
        connected?.let { hid?.disconnect(it) }
    }

    override fun sendMouse(buf: ByteArray) = send(HidDescriptor.REPORT_ID_MOUSE, buf)
    override fun sendKeyboard(buf: ByteArray) = send(HidDescriptor.REPORT_ID_KEYBOARD, buf)
    override fun sendConsumer(buf: ByteArray) = send(HidDescriptor.REPORT_ID_CONSUMER, buf)

    private fun send(id: Int, buf: ByteArray): Boolean {
        val d = connected ?: return false
        return try {
            hid?.sendReport(d, id, buf) == true
        } catch (_: SecurityException) {
            false
        }
    }

    private companion object {
        const val TAG = "Glidr"
        const val REGISTER_TIMEOUT_MS = 5_000L
        const val MAX_REGISTER_TRIES = 3
        const val MAX_CONNECT_TRIES = 3
        const val RETRY_DELAY_MS = 2_500L
    }
}
