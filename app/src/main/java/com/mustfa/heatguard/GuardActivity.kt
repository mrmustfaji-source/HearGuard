package com.mustfa.heatguard

/*
 * ===========================================================================
 *  GuardActivity.kt - garmi aur CPU ka pehra (app ka asli maqsad)
 *
 *  YE SCREEN KYUN WAPAS BANI
 *  -------------------------
 *  Beech mein MainActivity ko remote-control pad bana diya gaya tha, aur us
 *  chakkar mein ye poori screen gayab ho gayi. Nuksaan sirf dikhne ka nahi
 *  tha - `Store.setEnabled()` ko bulane wala EK HI button isi screen par tha.
 *
 *  Uske bina CheckReceiver ki pehli line:
 *
 *      if (!Store.isEnabled(context)) return
 *
 *  hamesha sach hoti thi. Yaani har 15 minute wala check, garmi ki chetavni,
 *  CPU khane wali app ka naam - sab kuch chup-chaap band pada tha, jabki app
 *  bani hi isi ke liye thi (25 Sep: launcher 3 din 19 ghante CPU kha gaya
 *  aur 3 din tak pata nahi chala).
 *
 *  Isliye ye screen ab alag hai, MainActivity ke andar nahi - taki agli baar
 *  koi MainActivity ka layout badle to ye saath mein na mit jaye.
 * ===========================================================================
 */

import android.app.AppOpsManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class GuardActivity : HgActivity() {

    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = UiPref.t(this, "Heat & CPU guard", "Garmi aur CPU ka pehra")
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(28))
        }
        setContentView(ScrollView(this).apply { addView(container) })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        container.removeAllViews()

        val on = Store.isEnabled(this)

        container.addView(heading(getString(if (on) R.string.status_on else R.string.status_off)))
        container.addView(note(getString(R.string.subtitle)))

        /*
         * Ye button hi is screen ke hone ki asli wajah hai.
         *
         * Store.setEnabled ko poore project mein sirf yahin se bulaya jata
         * hai. Ye na ho to pehra kabhi shuru hi nahi hota.
         */
        container.addView(button(getString(if (on) R.string.action_stop else R.string.action_start)) {
            val turningOn = !Store.isEnabled(this)
            Store.setEnabled(this, turningOn)
            if (turningOn) {
                Perms.ask(this)
                // Pehla sample abhi le lo, taki agle check ke paas tulna
                // karne ko kuch ho - warna pehla check bekaar jata hai.
                Store.saveSample(this, VitalsReader.read(this))
                Store.setHotStreak(this, 0)
                Scheduler.scheduleNext(this)
            } else {
                Scheduler.cancel(this)
            }
            render()
        })

        /* ---- Abhi ka haal ---- */

        val now = VitalsReader.read(this)
        val previous = Store.lastSample(this)
        val drain = Rules.drainPerHour(now, previous)

        container.addView(TextView(this).apply {
            textSize = 16f
            setPadding(0, dp(16), 0, dp(6))
            text = buildString {
                append(getString(R.string.label_temp, now.tempC))
                append("\n")
                append(getString(R.string.label_level, now.level))
                if (now.charging) append(getString(R.string.label_charging))
                append("\n")
                if (drain > 0f) append(getString(R.string.label_drain, drain))
                else append(getString(R.string.label_drain_unknown))
                if (!now.headroom.isNaN()) {
                    append("\n")
                    append(getString(R.string.label_headroom, now.headroom * 100f))
                }
            }
        })

        /* ---- Kaun sa mode, aur kaun CPU kha raha hai ---- */

        container.addView(note(modeText()))

        /* ---- Buttons ---- */

        container.addView(button(getString(R.string.action_open_battery)) {
            if (!Notify.startSafely(this, Notify.batteryScreenCandidates())) {
                toast(getString(R.string.msg_no_screen))
            }
        })

        if (!hasUsageAccess()) {
            container.addView(button(getString(R.string.action_usage_access)) {
                if (!Notify.startSafely(this, listOf(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)))) {
                    toast(getString(R.string.msg_no_screen))
                }
            })
        }

        /* ---- Ab tak kya pakda ---- */

        container.addView(heading(getString(R.string.history_title)))
        val events = Store.history(this)
        container.addView(TextView(this).apply {
            textSize = 13.5f
            text = if (events.isEmpty()) {
                getString(R.string.history_empty)
            } else {
                events.joinToString("\n\n") { event ->
                    buildString {
                        append(DateUtils.getRelativeTimeSpanString(event.timeMs))
                        append("\n")
                        append(event.reason)
                        if (event.foregroundApp.isNotBlank()) {
                            append("\n")
                            append(getString(R.string.hint_foreground, event.foregroundApp))
                        }
                    }
                }
            }
        })
        if (events.isNotEmpty()) {
            container.addView(button(getString(R.string.action_clear)) {
                Store.clearHistory(this)
                render()
            })
        }

        container.addView(note(getString(R.string.footer_limits)))
    }

    /**
     * Mode ka sach, teen soorton mein.
     *
     * "DUMP mili hai" aur "DUMP se kaam bhi ho raha hai" alag cheezein hain:
     * DUMP ek Java-level permission hai, par service tak pahunchne se pehle
     * Android alag se rokta hai. Isliye dawa nahi karte - chala kar dekhte
     * hain aur jo hua wahi likhte hain.
     */
    private fun modeText(): String {
        val sample = CpuInspector.sample()
        val (previous, time) = Store.cpuSample(this)
        val cpu = if (sample == null) {
            CpuSnapshot(false, emptyList())
        } else {
            CpuInspector.compare(this, previous, sample, System.currentTimeMillis() - time)
        }
        val dumpGranted = checkSelfPermission(android.Manifest.permission.DUMP) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

        return when {
            cpu.available -> buildString {
                append(getString(R.string.mode_full))
                append("\n\n")
                append(getString(R.string.top_cpu_title))
                append("\n")
                append(cpu.processes.take(5).joinToString("\n") {
                    "  %.1f%%  %s".format(it.percent, it.packageName)
                })
            }
            dumpGranted && sample != null -> getString(R.string.mode_warming)
            dumpGranted -> getString(R.string.mode_blocked)
            else -> getString(R.string.mode_basic) + "\n\n" + getString(R.string.mode_basic_how)
        }
    }

    @Suppress("DEPRECATION")
    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(AppOpsManager::class.java) ?: return false
        // unsafeCheckOpNoThrow deprecated hai par iska koi badla nahi aaya -
        // usage-access ka haal poochhne ka aur koi tareeka platform deta hi nahi.
        val mode = runCatching {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }.getOrDefault(AppOpsManager.MODE_ERRORED)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /* ---- chhote helpers ---- */

    private fun heading(text: String): View = TextView(this).apply {
        this.text = text
        textSize = 19f
        setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(14), 0, dp(4))
    }

    private fun note(text: String): View = TextView(this).apply {
        this.text = text
        textSize = 12.5f
        alpha = 0.8f
        setPadding(0, dp(6), 0, dp(12))
    }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
