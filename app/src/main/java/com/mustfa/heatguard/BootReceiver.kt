package com.mustfa.heatguard

/*
 * ===========================================================================
 *  BootReceiver.kt - phone restart ke baad pehra dobara shuru
 *
 *  Iske bina app ek hi reboot mein chup ho jaati aur aapko pata bhi na
 *  chalta - jo is app ke hone ka matlab hi khatam kar deta.
 * ===========================================================================
 */

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        if (Link.isControlled(context)) ListenService.start(context)

        if (Store.isEnabled(context)) {
            // Purana sample reboot se pehle ka hai - beech ka waqt phone band
            // tha, to us se tulna karna bemaani hai. Isliye abhi ka taaza
            // sample daal dete hain, aur streak zero. Agla check is naye
            // sample se tulega.
            Store.setHotStreak(context, 0)
            Store.saveSample(context, VitalsReader.read(context))
            Scheduler.scheduleNext(context)
        }
    }
}
