package com.mustfa.heatguard

import android.app.Activity
import android.content.Context

/** Theme aur language. Dono isi app mein, alag app nahi. */
object UiPref {

    private const val PREFS = "heatguard_ui"
    private const val K_THEME = "theme"
    private const val K_LANG = "lang"

    const val SYSTEM = "system"
    const val DARK = "dark"
    const val LIGHT = "light"
    const val HI = "hi"
    const val EN = "en"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun theme(c: Context): String = prefs(c).getString(K_THEME, SYSTEM) ?: SYSTEM

    fun setTheme(c: Context, value: String) {
        prefs(c).edit().putString(K_THEME, value).apply()
    }

    fun lang(c: Context): String = prefs(c).getString(K_LANG, HI) ?: HI

    fun setLang(c: Context, value: String) {
        prefs(c).edit().putString(K_LANG, value).apply()
    }

    fun hi(c: Context): Boolean = lang(c) == HI

    fun t(c: Context, en: String, hi: String): String = if (hi(c)) hi else en

    fun applyTheme(activity: Activity) {
        val id = when (theme(activity)) {
            DARK -> R.style.Theme_HeatGuard_Dark
            LIGHT -> R.style.Theme_HeatGuard_Light
            else -> R.style.Theme_HeatGuard
        }
        activity.setTheme(id)
    }
}
