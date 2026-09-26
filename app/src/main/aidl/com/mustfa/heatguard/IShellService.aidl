// Shizuku ke andar chalne wali service ka interface.
//
// Ye service HAMARI app ke andar nahi chalti - Shizuku ise `shell` user ke
// process mein banata hai. Isliye iske andar chalne wale commands ko wahi
// adhikaar milte hain jo `adb shell` ko milte hain, jaise
// CHANGE_APP_IDLE_STATE (deep sleep) aur FORCE_STOP_PACKAGES (app band karna)
// - jo hamari app ko kabhi nahi mil sakte.
package com.mustfa.heatguard;

/*
 * Har method ko transaction id DENI padti hai.
 *
 * AIDL ka niyam: "You must either assign id's to all methods or to none of
 * them." destroy() ko id deni hi hai (neeche wajah likhi hai), isliye exec()
 * ko bhi deni padi. Pehli koshish mein sirf destroy() ko di thi aur build
 * wahin ruk gayi thi.
 */
interface IShellService {
    /** Ek shell command chala kar uska poora output lautao. */
    String exec(String command) = 1;

    /**
     * Screen ki tasveer, chhoti JPEG.
     *
     * String se nahi: PNG binary String mein toot jati hai. Yahan shell
     * process khud screencap karke JPEG banata hai, app sirf bytes padhti hai.
     */
    byte[] capture() = 2;

    /**
     * Shizuku is service ko band karne ke liye yahi bulata hai.
     *
     * Transaction id 16777114 (IBinder.LAST_CALL_TRANSACTION) hona ZAROORI
     * hai - Shizuku isi number par call karta hai. Koi aur number dene par
     * service kabhi band nahi hogi aur `shell` process zinda pada rahega.
     */
    void destroy() = 16777114;
}
