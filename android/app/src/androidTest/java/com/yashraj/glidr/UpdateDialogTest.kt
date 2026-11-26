package com.yashraj.glidr

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yashraj.glidr.ui.UpdateDialog
import com.yashraj.glidr.ui.theme.GlidrTheme
import com.yashraj.glidr.update.InstallError
import com.yashraj.glidr.update.InstallStep
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateDialogTest {
    @get:Rule
    val rule = createComposeRule()

    private var step by mutableStateOf<InstallStep?>(null)
    private val pressed = mutableListOf<String>()

    private fun show() = rule.setContent {
        GlidrTheme(light = false, accent = 0, reduceMotion = false) {
            UpdateDialog(
                "9.0.0", "2.2.0", step,
                onUpdate = { pressed += "update" }, onInstall = { pressed += "install" },
                onLater = { pressed += "later" }, onOpenPage = { pressed += "page" },
            )
        }
    }

    @Test
    fun asksBeforeUpdating() {
        show()
        rule.onNodeWithText("Version 9.0.0 is ready. You have 2.2.0.").assertExists()
        rule.onNodeWithText("Later").performClick()
        rule.onNodeWithText("Update").performClick()
        assertEquals(listOf("later", "update"), pressed)
    }

    @Test
    fun followsTheInstall() {
        show()
        step = InstallStep.Downloading(40)
        rule.onNodeWithText("Downloading… 40%").assertExists()
        rule.onNodeWithText("Update").assertDoesNotExist()
        rule.onNodeWithText("Cancel").performClick()

        step = InstallStep.Installing
        rule.onNodeWithText("Installing…").assertExists()
        rule.onNodeWithText("Later").assertDoesNotExist()

        step = InstallStep.Confirm(Intent())
        rule.onNodeWithText("Install").performClick()

        step = InstallStep.Failed(InstallError.Download)
        rule.onNodeWithText("Try again").performClick()
        rule.onNodeWithText("Open release page").performClick()
        assertEquals(listOf("later", "install", "update", "page"), pressed)
    }
}
