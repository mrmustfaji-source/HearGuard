package com.mustfa.heatguard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem

/** Har screen par wahi menu: controls, screen, link, settings. */
open class HgActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        UiPref.applyTheme(this)
        super.onCreate(savedInstanceState)
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
