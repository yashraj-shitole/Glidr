package com.yashraj.glidr

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yashraj.glidr.ui.Reveal
import com.yashraj.glidr.ui.theme.GlidrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Reduce motion must turn the animations off, not just shorten them. */
@RunWith(AndroidJUnit4::class)
class MotionTest {
    @get:Rule
    val rule = createComposeRule()

    private var open by mutableStateOf(true)

    private fun show(reduceMotion: Boolean) {
        rule.setContent {
            GlidrTheme(light = false, accent = 0, reduceMotion = reduceMotion) {
                Reveal(open) { Box(Modifier.width(100.dp).height(200.dp).testTag("block")) }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { open = false }
        // Two frames: one to recompose, one for the transition to apply. No spring gets anywhere near done.
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
    }

    private fun blocks() = rule.onAllNodes(hasTestTag("block")).fetchSemanticsNodes().size

    @Test
    fun revealAnimatesByDefault() {
        show(reduceMotion = false)
        assertEquals("still folding away", 1, blocks())
    }

    @Test
    fun reduceMotionRemovesAtOnce() {
        show(reduceMotion = true)
        assertEquals(0, blocks())
    }
}
