package com.yashraj.glidr.hid

import kotlinx.coroutines.flow.StateFlow

enum class ConnectionState { Unsupported, BluetoothOff, Registering, Ready, Connecting, Connected, Reconnecting }

data class PcDevice(val name: String, val address: String)

/** All HID access goes through this interface so the engine is testable and other transports can be added. */
interface HidTransport {
    val state: StateFlow<ConnectionState>
    val host: StateFlow<PcDevice?>

    fun start()
    fun stop()

    /** Paired PCs the user can connect to. */
    fun bondedHosts(): List<PcDevice>
    fun connect(device: PcDevice)
    fun disconnect()

    /** [buf] is a [HidDescriptor.MOUSE_SIZE] payload built with [HidReports.mouse]. */
    fun sendMouse(buf: ByteArray): Boolean
    fun sendKeyboard(buf: ByteArray): Boolean
    fun sendConsumer(buf: ByteArray): Boolean
}
