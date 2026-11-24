package com.yashraj.glidr.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.yashraj.glidr.GlidrApp
import com.yashraj.glidr.LaunchExtras
import com.yashraj.glidr.MainActivity
import com.yashraj.glidr.R
import com.yashraj.glidr.hid.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Quick Settings tile. Lit while connected, and it follows the connection while the shade is open. Tapping it while
 * connected disconnects. Tapping it otherwise opens Glidr, which connects to the last PC (so Bluetooth work and the
 * foreground service always start from a visible app, never from the background).
 */
class GlidrTileService : TileService() {
    private val app get() = application as GlidrApp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        job?.cancel()
        job = scope.launch {
            combine(app.transport.state, app.transport.host) { s, h -> s to h?.name }.collect { (s, name) -> render(s, name) }
        }
    }

    override fun onStopListening() {
        job?.cancel()
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(state: ConnectionState, host: String?) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile)
        tile.label = getString(R.string.app_name)
        val (st, sub) = when (state) {
            ConnectionState.Connected -> Tile.STATE_ACTIVE to getString(R.string.notif_connected, host ?: getString(R.string.your_pc))
            ConnectionState.Unsupported -> Tile.STATE_UNAVAILABLE to getString(R.string.state_unsupported)
            ConnectionState.BluetoothOff -> Tile.STATE_INACTIVE to getString(R.string.state_bt_off)
            ConnectionState.Connecting, ConnectionState.Reconnecting, ConnectionState.Registering ->
                Tile.STATE_INACTIVE to getString(R.string.state_connecting)
            ConnectionState.Ready, ConnectionState.Failed -> Tile.STATE_INACTIVE to getString(R.string.tile_tap_connect)
        }
        tile.state = st
        tile.subtitle = sub
        tile.updateTile()
    }

    override fun onClick() {
        if (app.transport.state.value == ConnectionState.Connected) {
            app.transport.disconnect()
            return
        }
        if (isLocked) unlockAndRun { openApp() } else openApp()
    }

    // The Intent overload is only used below API 34, where the PendingIntent one does not exist yet.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(LaunchExtras.CONNECT, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
