package com.yashraj.glidr

import com.yashraj.glidr.ui.formatElapsed
import org.junit.Assert.assertEquals
import org.junit.Test

class PresentTest {
    @Test
    fun elapsedTimeFormats() {
        assertEquals("0:00", formatElapsed(0))
        assertEquals("1:15", formatElapsed(75))
        assertEquals("59:59", formatElapsed(3599))
        assertEquals("1:00:00", formatElapsed(3600))
        assertEquals("1:02:05", formatElapsed(3725))
        assertEquals("0:00", formatElapsed(-4))
    }
}
