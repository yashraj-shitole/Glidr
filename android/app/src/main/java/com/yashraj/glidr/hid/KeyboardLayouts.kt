package com.yashraj.glidr.hid

/** One key press: a USB HID keyboard usage plus modifier bits from [HidReports]. */
data class KeyStroke(val usage: Int, val modifiers: Int = 0)

/**
 * HID sends key codes, not characters, so text is typed through a per-layout table that must match the
 * layout configured on the PC. Characters that layout cannot produce (Unicode, Devanagari) return null.
 */
class KeyboardLayout(val id: String, val label: String, private val table: Map<Char, KeyStroke>) {
    fun strokeFor(c: Char): KeyStroke? = table[c]

    companion object {
        val US: KeyboardLayout by lazy { KeyboardLayout("us", "US", build(Variant.US)) }
        val UK: KeyboardLayout by lazy { KeyboardLayout("uk", "UK", build(Variant.UK)) }
        val DE: KeyboardLayout by lazy { KeyboardLayout("de", "DE", build(Variant.DE)) }
        val all: List<KeyboardLayout> get() = listOf(US, UK, DE)
        fun byId(id: String?): KeyboardLayout = all.firstOrNull { it.id == id } ?: US
    }

    private enum class Variant { US, UK, DE }
}

private const val SHIFT = HidReports.MOD_SHIFT
private const val ALTGR = HidReports.MOD_ALTGR

private fun build(v: Any): Map<Char, KeyStroke> {
    val name = v.toString()
    val m = HashMap<Char, KeyStroke>(128)

    // Letters. DE swaps Y and Z.
    for (i in 0 until 26) {
        val lower = 'a' + i
        var usage = 0x04 + i
        if (name == "DE") {
            if (lower == 'y') usage = 0x1D else if (lower == 'z') usage = 0x1C
        }
        m[lower] = KeyStroke(usage)
        m[lower.uppercaseChar()] = KeyStroke(usage, SHIFT)
    }
    // Digits 1..9, 0 on usages 0x1E..0x27.
    for (i in 0 until 10) {
        val digit = ('1' + i).let { if (i == 9) '0' else it }
        m[digit] = KeyStroke(0x1E + i)
    }
    m[' '] = KeyStroke(0x2C)
    m['\n'] = KeyStroke(0x28)
    m['\t'] = KeyStroke(0x2B)

    when (name) {
        "US" -> {
            "!@#$%^&*()".forEachIndexed { i, c -> m[c] = KeyStroke(0x1E + i, SHIFT) }
            pairs(m, 0x2D, '-', '_'); pairs(m, 0x2E, '=', '+'); pairs(m, 0x2F, '[', '{')
            pairs(m, 0x30, ']', '}'); pairs(m, 0x31, '\\', '|'); pairs(m, 0x33, ';', ':')
            pairs(m, 0x34, '\'', '"'); pairs(m, 0x35, '`', '~'); pairs(m, 0x36, ',', '<')
            pairs(m, 0x37, '.', '>'); pairs(m, 0x38, '/', '?')
        }
        "UK" -> {
            "!\"£$%^&*()".forEachIndexed { i, c -> m[c] = KeyStroke(0x1E + i, SHIFT) }
            m['€'] = KeyStroke(0x21, ALTGR)
            pairs(m, 0x2D, '-', '_'); pairs(m, 0x2E, '=', '+'); pairs(m, 0x2F, '[', '{')
            pairs(m, 0x30, ']', '}'); pairs(m, 0x32, '#', '~'); pairs(m, 0x33, ';', ':')
            pairs(m, 0x34, '\'', '@'); pairs(m, 0x35, '`', '¬'); pairs(m, 0x36, ',', '<')
            pairs(m, 0x37, '.', '>'); pairs(m, 0x38, '/', '?'); pairs(m, 0x64, '\\', '|')
        }
        else -> { // DE
            "!\"§$%&/()=".forEachIndexed { i, c -> m[c] = KeyStroke(0x1E + i, SHIFT) }
            pairs(m, 0x2D, 'ß', '?'); pairs(m, 0x30, '+', '*'); pairs(m, 0x32, '#', '\'')
            pairs(m, 0x33, 'ö', 'Ö'); pairs(m, 0x34, 'ä', 'Ä'); pairs(m, 0x2F, 'ü', 'Ü')
            pairs(m, 0x36, ',', ';'); pairs(m, 0x37, '.', ':'); pairs(m, 0x38, '-', '_')
            pairs(m, 0x64, '<', '>')
            m['@'] = KeyStroke(0x14, ALTGR); m['€'] = KeyStroke(0x08, ALTGR)
            m['{'] = KeyStroke(0x24, ALTGR); m['['] = KeyStroke(0x25, ALTGR)
            m[']'] = KeyStroke(0x26, ALTGR); m['}'] = KeyStroke(0x27, ALTGR)
            m['\\'] = KeyStroke(0x2D, ALTGR); m['~'] = KeyStroke(0x30, ALTGR)
            m['|'] = KeyStroke(0x64, ALTGR)
        }
    }
    return m
}

private fun pairs(m: MutableMap<Char, KeyStroke>, usage: Int, plain: Char, shifted: Char) {
    m[plain] = KeyStroke(usage)
    m[shifted] = KeyStroke(usage, SHIFT)
}

/** Usages for non-character keys and consumer-control codes. */
object Keys {
    const val ENTER = 0x28
    const val ESC = 0x29
    const val BACKSPACE = 0x2A
    const val TAB = 0x2B
    const val SPACE = 0x2C
    const val RIGHT = 0x4F
    const val LEFT = 0x50
    const val DOWN = 0x51
    const val UP = 0x52
    const val F4 = 0x3D
    const val L = 0x0F
    const val D = 0x07
    const val R = 0x15

    const val CONSUMER_PLAY_PAUSE = 0x00CD
    const val CONSUMER_NEXT = 0x00B5
    const val CONSUMER_PREV = 0x00B6
    const val CONSUMER_STOP = 0x00B7
    const val CONSUMER_MUTE = 0x00E2
    const val CONSUMER_VOL_UP = 0x00E9
    const val CONSUMER_VOL_DOWN = 0x00EA
}
