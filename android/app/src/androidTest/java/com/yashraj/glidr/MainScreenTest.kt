package com.yashraj.glidr

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /** A fresh install opens on the welcome card; skip it so the tests see the pad. */
    @Before
    fun skipWelcome() {
        rule.waitForIdle()
        if (rule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText("Skip").performClick()
    }

    @Test
    fun padShowsMouseButtonsAndHint() {
        rule.onNodeWithText("LEFT").assertExists()
        rule.onNodeWithText("RIGHT").assertExists()
        rule.onNodeWithContentDescription("Middle click").assertExists()
        rule.onNodeWithText("Tap click · 2 fingers scroll · 3 fingers switch").assertExists()
    }

    @Test
    fun settingsOpensAndGoesBack() {
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Sensitivity").assertExists()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("LEFT").assertExists()
    }

    @Test
    fun mediaTabShowsTransportControls() {
        rule.onNodeWithText("Media").performClick()
        rule.onNodeWithContentDescription("Play or pause").assertExists()
        rule.onNodeWithContentDescription("Next track").assertExists()
    }

    @Test
    fun presentTabShowsSlideButtonsAndTimer() {
        rule.onNodeWithText("Present").performClick()
        rule.onNodeWithContentDescription("Next slide").assertExists()
        rule.onNodeWithContentDescription("Previous slide").assertExists()
        rule.onNodeWithText("0:00").assertExists()
    }

    @Test
    fun keyboardOpensAndSwitchesTabs() {
        rule.onNodeWithContentDescription("Keyboard").performClick()
        rule.onNodeWithText("Esc").assertExists()
        rule.onNodeWithText("Shortcuts").performClick()
        rule.onNodeWithText("Task view").assertExists()
        rule.onNodeWithText("More").performClick()
        rule.onNodeWithText("F12").assertExists()
        rule.onNodeWithText("Keys").performClick()
        rule.onNodeWithText("123").performClick()
        rule.onNodeWithText("ABC").assertExists()
        rule.onNodeWithContentDescription("Keyboard").performClick()
        rule.onNodeWithText("Esc").assertDoesNotExist()
    }

    @Test
    fun padShowsQuickKeys() {
        rule.onNodeWithText("Ctrl").assertExists()
        rule.onNodeWithText("Paste").assertExists()
    }
}
