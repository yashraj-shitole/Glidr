package com.yashraj.glidr.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yashraj.glidr.R
import com.yashraj.glidr.ui.theme.Glidr
import com.yashraj.glidr.ui.theme.MAX_DIALOG_WIDTH
import com.yashraj.glidr.ui.theme.Radius
import com.yashraj.glidr.ui.theme.Space
import com.yashraj.glidr.update.InstallError
import com.yashraj.glidr.update.InstallStep

/**
 * Asks whether to update, then shows how the update is going. [step] is null until the user says yes. A tap outside
 * the card does nothing, so a finger already on its way to the pad cannot answer by accident.
 */
@Composable
fun UpdateDialog(
    version: String, current: String, step: InstallStep?,
    onUpdate: () -> Unit, onInstall: () -> Unit, onLater: () -> Unit, onOpenPage: () -> Unit,
) {
    val c = Glidr.colors
    val title = stringResource(R.string.update_title)
    val busy = step is InstallStep.Downloading || step == InstallStep.Installing
    BackHandler { if (!busy) onLater() }
    Box(
        Modifier.fillMaxSize().appear().background(Scrim).pointerInput(Unit) { detectTapGestures {} },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(Space.xl).widthIn(max = MAX_DIALOG_WIDTH).fillMaxWidth().appear(y = Space.lg, scale = 0.92f, fade = false)
                .clip(RoundedCornerShape(Radius.sheet)).background(c.s1).border(1.dp, c.line, RoundedCornerShape(Radius.sheet))
                .semantics { paneTitle = title }
                .animateContentSize(motionSpec(Spring.StiffnessMediumLow, 1f)).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Label(title, color = c.text, style = Glidr.type.heading)
            Label(stringResource(R.string.update_body, version, current), color = c.text, style = Glidr.type.body)
            val status = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            when (step) {
                null -> Label(stringResource(R.string.update_hint), color = c.dim, style = Glidr.type.small)
                is InstallStep.Downloading -> {
                    Label(stringResource(R.string.update_downloading, step.percent), color = c.accentText, style = Glidr.type.mono)
                    ProgressBar(step.percent / 100f)
                }
                InstallStep.Installing -> Label(stringResource(R.string.update_installing), status, c.accentText, Glidr.type.body)
                is InstallStep.Confirm -> Label(stringResource(R.string.update_confirm), status, c.accentText, Glidr.type.body)
                is InstallStep.Failed -> Label(stringResource(errorText(step.error)), status, c.dim, Glidr.type.body)
            }
            if (step != InstallStep.Installing) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    PressBox(onLater, Modifier.weight(1f).height(52.dp)) {
                        Label(stringResource(if (step is InstallStep.Downloading) R.string.cancel else R.string.update_later))
                    }
                    if (step !is InstallStep.Downloading) {
                        val action = when (step) {
                            is InstallStep.Confirm -> R.string.update_install
                            is InstallStep.Failed -> R.string.update_retry
                            else -> R.string.update_now
                        }
                        PressBox(
                            if (step is InstallStep.Confirm) onInstall else onUpdate,
                            Modifier.weight(1f).height(52.dp), null, background = c.accent, border = false,
                        ) { Label(stringResource(action), color = c.ink) }
                    }
                }
            }
            if (step is InstallStep.Failed) {
                PressBox(onOpenPage, Modifier.fillMaxWidth().height(52.dp)) { Label(stringResource(R.string.update_open_page)) }
            }
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    val c = Glidr.colors
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), settleSpec(), label = "download")
    Box(Modifier.fillMaxWidth().height(Space.sm).clip(CircleShape).background(c.s3)) {
        Box(Modifier.fillMaxWidth(shown.coerceIn(0f, 1f)).fillMaxHeight().background(c.accent))
    }
}

private fun errorText(error: InstallError) = when (error) {
    InstallError.Download -> R.string.update_error_download
    InstallError.Damaged -> R.string.update_error_damaged
    InstallError.Storage -> R.string.update_error_storage
    InstallError.Refused -> R.string.update_error_refused
}
