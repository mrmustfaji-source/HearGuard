package com.mustfa.heatguard

/*
 * ===========================================================================
 *  Keep.kt - jo apps kabhi deep sleep mein nahi jayengi
 *
 *  Samsung is list ko "Never auto sleeping apps" kehta hai. Yahan wahi kaam
 *  hai, par bulk button ke liye - taki ek tap se wo cheezein na ruk jayein
 *  jinka rukna aapko mehnga padega.
 *
 *  Deep sleep ka matlab hai background band AUR notification band. Iska matlab
 *  agar bank ki app so gayi to OTP nahi aayega, aur authenticator so gaya to
 *  account mein ghusna hi band. Isliye ye chhoti si list hai aur uske naam
 *  jaan-boojh kar tukdon mein match hote hain - "paisa" isliye ki Google Pay
 *  ka asli package `com.google.android.apps.nbu.paisa.user` hai, jise "gpay"
 *  kabhi match nahi karta.
 * ===========================================================================
 */

object Keep {

    private val PATTERNS = listOf(
        // Call / SMS / OTP
        "dialer", "incallui", "contacts", "messaging", "mms", "sms",
        // Messaging
        "whatsapp", "telegram",
        // Alarm / calendar - so gaye to alarm nahi bajega
        "deskclock", "clock", "alarm", "calendar",
        // Paisa
        "bank", "upi", "paytm", "phonepe", "gpay", "paisa", "wallet",
        // 2FA - ye so gaya to login hi band
        "authenticator", "authy", "otp",
        // Android ka apna dhancha
        "com.google.android.gms", "com.android.vending",
        // Khud ko sulana bewakoofi hogi
        "com.mustfa.heatguard"
    )

    fun isEssential(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return PATTERNS.any { lower.contains(it) }
    }
}
