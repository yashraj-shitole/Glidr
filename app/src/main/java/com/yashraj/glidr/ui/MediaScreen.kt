package com.yashraj.glidr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yashraj.glidr.R
import com.yashraj.glidr.hid.HidEngine
import com.yashraj.glidr.hid.Keys
import com.yashraj.glidr.ui.theme.Glidr

@Composable
fun MediaScreen(engine: HidEngine, haptic: Int, modifier: Modifier = Modifier) {
    val c = Glidr.colors
    val ctx = LocalContext.current
    val haptics = remember { Haptics(ctx) }
    fun send(usage: Int) {
        engine.consumer(usage)
        haptics.play(Haptic.Click, haptic)
    }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.padding(horizontal = 4.dp)) {
            Label(stringResource(R.string.media), color = c.text, style = Glidr.type.title)
            Label(stringResource(R.string.media_sub), color = c.dim, style = Glidr.type.small)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            PressBox({ send(Keys.CONSUMER_PREV) }, Modifier.size(68.dp), stringResource(R.string.prev_track), CircleShape) { Icon(Ico.Prev, c.text, 26.dp) }
            PressBox({ send(Keys.CONSUMER_PLAY_PAUSE) }, Modifier.size(104.dp), stringResource(R.string.play_pause), CircleShape, background = c.accent, border = false) {
                Icon(Ico.Play, c.ink, 38.dp)
            }
            PressBox({ send(Keys.CONSUMER_NEXT) }, Modifier.size(68.dp), stringResource(R.string.next_track), CircleShape) { Icon(Ico.Next, c.text, 26.dp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(Modifier.weight(1f), Ico.Mute, stringResource(R.string.mute)) { send(Keys.CONSUMER_MUTE) }
            Tile(Modifier.weight(1f), Ico.Stop, stringResource(R.string.stop)) { send(Keys.CONSUMER_STOP) }
            Tile(Modifier.weight(1f), Ico.VolDown, stringResource(R.string.vol_down)) { send(Keys.CONSUMER_VOL_DOWN) }
            Tile(Modifier.weight(1f), Ico.VolUp, stringResource(R.string.vol_up)) { send(Keys.CONSUMER_VOL_UP) }
        }
        Row(Modifier.height(1.dp)) {}
    }
}

@Composable
private fun Tile(modifier: Modifier, icon: Ico, label: String, onClick: () -> Unit) {
    val c = Glidr.colors
    PressBox(onClick, modifier.height(76.dp), label, androidx.compose.foundation.shape.RoundedCornerShape(22.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, c.text)
            Label(label, color = c.dim, style = Glidr.type.small)
        }
    }
}
