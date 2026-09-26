package com.mustfa.heatguard

/*
 * ===========================================================================
 *  Vitals.kt - phone se wo teen cheezein jo bina kisi permission ke milti hain
 *
 *  Kyun sirf teen? Kyunki Android bina root ke aur kuch deta hi nahi.
 *
 *  25 Sep 2026 ko is phone par MIUI ka launcher (com.miui.home) 7 din tak ek
 *  loop mein atka raha - do CPU core lagataar 100% - aur 3 din tak kisi ko
 *  pata nahi chala. Sawaal uthta hai: to ye app us launcher ka naam kyun nahi
 *  bata sakti? Isliye ki nahi bata sakti:
 *
 *      $ run-as com.mustfa.rakshapdf cat /proc/21380/stat
 *      cat: /proc/21380/stat: Permission denied
 *
 *  Ye maine isi phone par chala kar dekha hai. Android 9 se /proc doosri app
 *  ke liye band hai. Play Store ki "cooler"/"booster" apps bhi yahi nahi kar
 *  paatin - wo bas animation dikha kar aapko lagta hai kuch hua.
 *
 *  To ye app wo karti hai jo ho sakta hai, aur achhe se karti hai: garmi aur
 *  battery-drain par nazar rakhti hai, aur 15-30 minute mein bata deti hai ki
 *  kuch gadbad hai. Kis app ne kiya, ye aap Settings ki Battery screen par ek
 *  tap mein dekh lenge - us screen tak app aapko seedha le jaati hai.
 *
 *  3 din ka nuksaan 20 minute ka ho jata hai. Asli faayda wahi hai.
 * ===========================================================================
 */

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager

/** Ek waqt par phone ka haal. */
data class Vitals(
    val timeMs: Long,
    /** Battery ka temperature, Celsius mein. */
    val tempC: Float,
    /** Battery percent, 0-100. */
    val level: Int,
    val charging: Boolean,
    /** Screen us waqt on thi ya nahi. */
    val screenOn: Boolean,
    /**
     * Thermal headroom: 0 matlab bilkul thanda, 1 matlab throttling shuru.
     * Kuch phone ise support nahi karte - tab NaN aata hai.
     */
    val headroom: Float
) {
    val valid: Boolean get() = tempC > 0f && level in 0..100
}

object VitalsReader {

    fun read(context: Context): Vitals {
        // ACTION_BATTERY_CHANGED ek "sticky" broadcast hai: null receiver ke
        // saath register karne par aakhri value turant mil jaati hai, bina
        // kisi permission ke aur bina intezaar ke.
        val battery: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        // EXTRA_TEMPERATURE dashamlav ke bina deci-Celsius mein aata hai:
        // 414 ka matlab 41.4 C.
        val tempRaw = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val tempC = if (tempRaw > 0) tempRaw / 10f else -1f

        val rawLevel = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val level = if (rawLevel >= 0 && scale > 0) rawLevel * 100 / scale else -1

        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val power = context.getSystemService(PowerManager::class.java)
        val screenOn = power?.isInteractive ?: true

        // Agle 60 second ka andaza maangte hain. Har phone ise support nahi
        // karta, aur jo nahi karte wo exception phenk dete hain - isliye
        // runCatching. NaN ka matlab "pata nahi", jise Rules ignore kar deta hai.
        val headroom = runCatching { power?.getThermalHeadroom(60) ?: Float.NaN }
            .getOrDefault(Float.NaN)

        return Vitals(
            timeMs = System.currentTimeMillis(),
            tempC = tempC,
            level = level,
            charging = charging,
            screenOn = screenOn,
            headroom = headroom
        )
    }
}
