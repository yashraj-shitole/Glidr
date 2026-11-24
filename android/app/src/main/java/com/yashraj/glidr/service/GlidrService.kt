package com.yashraj.glidr.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yashraj.glidr.GlidrApp
import com.yashraj.glidr.MainActivity
import com.yashraj.glidr.R
import com.yashraj.glidr.data.Settings
import com.yashraj.glidr.hid.ConnectionState
import com.yashraj.glidr.hid.Keys
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service (type connectedDevice). It owns the HID connection lifetime and the keep-awake
 * scheduler. It is started from the foreground UI only, shows an ongoing notification, and stops itself
 * once the PC is disconnected and keep awake is off.
 */
class GlidrService : Service() {
    private val app get() = application as GlidrApp
    private var watcher: Job? = null
    private var awakeJob: Job? = null
    private var foreground = false
    private var everySec = 30

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_AWAKE_START -> {
                ensureForeground()
                startAwake()
            }
            ACTION_AWAKE_STOP -> stopAwake()
            ACTION_PAUSE -> app.keepAwake.update { it.copy(paused = !it.paused) }
            ACTION_MEDIA_PREV -> media(Keys.CONSUMER_PREV)
            ACTION_MEDIA_PLAY -> media(Keys.CONSUMER_PLAY_PAUSE)
            ACTION_MEDIA_NEXT -> media(Keys.CONSUMER_NEXT)
            ACTION_NUDGE -> {
                app.scope.launch { app.engine.nudge(app.settings.flow.first().nudgeRoam) }
                app.keepAwake.update { it.copy(nextInSec = everySec) }
            }
            else -> {
                ensureForeground()
                app.transport.start()
                startWatcher()
            }
        }
        refresh()
        return START_NOT_STICKY
    }

    /** Lock-screen remote: sends a media key straight from the notification, with no unlocking or opening the app. */
    private fun media(usage: Int) {
        if (app.transport.state.value == ConnectionState.Connected) app.engine.consumer(usage)
    }

    private fun ensureForeground() {
        if (foreground) return
        foreground = true
        startForeground(NOTIFICATION_ID, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
    }

    /** Stops the service a short while after the PC drops, unless keep awake still needs it. */
    private fun startWatcher() {
        if (watcher?.isActive == true) return
        watcher = app.scope.launch {
            combine(app.transport.state, app.keepAwake.state) { c, a -> c to a }.collectLatest { (conn, awake) ->
                refresh()
                if (conn != ConnectionState.Connected && !awake.active) {
                    delay(IDLE_GRACE_MS)
                    shutdown()
                }
            }
        }
    }

    private fun startAwake() {
        if (awakeJob?.isActive == true) return
        awakeJob = app.scope.launch {
            everySec = Settings.NUDGE_SECONDS[app.settings.flow.first().nudgeEvery.coerceIn(0, 3)]
            app.keepAwake.set(KeepAwakeState(active = true, nextInSec = everySec))
            startWatcher()
            while (isActive) {
                delay(1000)
                val cfg = app.settings.flow.first()
                everySec = Settings.NUDGE_SECONDS[cfg.nudgeEvery.coerceIn(0, 3)]
                val limitMin = Settings.AUTO_STOP_MINUTES[cfg.autoStop.coerceIn(0, 3)]
                if (limitMin > 0 && app.keepAwake.state.value.elapsedSec + 1 >= limitMin * 60) {
                    notifyAutoStopped()
                    stopAwake()
                    return@launch
                }
                // Atomic read-modify-write: the notification's Pause / Nudge now actions change this state from
                // the main thread, and a plain read-then-set here could silently undo them.
                var nudgeNow = false
                app.keepAwake.update { st ->
                    nudgeNow = false
                    when {
                        !st.active -> st
                        st.paused -> st.copy(elapsedSec = st.elapsedSec + 1)
                        else -> {
                            var next = st.nextInSec - 1
                            if (next <= 0) {
                                if (cfg.pauseWhileTouching && app.keepAwake.touching) {
                                    next = 1 // retry shortly, never mid-gesture
                                } else {
                                    nudgeNow = true
                                    next = everySec
                                }
                            }
                            st.copy(elapsedSec = st.elapsedSec + 1, nextInSec = next)
                        }
                    }
                }
                if (nudgeNow && app.transport.state.value == ConnectionState.Connected) app.engine.nudge(cfg.nudgeRoam)
                refresh()
            }
        }
    }

    private fun stopAwake() {
        awakeJob?.cancel()
        awakeJob = null
        app.keepAwake.set(KeepAwakeState())
        if (app.transport.state.value != ConnectionState.Connected) shutdown() else refresh()
    }

    private fun shutdown() {
        awakeJob?.cancel()
        watcher?.cancel()
        app.keepAwake.set(KeepAwakeState())
        stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    override fun onDestroy() {
        awakeJob?.cancel()
        watcher?.cancel()
        app.keepAwake.set(KeepAwakeState())
        super.onDestroy()
    }

    private fun refresh() {
        if (!foreground) return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build())
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    /** Tells the user that keep awake ended on its own, so a PC that later falls asleep is not a mystery. */
    private fun notifyAutoStopped() {
        val nm = getSystemService(NotificationManager::class.java)
        ensureChannel(nm)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            NOTIFICATION_ID_STOPPED,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_awake)
                .setContentTitle(getString(R.string.autostop_title))
                .setContentText(getString(R.string.autostop_text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build(),
        )
    }

    private fun build(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        ensureChannel(nm)
        val st = app.keepAwake.state.value
        val conn = app.transport.state.value
        val host = app.transport.host.value?.name ?: getString(R.string.your_pc)
        val title = when {
            st.active -> getString(R.string.notif_title, host)
            conn == ConnectionState.Connected -> getString(R.string.notif_connected, host)
            else -> getString(R.string.notif_waiting)
        }
        val text = when {
            st.active && st.paused -> getString(R.string.notif_paused)
            st.active -> getString(R.string.notif_next, fmt(st.nextInSec))
            else -> getString(R.string.notif_idle)
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_awake)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (st.active) {
            if (!st.paused) b.setProgress(everySec, (everySec - st.nextInSec).coerceIn(0, everySec), false)
            b.addAction(0, getString(if (st.paused) R.string.resume else R.string.pause), action(ACTION_PAUSE))
            b.addAction(0, getString(R.string.nudge_now), action(ACTION_NUDGE))
            b.addAction(0, getString(R.string.stop), action(ACTION_AWAKE_STOP))
            // Android 16 Live Update: ask for promotion to the status-bar chip and lock screen.
            b.addExtras(
                Bundle().apply {
                    putBoolean("android.requestPromotedOngoing", true)
                    putString("android.shortCriticalText", fmt(st.nextInSec))
                },
            )
        } else if (conn == ConnectionState.Connected) {
            // Not keeping awake: the three action slots become a media remote. Plain actions, not MediaStyle, so
            // Android's own media session and its controls stay untouched.
            b.addAction(0, getString(R.string.notif_media_prev), action(ACTION_MEDIA_PREV))
            b.addAction(0, getString(R.string.notif_media_play), action(ACTION_MEDIA_PLAY))
            b.addAction(0, getString(R.string.notif_media_next), action(ACTION_MEDIA_NEXT))
        }
        return b.build()
    }

    private fun action(a: String): PendingIntent = PendingIntent.getService(
        this, a.hashCode(), Intent(this, GlidrService::class.java).setAction(a),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun fmt(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

    companion object {
        private const val CHANNEL = "glidr_status"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_ID_STOPPED = 2
        private const val IDLE_GRACE_MS = 20_000L
        private const val ACTION_MEDIA_PREV = "com.yashraj.glidr.MEDIA_PREV"
        private const val ACTION_MEDIA_PLAY = "com.yashraj.glidr.MEDIA_PLAY"
        private const val ACTION_MEDIA_NEXT = "com.yashraj.glidr.MEDIA_NEXT"
        private const val ACTION_PAUSE = "com.yashraj.glidr.PAUSE"
        private const val ACTION_NUDGE = "com.yashraj.glidr.NUDGE"
        private const val ACTION_AWAKE_START = "com.yashraj.glidr.AWAKE_START"
        private const val ACTION_AWAKE_STOP = "com.yashraj.glidr.AWAKE_STOP"

        /** Keeps the connection alive in the background. Call only from the foreground UI. */
        fun ensureRunning(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, GlidrService::class.java))
        }

        fun startKeepAwake(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, GlidrService::class.java).setAction(ACTION_AWAKE_START))
        }

        fun stopKeepAwake(context: Context) {
            context.startService(Intent(context, GlidrService::class.java).setAction(ACTION_AWAKE_STOP))
        }
    }
}
