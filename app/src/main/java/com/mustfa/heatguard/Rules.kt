package com.mustfa.heatguard

/*
 * ===========================================================================
 *  Rules.kt - kab kehna hai "kuch gadbad hai"
 *
 *  Ye numbers hawa mein se nahi liye. 25 Sep 2026 ko isi phone par naape gaye:
 *
 *      jab launcher loop mein atka tha   : battery 41.4 C, CPU 99.6 C
 *      launcher band karne ke turant baad: battery 39.8 C
 *      thodi der baad                    : battery 37.6 C
 *
 *  To is phone ka aaram ka temperature 37-39 C hai, aur 41 C par gadbad
 *  shuru hoti hai. Isiliye lakeer 41 par hai, 45 ya 50 par nahi - jab tak
 *  battery 45 tak pahunchegi tab tak kai ghante nikal chuke honge.
 * ===========================================================================
 */

/** Battery itni garam, bina charging ke, matlab kuch chal raha hai. */
private const val HOT_C = 41.0f

/**
 * Charging par battery ka garam hona normal hai - charging khud garmi paida
 * karti hai. Isliye alag, oonchi lakeer, warna har raat jhootha alarm bajta.
 */
private const val HOT_CHARGING_C = 44.0f

/** Itni readings lagataar garam aayein tab bolna hai. Ek check = ~15 minute. */
private const val HOT_STREAK_NEEDED = 2

/**
 * Screen band hone par itne percent prati ghanta se zyada girna galat hai.
 *
 * Aaram se pada phone 1-2% prati ghanta kharch karta hai. 10% par wo ya to
 * kuch bhaari chala raha hai ya koi app atki hui hai.
 */
private const val DRAIN_ALERT_PER_HOUR = 10.0f

/** Itne kam waqt ka farak bharosemand rate nahi deta. */
private const val MIN_INTERVAL_MS = 8L * 60 * 1000

/**
 * Ek app itna CPU khaye to wo bhaag rahi hai.
 *
 * Paimana: 100% = saare 8 core (dumpsys cpuinfo ka paimana, `top` ka nahi).
 * 25 Sep wala launcher `top` par 200% tha - yahan wo 25% banta hai. 18% se
 * upar matlab lagbhag 1.5 core lagataar. Koi bhi normal app aaram se itna
 * nahi khaati: system_server bhi 2-5% par rehta hai.
 */
private const val CPU_RUNAWAY_PCT = 18f

/** CPU wali shikayat bhi lagataar do baar chahiye. */
private const val CPU_STREAK_NEEDED = 2

/** Ek chetavni ke baad itni der chup - warna app khud pareshani ban jayegi. */
const val ALERT_COOLDOWN_MS = 2L * 60 * 60 * 1000

/** Rules ka faisla. */
data class Verdict(
    val alert: Boolean,
    /** Aadmi ke padhne laayak wajah. Khaali matlab sab theek. */
    val reason: String,
    val drainPerHour: Float,
    /** Garmi wali shart poori hui - streak ginne ke liye. */
    val hot: Boolean,
    /**
     * Jis app par shak hai, agar naam pata chal saka. Khaali matlab ya to
     * DUMP permission nahi hai, ya kisi ek app par ungli nahi uthti.
     */
    val culprit: String = "",
    val culpritPercent: Float = 0f
)

object Rules {

    /**
     * @param now abhi ka haal
     * @param previous pichhla sample, agar hai to
     * @param hotStreak ab tak lagataar kitni garam readings aa chuki hain
     * @param cpu process-wise CPU, agar DUMP permission mili ho
     * @param cpuStreak wahi app lagataar kitni baar hadd paar kar chuki hai
     */
    fun judge(
        now: Vitals,
        previous: Vitals?,
        hotStreak: Int,
        cpu: CpuSnapshot? = null,
        cpuStreak: Int = 0
    ): Verdict {
        if (!now.valid) return Verdict(false, "", 0f, false)

        /*
         * Sabse pehle CPU, kyunki yahi asli jawab hai.
         *
         * Garmi aur drain to nateeje hain - "phone garam hai" aapko pehle se
         * pata hai, haath mein hai. Kaam ki baat NAAM hai. Isliye jab naam
         * pata chal sakta hai, wahi chetavni jaati hai.
         */
        val hog = cpu?.top
        if (hog != null && hog.percent >= CPU_RUNAWAY_PCT && (cpuStreak + 1) >= CPU_STREAK_NEEDED) {
            /*
             * Screen chalu hai aur phone thanda hai, to shayad aap khud wahi
             * app chala rahe hain (game, video, camera). Us par ilzaam lagana
             * galat hoga. Screen band ho, ya phone garam ho - tab pakka
             * gadbad hai.
             */
            val userIsProbablyUsingIt = now.screenOn && now.tempC < HOT_C - 1f
            if (!userIsProbablyUsingIt) {
                return Verdict(
                    alert = true,
                    reason = "%s lagataar CPU kha rahi hai (%.0f%%). Isi se phone garam ho raha hai."
                        .format(hog.packageName, hog.percent),
                    drainPerHour = drainPerHour(now, previous),
                    hot = now.tempC >= (if (now.charging) HOT_CHARGING_C else HOT_C),
                    culprit = hog.packageName,
                    culpritPercent = hog.percent
                )
            }
        }

        val limit = if (now.charging) HOT_CHARGING_C else HOT_C
        val hot = now.tempC >= limit

        val drain = drainPerHour(now, previous)

        // 1) Garmi, lagataar. Streak mein YE reading bhi ginti hai, isliye +1.
        if (hot && (hotStreak + 1) >= HOT_STREAK_NEEDED) {
            return Verdict(
                alert = true,
                reason = "Phone garam hai - battery %.1f C. Koi app background mein chal rahi hai."
                    .format(now.tempC),
                drainPerHour = drain,
                hot = true
            )
        }

        // 2) Screen band thi phir bhi battery tezi se giri.
        //
        // Dono samples par screen band honi chahiye. Agar beech mein aapne
        // phone chalaya, to giravat aapki wajah se hai - app ki nahi.
        if (previous != null &&
            !now.charging && !previous.charging &&
            !now.screenOn && !previous.screenOn &&
            (now.timeMs - previous.timeMs) >= MIN_INTERVAL_MS &&
            drain >= DRAIN_ALERT_PER_HOUR
        ) {
            return Verdict(
                alert = true,
                reason = "Screen band hone par bhi battery %.0f%% prati ghanta gir rahi hai."
                    .format(drain),
                drainPerHour = drain,
                hot = hot
            )
        }

        return Verdict(false, "", drain, hot)
    }

    /**
     * Percent prati ghanta. Charging ke aar-paar ya oopar jaate level par
     * 0 lautata hai - wo giravat hai hi nahi.
     */
    fun drainPerHour(now: Vitals, previous: Vitals?): Float {
        if (previous == null) return 0f
        if (now.charging || previous.charging) return 0f
        val dropped = previous.level - now.level
        if (dropped <= 0) return 0f
        val hours = (now.timeMs - previous.timeMs) / 3_600_000f
        if (hours <= 0.01f) return 0f
        return dropped / hours
    }
}
