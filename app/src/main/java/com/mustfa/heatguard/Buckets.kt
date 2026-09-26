package com.mustfa.heatguard

/*
 * ===========================================================================
 *  Buckets.kt - Samsung wali "deep sleeping / sleeping / active" wali soorat
 *
 *  Samsung ka "Deep sleeping apps" koi apna jaadu nahi hai. Wo Android ke
 *  apne App Standby Buckets hain, jo har phone mein hote hain - aapke phone
 *  mein bhi. Samsung ne bas unko ek achhi screen de di hai.
 *
 *      10 active      abhi use hui
 *      20 working     roz use hoti hai
 *      30 frequent    aksar
 *      40 rare        kabhi kabhi     <- Android khud yahan daalta hai
 *      45 restricted  DEEP SLEEP      <- background band, notification band
 *      50 never       kabhi chali hi nahi
 *
 *  PADHNA ho jata hai: `dumpsys usagestats` ke "App Standby States" section
 *  mein har app ka bucket likha hota hai. Iske liye DUMP + usage access
 *  chahiye, dono mil sakti hain.
 *
 *  BADALNA nahi ho sakta. Maine aapke phone par do baar test kiya:
 *
 *      $ pm grant com.mustfa.heatguard android.permission.CHANGE_APP_IDLE_STATE
 *      SecurityException: ... is not a changeable permission type
 *
 *      $ run-as com.mustfa.heatguard am set-standby-bucket <pkg> restricted
 *      SecurityException: Access denied, requires CHANGE_APP_IDLE_STATE
 *
 *  Wo permission `signature|privileged` hai - sirf un apps ko milti hai jo
 *  phone ke firmware ke saath sign hui hon. Samsung ki Settings app aisi hi
 *  hai; koi bhi Play Store app nahi hai. Isliye ye screen dikhati hai aur
 *  seedha us jagah le jaati hai jahan aap ek tap mein badal sakte hain.
 * ===========================================================================
 */

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

data class AppBucket(
    val packageName: String,
    val label: String,
    val bucket: Int
) {
    /** Samsung ki teen listein. */
    val group: BucketGroup
        get() = when {
            bucket >= 45 -> BucketGroup.DEEP_SLEEPING
            bucket >= 30 -> BucketGroup.SLEEPING
            else -> BucketGroup.ACTIVE
        }
}

/**
 * Samsung ki teen listein, aur unke peechhe ka asli Android bucket.
 *
 * [bucketName] wahi shabd hai jo `am set-standby-bucket` samajhta hai.
 */
enum class BucketGroup(
    val titleRes: Int,
    val helpRes: Int,
    val bucketName: String
) {
    DEEP_SLEEPING(R.string.group_deep, R.string.group_deep_help, "restricted"),
    SLEEPING(R.string.group_sleeping, R.string.group_sleeping_help, "rare"),
    /** "Never auto sleeping" - Android mein iska matlab hai bucket active (10). */
    ACTIVE(R.string.group_active, R.string.group_active_help, "active")
}

object Buckets {

    /**
     * Har installed app ka bucket.
     *
     * Khaali list matlab padha nahi ja saka - ya DUMP nahi hai, ya usage
     * access nahi hai.
     */
    fun read(context: Context): List<AppBucket> {
        // Filter phone par hi. usagestats ka poora dump bada hota hai aur
        // humein sirf "App Standby States" wali lines chahiye.
        val output = exec(
            "sh", "-c",
            "dumpsys usagestats 2>/dev/null | grep -E '^  package=.* bucket=[0-9]+'"
        ) ?: return emptyList()

        if (output.contains("Permission Denial", ignoreCase = true)) return emptyList()

        val packageManager = context.packageManager
        // Sirf wo apps jo user ne install ki hain aur jinka launcher icon hai -
        // 200 system services ki list dikhana kisi kaam ka nahi.
        val visible = runCatching {
            packageManager.getInstalledApplications(0)
                .filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
                .associate { it.packageName to packageManager.getApplicationLabel(it).toString() }
        }.getOrDefault(emptyMap())
        if (visible.isEmpty()) return emptyList()

        val seen = HashMap<String, Int>()
        val line = Regex("""package=(\S+)\s.*?\bbucket=(\d+)""")
        for (raw in output.lineSequence()) {
            val m = line.find(raw) ?: continue
            val pkg = m.groupValues[1]
            val bucket = m.groupValues[2].toIntOrNull() ?: continue
            if (pkg !in visible) continue
            // Ek package kai baar aa sakta hai; aakhri wali sabse nayi hai.
            seen[pkg] = bucket
        }

        return seen.map { (pkg, bucket) ->
            AppBucket(pkg, visible[pkg] ?: pkg, bucket)
        }.sortedBy { it.label.lowercase() }
    }

    private fun exec(vararg command: String): String? = runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val text = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
        process.waitFor()
        text
    }.getOrNull()
}
