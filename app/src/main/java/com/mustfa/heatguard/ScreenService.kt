package com.mustfa.heatguard

/*
 * Screen session. Hamesha nahi chalti.
 *
 * User button dabata hai, tab foreground service chalti hai, 10 minute baad
 * khud band. Beech mein notification rehti hai. ntfy ki limit 1 request /
 * 5 second hai, isliye ek baar tasveer, agli baar command - tasveer ~10
 * second mein badalti hai. Ye video nahi hai.
 */

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager

class ScreenService : Service() {

    private var worker: Thread? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            running = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(
            NOTIFY_ID,
            notification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        if (worker?.isAlive == true) return START_NOT_STICKY
        running = true
        worker = Thread { loop() }.also { it.start() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        wake?.let { if (it.isHeld) it.release() }
        wake = null
        super.onDestroy()
    }

    private fun loop() {
        val power = getSystemService(PowerManager::class.java)
        val lock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "heatguard:screen")
        wake = lock
        lock?.acquire(SESSION_MS)
        val deadline = System.currentTimeMillis() + SESSION_MS
        var sendFrame = true
        try {
            while (running && System.currentTimeMillis() < deadline) {
                if (!Shell.shizukuReady() && !HgAccessibility.isEnabled()) break
                if (sendFrame) {
                    val jpeg = Shell.captureJpeg(this)
                    if (jpeg != null) Link.publishFrame(this, jpeg)
                } else {
                    Link.pollAndRun(this)
                }
                sendFrame = !sendFrame
            }
        } finally {
            running = false
            lock?.let { if (it.isHeld) it.release() }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL,
            getString(R.string.screen_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        manager?.createNotificationChannel(channel)
        val stop = PendingIntent.getService(
            this,
            21,
            Intent(this, ScreenService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_heat)
            .setContentTitle(getString(R.string.screen_notify_title))
            .setContentText(getString(R.string.screen_notify_body))
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(null, getString(R.string.screen_stop), stop).build()
            )
            .build()
    }

    companion object {
        private const val ACTION_STOP = "com.mustfa.heatguard.SCREEN_STOP"
        private const val CHANNEL = "screen_session"
        private const val NOTIFY_ID = 2
        private const val SESSION_MS = 10L * 60 * 1000

        @Volatile
        var running = false

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ScreenService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ScreenService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
