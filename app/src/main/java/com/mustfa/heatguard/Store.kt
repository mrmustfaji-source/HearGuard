package com.mustfa.heatguard

/*
 * ===========================================================================
 *  Store.kt - jo yaad rakhna hai wo SharedPreferences mein
 *
 *  Database ki zaroorat nahi: ek pichhla sample, thode counters, aur pichhli
 *  30 ghatnaayein. Poora data kuch KB ka hai.
 * ===========================================================================
 */

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Ek dafa jab app ne kaha "kuch gadbad hai". */
data class HeatEvent(
    val timeMs: Long,
    val reason: String,
    val tempC: Float,
    val drainPerHour: Float,
    /** Us waqt screen par jo app thi - agar pata chal saka to. */
    val foregroundApp: String
)

object Store {

    private const val PREFS = "heatguard"

    private const val K_ENABLED = "enabled"
    private const val K_LAST_TIME = "last_time"
    private const val K_LAST_TEMP = "last_temp"
    private const val K_LAST_LEVEL = "last_level"
    private const val K_LAST_CHARGING = "last_charging"
    private const val K_LAST_SCREEN = "last_screen"
    private const val K_HOT_STREAK = "hot_streak"
    private const val K_CPU_STREAK = "cpu_streak"
    private const val K_CPU_PKG = "cpu_pkg"
    private const val K_SNOOZE_UNTIL = "snooze_until"
    private const val K_LAST_ALERT = "last_alert"
    private const val K_HISTORY = "history"

    /** Itni ghatnaayein rakhi jaati hain, uske baad purani girti jaati hai. */
    private const val MAX_HISTORY = 30

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /* ---- on/off ---- */

    fun isEnabled(c: Context): Boolean = prefs(c).getBoolean(K_ENABLED, false)

    fun setEnabled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean(K_ENABLED, on).apply()
    }

    /* ---- pichhla sample ---- */

    fun lastSample(c: Context): Vitals? {
        val p = prefs(c)
        val t = p.getLong(K_LAST_TIME, 0L)
        if (t <= 0L) return null
        return Vitals(
            timeMs = t,
            tempC = p.getFloat(K_LAST_TEMP, -1f),
            level = p.getInt(K_LAST_LEVEL, -1),
            charging = p.getBoolean(K_LAST_CHARGING, false),
            screenOn = p.getBoolean(K_LAST_SCREEN, true),
            headroom = Float.NaN
        )
    }

    fun saveSample(c: Context, v: Vitals) {
        prefs(c).edit()
            .putLong(K_LAST_TIME, v.timeMs)
            .putFloat(K_LAST_TEMP, v.tempC)
            .putInt(K_LAST_LEVEL, v.level)
            .putBoolean(K_LAST_CHARGING, v.charging)
            .putBoolean(K_LAST_SCREEN, v.screenOn)
            .apply()
    }

    /* ---- garmi ka streak ----
     *
     * Ek hi garam reading par shor machana theek nahi: camera, game ya dhoop
     * se bhi phone garam hota hai aur wo apne aap theek ho jata hai. Isliye
     * lagataar do readings chahiye.
     */

    fun hotStreak(c: Context): Int = prefs(c).getInt(K_HOT_STREAK, 0)

    fun setHotStreak(c: Context, n: Int) {
        prefs(c).edit().putInt(K_HOT_STREAK, n).apply()
    }

    /* ---- CPU streak ----
     *
     * Package ke saath rakha jaata hai, sirf ginti ke saath nahi. Warna
     * "pichhli baar koi app tez thi" + "is baar koi DOOSRI app tez hai"
     * milkar jhootha do-ka-streak bana deta, aur dono mein se kisi par bhi
     * asli ilzaam nahi banta tha.
     */

    /* ---- pichhla per-uid CPU sample ----
     *
     * batterystats jod deta hai, rate nahi - isliye pichhla sample rakhna
     * padta hai aur antar naapna padta hai. "uid:ms,uid:ms" ke roop mein,
     * kyunki ye do sau chhoti sankhyaayein hain aur JSON ka wazan bekaar hai.
     */

    private const val K_CPU_SAMPLE = "cpu_sample"
    private const val K_CPU_SAMPLE_TIME = "cpu_sample_time"

    fun cpuSample(c: Context): Pair<Map<Int, Long>, Long> {
        val raw = prefs(c).getString(K_CPU_SAMPLE, null) ?: return emptyMap<Int, Long>() to 0L
        val time = prefs(c).getLong(K_CPU_SAMPLE_TIME, 0L)
        val map = HashMap<Int, Long>()
        for (part in raw.split(',')) {
            val i = part.indexOf(':')
            if (i < 1) continue
            val uid = part.substring(0, i).toIntOrNull() ?: continue
            val ms = part.substring(i + 1).toLongOrNull() ?: continue
            map[uid] = ms
        }
        return map to time
    }

    fun saveCpuSample(c: Context, sample: Map<Int, Long>, timeMs: Long) {
        // Sirf wo uid jinhone kuch to kharch kiya - baaki likhna jagah ki barbaadi.
        val text = sample.entries
            .filter { it.value > 0L }
            .joinToString(",") { "${it.key}:${it.value}" }
        prefs(c).edit()
            .putString(K_CPU_SAMPLE, text)
            .putLong(K_CPU_SAMPLE_TIME, timeMs)
            .apply()
    }

    fun cpuStreak(c: Context, pkg: String): Int {
        val p = prefs(c)
        if (p.getString(K_CPU_PKG, "") != pkg) return 0
        return p.getInt(K_CPU_STREAK, 0)
    }

    fun setCpuStreak(c: Context, pkg: String, n: Int) {
        prefs(c).edit().putString(K_CPU_PKG, pkg).putInt(K_CPU_STREAK, n).apply()
    }

    /* ---- shor na machane ke liye ---- */

    fun snoozeUntil(c: Context): Long = prefs(c).getLong(K_SNOOZE_UNTIL, 0L)

    fun snoozeFor(c: Context, millis: Long) {
        prefs(c).edit().putLong(K_SNOOZE_UNTIL, System.currentTimeMillis() + millis).apply()
    }

    fun lastAlert(c: Context): Long = prefs(c).getLong(K_LAST_ALERT, 0L)

    fun markAlerted(c: Context) {
        prefs(c).edit().putLong(K_LAST_ALERT, System.currentTimeMillis()).apply()
    }

    /* ---- history ---- */

    fun history(c: Context): List<HeatEvent> {
        val raw = prefs(c).getString(K_HISTORY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HeatEvent(
                    timeMs = o.optLong("t"),
                    reason = o.optString("r"),
                    tempC = o.optDouble("c", 0.0).toFloat(),
                    drainPerHour = o.optDouble("d", 0.0).toFloat(),
                    foregroundApp = o.optString("f")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun addEvent(c: Context, e: HeatEvent) {
        val list = (listOf(e) + history(c)).take(MAX_HISTORY)
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(JSONObject().apply {
                put("t", item.timeMs)
                put("r", item.reason)
                put("c", item.tempC.toDouble())
                put("d", item.drainPerHour.toDouble())
                put("f", item.foregroundApp)
            })
        }
        prefs(c).edit().putString(K_HISTORY, arr.toString()).apply()
    }

    fun clearHistory(c: Context) {
        prefs(c).edit().remove(K_HISTORY).apply()
    }
}
