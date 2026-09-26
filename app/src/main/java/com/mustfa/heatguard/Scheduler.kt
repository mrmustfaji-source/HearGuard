package com.mustfa.heatguard

/*
 * ===========================================================================
 *  Scheduler.kt - agla check kab
 * ===========================================================================
 */

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Scheduler {

    /**
     * Do check ke beech ka waqt.
     *
     * 15 minute jaan-boojh kar. Jis problem ke liye ye app hai wo ghanton
     * chalti hai (pichhli baar 3 din), to har minute dekhne ka koi faayda
     * nahi - ulta battery hi kharch hogi. 15 minute par do lagataar readings
     * ka matlab hai aadhe ghante mein pata chal jayega. Pehle 3 din lagte the.
     */
    private const val INTERVAL_MS = 15L * 60 * 1000

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, CheckReceiver::class.java).setAction(CheckReceiver.ACTION_CHECK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun scheduleNext(context: Context) {
        if (!Store.isEnabled(context)) return
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        // set() inexact hai: system ise doosre wake-ups ke saath jod deta hai.
        // Exact alarm ki zaroorat hi nahi - kuch minute idhar-udhar se koi
        // farak nahi padta - aur exact alarm Android 12+ par alag permission
        // maangta hai jo user ko manually deni padti.
        alarm.set(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + INTERVAL_MS,
            pending(context)
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context))
    }
}
