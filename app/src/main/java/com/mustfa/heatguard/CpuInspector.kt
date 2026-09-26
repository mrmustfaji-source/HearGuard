package com.mustfa.heatguard

/*
 * ===========================================================================
 *  CpuInspector.kt - kaun kitna CPU kha raha hai, NAAM ke saath
 *
 *  Ye app ka asli daant hai. Iske chalne ke liye DO cheezein chahiye:
 *
 *    1. android.permission.DUMP  - ek baar laptop se:
 *           adb shell pm grant com.mustfa.heatguard android.permission.DUMP
 *       (Iska level `signature|privileged|development` hai. Us `development`
 *        ki wajah se adb de sakta hai; manifest mein likhne bhar se nahi milti,
 *        aur reboot ke baad bani rehti hai.)
 *
 *    2. Usage access - ye AAP khud Settings mein de sakte hain, app ke
 *       "Usage access do" button se. Iske liye laptop ki zaroorat nahi.
 *
 *  Dono ke bina ye chup-chaap band rehta hai aur app garmi/drain wale mode par
 *  chalti hai.
 *
 *  KYUN batterystats, cpuinfo kyun nahi
 *  ------------------------------------
 *  Pehla version `dumpsys cpuinfo` chalata tha. Wo phone par test karne par
 *  fail hua:
 *
 *      $ run-as com.mustfa.heatguard dumpsys cpuinfo
 *      Can't find service: cpuinfo
 *
 *  DUMP milne ke BAAD bhi. Kuch services app ko "dikhti" hi nahi, chahe
 *  permission ho. Isi tarah `battery` aur `meminfo` bhi nahi dikhte.
 *
 *  Jo dikhte hain (test kiye gaye): batterystats, procstats, activity,
 *  usagestats, power, thermalservice.
 *
 *  Inmein se per-app CPU sirf `batterystats` deta hai - har uid ke neeche
 *  "Total cpu time: u=... s=...". `procstats` sirf memory deta hai, CPU nahi.
 *
 *  Ye aankda BOOT/charge se ab tak ka JOD hai, abhi ka rate nahi. Isliye do
 *  sample liye jaate hain aur unka antar naapa jaata hai - jo asal mein `top`
 *  se bhi behtar hai, kyunki ye poore 15 minute ka ausat deta hai, ek jhalak
 *  nahi.
 * ===========================================================================
 */

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/** Ek app aur uska CPU hissa. */
data class ProcCpu(
    val uid: Int,
    /** Dikhane laayak naam. Na mile to uid. */
    val name: String,
    /**
     * CPU ka hissa, jahan 100% = SAARE cores.
     *
     * `top` se alag paimana: wahan ek core = 100%, to do core kha rahi app
     * 200% dikhti hai. Yahan wahi app 25% hogi (8 core par). Rules.kt ke
     * threshold isi paimane par hain.
     */
    val percent: Float
) {
    val packageName: String get() = name
}

data class CpuSnapshot(
    val available: Boolean,
    val processes: List<ProcCpu>,
    val load1: Float = 0f
) {
    val top: ProcCpu? get() = processes.maxByOrNull { it.percent }
}

object CpuInspector {

    private val EMPTY = CpuSnapshot(false, emptyList())

    /** "  u0a184:" ya "  1000:" ya "  -5:" - batterystats mein uid ka header. */
    private val UID_HEADER = Regex("""^ {2}(-?\d+|u\d+a\d+):\s*$""")

    /** "    Total cpu time: u=1h 2m 3s 400ms s=..." */
    private val CPU_LINE = Regex("""^ {4}Total cpu time: (.*)$""")

    /** "1h" / "2m" / "3s" / "400ms" - ms wala pehle dekha jata hai. */
    private val DURATION = Regex("""(\d+)(ms|h|m|s)""")

    /**
     * Abhi tak ka jod: uid -> CPU milliseconds.
     *
     * @return null matlab dumpsys chala hi nahi (permission nahi, ya service
     *         nahi mili). Khaali map matlab chala par kuch mila nahi.
     */
    fun sample(): Map<Int, Long>? {
        /*
         * Filter PHONE par hi lagta hai, yahan nahi.
         *
         * Poora `dumpsys batterystats` 376,000 lines ka hota hai. Use process
         * ke beech se guzaarna bewakoofi hoti; grep se sirf ~600 lines wapas
         * aati hain. Poora dump banne mein phir bhi ~1 second lagta hai, par
         * 15 minute mein ek baar = 0.1% se bhi kam.
         */
        val output = exec(
            "sh", "-c",
            "dumpsys batterystats 2>/dev/null | " +
                "grep -E '^  (-?[0-9]+|u[0-9]+a[0-9]+):\\s*$|^    Total cpu time: '"
        ) ?: return null

        if (output.contains("Permission Denial", ignoreCase = true)) return null
        if (output.contains("Can't find service", ignoreCase = true)) return null

        val result = HashMap<Int, Long>()
        var pendingUid: Int? = null
        for (raw in output.lineSequence()) {
            val line = raw.trimEnd('\r')

            UID_HEADER.find(line)?.let { m ->
                pendingUid = parseUid(m.groupValues[1])
                return@let
            }

            val cpu = CPU_LINE.find(line) ?: continue
            val uid = pendingUid ?: continue
            result[uid] = (result[uid] ?: 0L) + parseDuration(cpu.groupValues[1])
            // Ek uid ka ek hi "Total cpu time" hota hai; iske baad agla header
            // aane tak kisi aur line ko is uid se nahi jodna.
            pendingUid = null
        }
        return result
    }

    /**
     * Do samples ka antar -> kis app ne beech ke waqt mein kitna CPU khaya.
     *
     * @param intervalMs dono samples ke beech ka asli waqt
     */
    fun compare(
        context: Context,
        previous: Map<Int, Long>,
        now: Map<Int, Long>,
        intervalMs: Long
    ): CpuSnapshot {
        if (previous.isEmpty() || now.isEmpty() || intervalMs <= 0) return EMPTY

        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        // Is antaraal mein kul kitne CPU-millisecond maujood the.
        val capacityMs = intervalMs.toFloat() * cores

        val list = ArrayList<ProcCpu>()
        for ((uid, nowMs) in now) {
            val before = previous[uid] ?: continue
            val delta = nowMs - before
            // Batterystats charge hone par reset ho jaata hai; tab delta
            // negative aata hai aur wo aankda bekaar hai.
            if (delta <= 0L) continue
            val percent = delta / capacityMs * 100f
            if (percent < 0.5f) continue
            list.add(ProcCpu(uid, labelFor(context, uid), percent))
        }
        if (list.isEmpty()) return EMPTY
        return CpuSnapshot(true, list.sortedByDescending { it.percent })
    }

    /** Sirf ye jaanne ke liye ki raasta khula hai ya nahi. */
    fun canRead(): Boolean = sample() != null

    /* ---------------------------------------------------------------- */

    /** "u0a184" -> 10184, "1000" -> 1000. */
    private fun parseUid(text: String): Int? {
        if (!text.startsWith("u")) return text.toIntOrNull()
        val m = Regex("""u(\d+)a(\d+)""").find(text) ?: return null
        val user = m.groupValues[1].toIntOrNull() ?: return null
        val app = m.groupValues[2].toIntOrNull() ?: return null
        // Android ka apna hisaab: user * 100000 + 10000 + appId
        return user * 100_000 + 10_000 + app
    }

    /**
     * "u=1h 2m 3s 400ms s=5s" -> kul milliseconds.
     *
     * Dono hisse (user + system) jodte hain - dono asli CPU hain.
     */
    private fun parseDuration(text: String): Long {
        var total = 0L
        for (m in DURATION.findAll(text)) {
            val value = m.groupValues[1].toLongOrNull() ?: continue
            total += when (m.groupValues[2]) {
                "ms" -> value
                "s" -> value * 1000
                "m" -> value * 60_000
                "h" -> value * 3_600_000
                else -> 0L
            }
        }
        return total
    }

    /** uid -> app ka naam. Ye public API hai, iske liye koi permission nahi chahiye. */
    private fun labelFor(context: Context, uid: Int): String = runCatching {
        val packageManager = context.packageManager
        val packages = packageManager.getPackagesForUid(uid)
        val first = packages?.firstOrNull() ?: return@runCatching "uid $uid"
        first
    }.getOrDefault("uid $uid")

    private fun exec(vararg command: String): String? = runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val text = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
        process.waitFor()
        text
    }.getOrNull()
}
