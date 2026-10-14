package com.yashraj.glidr.hid

/** Report encoders. Each encoder writes into a caller-owned buffer so the hot path never allocates. */
object HidReports {
    const val BUTTON_LEFT = 1
    const val BUTTON_RIGHT = 2
    const val BUTTON_MIDDLE = 4

    const val MOD_CTRL = 0x01
    const val MOD_SHIFT = 0x02
    const val MOD_ALT = 0x04
    const val MOD_GUI = 0x08
    const val MOD_ALTGR = 0x40 // right alt

    fun mouse(buf: ByteArray, buttons: Int, dx: Int, dy: Int, wheel: Int = 0, pan: Int = 0) {
        buf[0] = buttons.toByte()
        buf[1] = dx.coerceIn(-127, 127).toByte()
        buf[2] = dy.coerceIn(-127, 127).toByte()
        buf[3] = wheel.coerceIn(-127, 127).toByte()
        buf[4] = pan.coerceIn(-127, 127).toByte()
    }

    /** [keys] holds up to six usage codes; unused slots must be 0. */
    fun keyboard(buf: ByteArray, modifiers: Int, k0: Int = 0, k1: Int = 0, k2: Int = 0, k3: Int = 0, k4: Int = 0, k5: Int = 0) {
        buf[0] = modifiers.toByte()
        buf[1] = 0
        buf[2] = k0.toByte()
        buf[3] = k1.toByte()
        buf[4] = k2.toByte()
        buf[5] = k3.toByte()
        buf[6] = k4.toByte()
        buf[7] = k5.toByte()
    }

    fun consumer(buf: ByteArray, usage: Int) {
        buf[0] = (usage and 0xFF).toByte()
        buf[1] = ((usage shr 8) and 0xFF).toByte()
    }
}
