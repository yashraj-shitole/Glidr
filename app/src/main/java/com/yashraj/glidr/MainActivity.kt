package com.yashraj.glidr

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yashraj.glidr.hid.ConnectionState
import com.yashraj.glidr.ui.GlidrViewModel
import com.yashraj.glidr.ui.Haptic
import com.yashraj.glidr.ui.Haptics
import com.yashraj.glidr.ui.RootScreen
import com.yashraj.glidr.ui.theme.GlidrTheme

class MainActivity : ComponentActivity() {
    private val vm: GlidrViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val s by vm.settings.collectAsStateWithLifecycle()
            val conn by vm.connection.collectAsStateWithLifecycle()
            var granted by remember { mutableStateOf(hasBtPermission()) }

            val btPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                granted = ok
                if (ok) vm.startTransport()
            }
            LaunchedEffect(s.lightTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (s.lightTheme) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) else SystemBarStyle.dark(Color.TRANSPARENT),
                    navigationBarStyle = if (s.lightTheme) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) else SystemBarStyle.dark(Color.TRANSPARENT),
                )
            }
            val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.toggleKeepAwake() }

            LaunchedEffect(granted) { if (granted) vm.startTransport() }
            var micGranted by remember { mutableStateOf(hasPermission(Manifest.permission.RECORD_AUDIO)) }
            val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }
            val haptics = remember { Haptics(this@MainActivity) }
            // Android's "remove animations" system setting also turns motion off.
            val systemNoAnim = remember {
                AndroidSettings.Global.getFloat(contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }

            LaunchedEffect(conn) {
                if (conn == ConnectionState.Ready) vm.reconnectLast()
                if (conn == ConnectionState.Connected) {
                    vm.ensureServiceRunning()
                    haptics.play(Haptic.Confirm, s.haptics)
                }
            }

            GlidrTheme(light = s.lightTheme, accent = s.accent, reduceMotion = s.reduceMotion || systemNoAnim) {
                RootScreen(
                    vm = vm,
                    hasBluetoothPermission = granted,
                    onRequestBluetooth = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) btPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    },
                    onMakeDiscoverable = {
                        startActivity(
                            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120),
                        )
                    },
                    onOpenBluetoothSettings = { startActivity(Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS)) },
                    hasMic = micGranted,
                    onRequestMic = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                    onToggleKeepAwake = {
                        val needsNotif = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        if (needsNotif && !vm.keepAwake.value.active) notif.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.toggleKeepAwake()
                    },
                )
            }
        }
    }

    private fun hasPermission(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun hasBtPermission() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
}
