package com.yashraj.glidr

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

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
}
