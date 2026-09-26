package com.mustfa.heatguard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowInsets

/** Har screen par wahi menu: controls, screen, link, settings. */
open class HgActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        UiPref.applyTheme(this)
        super.onCreate(savedInstanceState)
    }

    /*
     * Layout fix: targetSdk 36 (Android 15+) par edge-to-edge system khud
     * thop deta hai, band nahi kiya ja sakta. Iska matlab hai screen ka
     * content ab status bar/action bar ke NEECHE se, unke peeche se, shuru
     * hota hai - isliye sabse upar wala button/text unke peeche dab jata
     * tha. Har activity apna layout alag tarike se banati hai (kuch XML se,
     * kuch seedha Kotlin se), isliye fix yahan HgActivity mein ek hi jagah -
     * jo bhi content aaye, uspar system bars jitni padding daal do.
     *
     * WindowInsets.Type.systemBars() API 30 se hai, jo iss app ka minSdk
     * bhi hai - koi extra library nahi chahiye.
     */
    override fun setContentView(view: View) {
        super.setContentView(view)
        applyBarPadding()
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        applyBarPadding()
    }

    private fun applyBarPadding() {
        val content = findViewById<View>(android.R.id.content) ?: return
        content.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        content.requestApplyInsets()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 1, UiPref.t(this, "Controls", "Controls"))
        menu.add(0, 2, 2, UiPref.t(this, "Screen", "Screen"))
        menu.add(0, 3, 3, UiPref.t(this, "Link", "Link"))
        menu.add(0, 4, 4, UiPref.t(this, "Settings", "Settings"))
        menu.add(0, 5, 5, "Deep sleep")
        /*
         * Ye do beech mein menu se gayab ho gaye the, aur dono ke bina app
         * apna asli kaam nahi kar pa rahi thi:
         *
         *   Heat & CPU - is screen par pehra chalu karne wala button hai.
         *                Store.setEnabled() poore project mein sirf wahin se
         *                bulaya jata hai, to uske bina garmi ka check kabhi
         *                chalta hi nahi tha.
         *
         *   Telegram   - doosra remote raasta, jisme Samsung par app lagane
         *                ki zaroorat nahi. Activity manifest mein thi par
         *                usse kholne ka koi rasta hi nahi bacha tha.
         */
        menu.add(0, 6, 6, UiPref.t(this, "Heat & CPU", "Garmi aur CPU"))
        menu.add(0, 7, 7, "Telegram")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val next = when (item.itemId) {
            1 -> PadActivity::class.java
            2 -> if (Link.role(this) == LinkRole.CONTROLLER) ScreenActivity::class.java
                  else LinkActivity::class.java
            3 -> LinkActivity::class.java
            4 -> SettingsActivity::class.java
            5 -> DeepSleepActivity::class.java
            6 -> GuardActivity::class.java
            7 -> RemoteActivity::class.java
            else -> return super.onOptionsItemSelected(item)
        }
        if (next != this::class.java) startActivity(Intent(this, next))
        return true
    }
}
