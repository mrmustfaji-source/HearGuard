package com.mustfa.heatguard

/*
 * Jis phone ko control kiya ja raha hai, woh yahin se command sunta hai.
 * Bina iske phone 15 minute soya rehta hai aur button bekaar lagta hai.
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

class ListenService : Service() {

    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            running = false
            /*
             * "Control band" ka matlab sirf ye tez sunna band karna hai.
             *
             * Telegram wala remote yahan jaan-boojh kar band NAHI kiya jata:
             * wo har ~15 minute wale check se chalta rehta hai. Usey poori
             * tarah band karna ho to Telegram screen par "Remote band karo"
             * hai. Warna ek notification tap se door ka control hamesha ke
             * liye kat jata, aur pata bhi na chalta.
             */
            if (Link.isControlled(this)) Link.setRole(this, LinkRole.OFF)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFY_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (worker?.isAlive == true) return START_STICKY
        running = true
        worker = Thread {
            val deadline = System.currentTimeMillis() + SESSION_MS
            while (running && System.currentTimeMillis() < deadline && wanted(this)) {
                /*
                 * Do raaste, ek hi loop.
                 *
                 * Link (ntfy) wala rasta khud 5 second ki raftaar par baandha
                 * hua hai, isliye wo loop ko apne aap dheema rakhta hai.
                 * Telegram par aisi koi rok nahi - agar sirf wahi chalu ho to
                 * ye loop poori raftaar se Telegram ko peetne lagega. Isliye
                 * neeche ka sleep zaroori hai.
                 */
                if (Link.isControlled(this)) runCatching { Link.pollAndRun(this) }
                if (Remote.isPaired(this)) {
                    runCatching { Remote.poll(this) }
                    if (!Link.isControlled(this)) runCatching { Thread.sleep(PACE_MS) }
                }
            }
            running = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }.also { it.start() }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Control", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this, 31,
            Intent(this, ListenService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_heat)
            .setContentTitle(getString(R.string.listen_title))
            .setContentText(getString(R.string.listen_body))
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.listen_stop), stop).build())
            .build()
    }

    companion object {
        private const val ACTION_STOP = "com.mustfa.heatguard.LISTEN_STOP"
        private const val CHANNEL = "listen"
        private const val NOTIFY_ID = 3
        private const val SESSION_MS = 2L * 60 * 60 * 1000

        /** Telegram ko itni der mein ek baar poochha jata hai. */
        private const val PACE_MS = 3000L

        private fun wanted(context: Context): Boolean =
            Link.isControlled(context) || Remote.isPaired(context)

        @Volatile
        var running = false
            private set

        fun start(context: Context) {
            if (!wanted(context)) return
            runCatching {
                context.startForegroundService(Intent(context, ListenService::class.java))
            }
        }
    }
}
