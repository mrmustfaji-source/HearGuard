package com.mustfa.heatguard

/*
 * ===========================================================================
 *  Notify.kt - chetavni, aur uske saath seedha kaam ka rasta
 *
 *  Sirf "phone garam hai" bol dena bekaar hai - aap pehle se jaante hain ki
 *  garam hai, haath mein hai. Kaam ki baat ye hai ki KAUN kar raha hai, aur
 *  wo sirf Settings ki Battery screen par dikhta hai. Isliye notification par
 *  seedha usi screen ka button hai.
 * ===========================================================================
 */

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object Notify {

    private const val CHANNEL_ALERT = "heat_alert"
    const val ID_ALERT = 1

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ALERT,
            context.getString(R.string.channel_alert),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_alert_desc)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Battery screen kholne ke raaste, pehli pasand se aakhri tak.
     *
     * Pehla version `resolveActivity()` par bharosa karta tha aur app CRASH
     * ho gayi thi:
     *
     *     ActivityNotFoundException: Unable to find explicit activity class
     *     {com.miui.powerkeeper/com.miui.powerkeeper.ui.HiddenAppsConfigActivity}
     *
     * Sabak: ek EXPLICIT component wale intent par `resolveActivity()` component
     * wapas kar deta hai bina ye dekhe ki wo activity maujood bhi hai ya nahi.
     * Isliye ab koi bharosa nahi - [startSafely] har raasta asli mein chala kar
     * dekhta hai, aur na chale to agle par chala jata hai.
     *
     * Ye teeno is phone par `cmd package resolve-activity` se jaanche gaye hain:
     *   POWER_USAGE_SUMMARY          -> com.miui.powercenter.PowerMainActivity
     *   IGNORE_BATTERY_OPTIMIZATION  -> Settings$AppBatteryUsageActivity
     *   SETTINGS                     -> com.android.settings.MiuiSettings
     */
    fun batteryScreenCandidates(): List<Intent> = listOf(
        Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    ).map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    /** Notification ke PendingIntent ke liye - wahan try/catch nahi chalta. */
    fun batteryScreenIntent(context: Context): Intent =
        Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Ek ke baad ek chala kar dekho, jo chale wahi.
     *
     * @return false matlab ek bhi nahi chala - caller user ko bata sake.
     */
    fun startSafely(context: Context, candidates: List<Intent>): Boolean {
        for (intent in candidates) {
            val ok = runCatching { context.startActivity(intent) }.isSuccess
            if (ok) return true
        }
        return false
    }

    /**
     * Ek app ke apne "App info" page ka intent - wahin "Force stop" ka button
     * hota hai.
     *
     * Ye app khud force-stop nahi kar sakti: uske liye FORCE_STOP_PACKAGES
     * chahiye, jiska protection level is phone par `signature|privileged` hai
     * - yaani wo sirf firmware ke saath sign hui apps ko milti hai, adb se
     * bhi nahi. To agla behtareen kaam yahi hai: aapko seedha us button tak
     * pahuncha dena, do tap mein.
     */
    fun appInfoIntent(context: Context, pkg: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", pkg, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun alert(context: Context, verdict: Verdict, vitals: Vitals, foregroundHint: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context)

        // Culprit ka naam pata ho to seedha uske page par bhejo; warna
        // battery usage list par, jahan naam khud dhoondhna padega.
        val primary = if (verdict.culprit.isNotBlank()) {
            appInfoIntent(context, verdict.culprit)
        } else {
            batteryScreenIntent(context)
        }
        val openBattery = PendingIntent.getActivity(
            context, 10, primary,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openApp = PendingIntent.getActivity(
            context, 11,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val snooze = PendingIntent.getBroadcast(
            context, 12,
            Intent(context, CheckReceiver::class.java).setAction(CheckReceiver.ACTION_SNOOZE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val named = verdict.culprit.isNotBlank()
        val body = buildString {
            append(verdict.reason)
            // Foreground ishara sirf tab kaam ka hai jab naam pata na ho.
            // Naam pata hone par wo shor hai.
            if (!named && foregroundHint.isNotBlank()) {
                append("\n\n")
                append(context.getString(R.string.hint_foreground, foregroundHint))
            }
            append("\n\n")
            append(
                context.getString(
                    if (named) R.string.hint_what_to_do_named else R.string.hint_what_to_do
                )
            )
        }

        val notification = Notification.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_heat)
            .setContentTitle(
                if (named) {
                    context.getString(R.string.alert_title_named, label(context, verdict.culprit))
                } else {
                    context.getString(R.string.alert_title)
                }
            )
            .setContentText(verdict.reason)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(false)
            .setOngoing(false)
            /*
             * Pehla button do mein se ek hota hai:
             *
             *   Shizuku hai  -> "Abhi band karo": ek tap, app sach mein ruk
             *                   jaati hai (am force-stop shell ke adhikaar se)
             *   Shizuku nahi -> us app ke Settings page ka rasta, jahan
             *                   Force stop ka button khud dabana padta hai
             *
             * Jo button kaam na kar sake, wo dikhana hi nahi chahiye - isliye
             * ye faisla yahan, banate waqt hota hai.
             */
            .addAction(
                if (named && Shell.shizukuReady()) {
                    val stop = PendingIntent.getBroadcast(
                        context, 13,
                        Intent(context, CheckReceiver::class.java)
                            .setAction(CheckReceiver.ACTION_STOP_APP)
                            .putExtra(CheckReceiver.EXTRA_PACKAGE, verdict.culprit),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    Notification.Action.Builder(
                        null, context.getString(R.string.action_stop_now), stop
                    ).build()
                } else {
                    Notification.Action.Builder(
                        null,
                        context.getString(
                            if (named) R.string.action_force_stop else R.string.action_open_battery
                        ),
                        openBattery
                    ).build()
                }
            )
            .addAction(
                Notification.Action.Builder(
                    null, context.getString(R.string.action_snooze), snooze
                ).build()
            )
            .build()

        manager.notify(ID_ALERT, notification)
    }

    /** Package ka dikhne wala naam. Na mile to package hi sahi. */
    fun label(context: Context, pkg: String): String = runCatching {
        val packageManager = context.packageManager
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(pkg, 0)
        ).toString()
    }.getOrDefault(pkg)
}
