package com.yashraj.glidr.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yashraj.glidr.GlidrApp
import com.yashraj.glidr.MainActivity
import com.yashraj.glidr.R
import com.yashraj.glidr.data.Settings
import com.yashraj.glidr.hid.ConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that nudges the PC's mouse on a timer so it does not sleep. It is started only when
 * the user turns keep awake on, and stops itself when they turn it off or the auto-stop time is reached.
 */
class KeepAwakeService : Service() {
    private val app get() = application as GlidrApp
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> app.keepAwake.update { it.copy(paused = !it.paused) }
            ACTION_NUDGE -> {
                app.engine.nudge()
                app.keepAwake.update { it.copy(nextInSec = currentEvery) }
            }
            ACTION_STOP -> {
                stopKeepAwake()
                return START_NOT_STICKY
            }
            else -> begin()
        }
        notifyState()
        return START_NOT_STICKY
    }

    private var currentEvery = 30

    private fun begin() {
        if (job?.isActive == true) return
        startForegroundCompat(build())
        job = app.scope.launch {
            val s = app.settings.flow.first()
            currentEvery = Settings.NUDGE_SECONDS[s.nudgeEvery.coerceIn(0, 3)]
            app.keepAwake.set(KeepAwakeState(active = true, nextInSec = currentEvery))
            while (isActive) {
                delay(1000)
                val cfg = app.settings.flow.first()
                currentEvery = Settings.NUDGE_SECONDS[cfg.nudgeEvery.coerceIn(0, 3)]
                val st = app.keepAwake.state.value
                val elapsed = st.elapsedSec + 1
                val limitMin = Settings.AUTO_STOP_MINUTES[cfg.autoStop.coerceIn(0, 3)]
                if (limitMin > 0 && elapsed >= limitMin * 60) {
                    stopKeepAwake()
                    return@launch
                }
                if (st.paused) {
                    app.keepAwake.set(st.copy(elapsedSec = elapsed))
                } else {
                    var next = st.nextInSec - 1
                    if (next <= 0) {
                        val blocked = cfg.pauseWhileTouching && app.keepAwake.touching
                        val connected = app.transport.state.value == ConnectionState.Connected
                        if (!blocked) {
                            if (connected) app.engine.nudge()
                            next = currentEvery
                        } else {
                            next = 1 // retry shortly, never mid-gesture
                        }
                    }
                    app.keepAwake.set(st.copy(elapsedSec = elapsed, nextInSec = next))
                }
                notifyState()
            }
        }
    }

    private fun stopKeepAwake() {
        job?.cancel()
        job = null
        app.keepAwake.set(KeepAwakeState())
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        job?.cancel()
        app.keepAwake.set(KeepAwakeState())
        super.onDestroy()
    }

    private fun notifyState() {
        if (job?.isActive != true) return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build())
    }

    private fun startForegroundCompat(n: Notification) {
        startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
    }

    private fun build(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val st = app.keepAwake.state.value
        val host = app.transport.host.value?.name ?: getString(R.string.your_pc)
        val text = when {
            st.paused -> getString(R.string.notif_paused)
            else -> getString(R.string.notif_next, fmt(st.nextInSec))
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_awake)
            .setContentTitle(getString(R.string.notif_title, host))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(if (st.paused) R.string.resume else R.string.pause), action(ACTION_PAUSE))
            .addAction(0, getString(R.string.nudge_now), action(ACTION_NUDGE))
            .addAction(0, getString(R.string.stop), action(ACTION_STOP))
            .build()
    }

    private fun action(a: String): PendingIntent = PendingIntent.getService(
        this, a.hashCode(), Intent(this, KeepAwakeService::class.java).setAction(a),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun fmt(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

    companion object {
        private const val CHANNEL = "keep_awake"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_PAUSE = "com.yashraj.glidr.PAUSE"
        private const val ACTION_NUDGE = "com.yashraj.glidr.NUDGE"
        private const val ACTION_STOP = "com.yashraj.glidr.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, KeepAwakeService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, KeepAwakeService::class.java).setAction(ACTION_STOP))
        }
    }
}
