package com.mustfa.heatguard

/*
 * ===========================================================================
 *  CheckReceiver.kt - har ~15 minute par ek chhota sa check
 *
 *  Yahan jaan-boojh kar koi foreground service NAHI hai.
 *
 *  Ek app jo garmi aur battery-drain pakadne ke liye khud CPU aur battery
 *  khaye, wo apne hi maqsad ke khilaf hai. Ye receiver har baar bas chand
 *  millisecond chalta hai: battery ka temperature aur level padho, pichhle
 *  sample se tulna karo, aur so jao. AlarmManager ka inexact alarm system ko
 *  doosre wake-ups ke saath jodne deta hai, to phone iske liye alag se jagta
 *  bhi nahi.
 * ===========================================================================
 */

import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CheckReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CHECK = "com.mustfa.heatguard.CHECK"
        const val ACTION_SNOOZE = "com.mustfa.heatguard.SNOOZE"
        const val ACTION_STOP_APP = "com.mustfa.heatguard.STOP_APP"
        const val EXTRA_PACKAGE = "pkg"

        /** "Abhi chup ho ja" ka matlab ek ghanta. */
        private const val SNOOZE_MS = 60L * 60 * 1000
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SNOOZE) {
            Store.snoozeFor(context, SNOOZE_MS)
            context.getSystemService(android.app.NotificationManager::class.java)
                ?.cancel(Notify.ID_ALERT)
            return
        }

        // Notification ka "Band karo" - sirf tab dikhta hai jab Shizuku ho,
        // kyunki bina uske force-stop ho hi nahi sakta.
        if (intent.action == ACTION_STOP_APP) {
            val target = intent.getStringExtra(EXTRA_PACKAGE) ?: return
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            // Binder call hai - broadcast ke main thread par nahi.
            val pending = goAsync()
            Thread {
                val ok = Shell.forceStop(context, target)
                if (ok) {
                    manager?.cancel(Notify.ID_ALERT)
                    // Ab wo app rok di gayi hai; uska purana CPU jod bekaar
                    // hai, warna agla check use phir se doshi bata dega.
                    Store.setCpuStreak(context, "", 0)
                }
                pending.finish()
            }.start()
            return
        }

        // Har check ke baad agla khud set hota hai. Sirf setRepeating par
        // bharosa nahi kiya ja sakta - system usse chup-chaap gira deta hai.
        Scheduler.scheduleNext(context)

        /*
         * Telegram se aaye command pehle - chetavni se pehle.
         *
         * Wajah: agar aapne "/stop com.miui.home" bheja hai, to use pehle
         * chala dena chahiye. Uske baad hone wala check tab tak sudhri hui
         * haalat dekhega aur bekaar ki chetavni nahi bhejega.
         *
         * Network kaam hai, isliye alag thread par aur goAsync() ke saath -
         * BroadcastReceiver ka main thread iske liye nahi hai.
         */
        if (Remote.isEnabled(context) || Link.isControlled(context)) {
            val pending = goAsync()
            Thread {
                runCatching { Remote.poll(context) }
                // Session khud tez poll karti hai. Dono ek saath na chalein.
                if (!ScreenService.running && !ListenService.running) {
                    runCatching { Link.pollAndRun(context) }
                }
                pending.finish()
            }.start()
        }

        if (!Store.isEnabled(context)) return

        val now = VitalsReader.read(context)
        if (!now.valid) return

        val previous = Store.lastSample(context)
        val streak = Store.hotStreak(context)

        /*
         * Per-app CPU: pichhle sample se antar.
         *
         * batterystats jod deta hai, rate nahi - isliye ab ka sample lo,
         * pichhle se ghatao, aur beech ke waqt se baanto. Pehli baar koi
         * pichhla sample nahi hota, to available=false aata hai aur Rules
         * chup-chaap garmi/drain wale niyamon par chala jata hai.
         */
        val cpuNow = CpuInspector.sample()
        val (cpuPrev, cpuPrevTime) = Store.cpuSample(context)
        val cpu = if (cpuNow == null) {
            CpuSnapshot(false, emptyList())
        } else {
            val snapshot = CpuInspector.compare(
                context, cpuPrev, cpuNow, now.timeMs - cpuPrevTime
            )
            Store.saveCpuSample(context, cpuNow, now.timeMs)
            snapshot
        }
        val hogPkg = cpu.top?.packageName.orEmpty()
        val cpuStreak = if (hogPkg.isBlank()) 0 else Store.cpuStreak(context, hogPkg)

        val verdict = Rules.judge(now, previous, streak, cpu, cpuStreak)

        // Sample aur streaks hamesha update hote hain, chahe chetavni de ya na de.
        Store.saveSample(context, now)
        Store.setHotStreak(context, if (verdict.hot) streak + 1 else 0)
        if (hogPkg.isNotBlank() && (cpu.top?.percent ?: 0f) >= 18f) {
            Store.setCpuStreak(context, hogPkg, cpuStreak + 1)
        } else {
            Store.setCpuStreak(context, "", 0)
        }

        if (!verdict.alert) return

        val time = System.currentTimeMillis()
        if (time < Store.snoozeUntil(context)) return
        if (time - Store.lastAlert(context) < ALERT_COOLDOWN_MS) return

        val hint = foregroundHint(context)
        Store.addEvent(
            context,
            HeatEvent(
                timeMs = time,
                reason = verdict.reason,
                tempC = now.tempC,
                drainPerHour = verdict.drainPerHour,
                foregroundApp = hint
            )
        )
        Store.markAlerted(context)
        Notify.alert(context, verdict, now, hint)

        // Wahi chetavni Samsung par bhi. Ye TURANT jaati hai - phone khud
        // bhejta hai, kisi ke poochhne ka intezaar nahi karta.
        if (Remote.isEnabled(context) && Remote.ownerChat(context) != 0L) {
            val pending = goAsync()
            Thread {
                runCatching {
                    Remote.send(
                        context,
                        "Phone garam ho raha hai\n\n" + verdict.reason +
                            "\n\nBattery: " + now.level + "%  |  " +
                            "%.1f".format(now.tempC) + " C" +
                            if (verdict.culprit.isNotBlank()) {
                                "\n\nBand karne ke liye: /stop " + verdict.culprit
                            } else ""
                    )
                }
                pending.finish()
            }.start()
        }
    }

    /**
     * Pichhle ghante mein screen par sabse zyada rehne wali app.
     *
     * Ye CPU ka naap NAHI hai - Android wo deta hi nahi (dekhiye Vitals.kt).
     * Ye sirf ek ishara hai, aur tabhi milta hai jab aapne "Usage access" di
     * ho. Phir bhi kaam ka hai: 25 Sep wala launcher screen par bhi rehta tha,
     * to ye ishara usi taraf ungli uthata.
     */
    private fun foregroundHint(context: Context): String {
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return ""
        val end = System.currentTimeMillis()
        val start = end - 60L * 60 * 1000
        val stats = runCatching {
            usage.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end)
        }.getOrNull() ?: return ""
        if (stats.isEmpty()) return ""

        val top = stats
            .filter { it.totalTimeInForeground > 0 && it.packageName != context.packageName }
            .maxByOrNull { it.totalTimeInForeground } ?: return ""

        val minutes = top.totalTimeInForeground / 60000
        if (minutes < 1) return ""
        return "${labelFor(context, top.packageName)} (${minutes} min)"
    }

    private fun labelFor(context: Context, pkg: String): String = runCatching {
        val packageManager = context.packageManager
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(pkg, 0)
        ).toString()
    }.getOrDefault(pkg)
}
