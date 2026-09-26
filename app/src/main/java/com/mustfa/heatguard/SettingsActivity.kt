package com.mustfa.heatguard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class SettingsActivity : HgActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = UiPref.t(this, "Settings", "Settings")
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(28))
        }
        root.addView(note(UiPref.t(
            this,
            "Theme, language, and the shell this app uses for remote controls. No second remote app.",
            "Theme, language, aur shell isi app mein. Doosri remote app nahi chahiye."
        )))

        root.addView(heading(UiPref.t(this, "Theme", "Theme")))
        root.addView(button("System") { pickTheme(UiPref.SYSTEM) })
        root.addView(button("Dark") { pickTheme(UiPref.DARK) })
        root.addView(button("Light") { pickTheme(UiPref.LIGHT) })

        root.addView(heading(UiPref.t(this, "Language", "Language")))
        root.addView(button("Hindi") { pickLang(UiPref.HI) })
        root.addView(button("English") { pickLang(UiPref.EN) })

        root.addView(heading("Shell"))
        root.addView(note(UiPref.t(
            this,
            "Android does not let a normal app tap the screen or force-stop others. This app talks to the phone's own Wireless debugging (the same door Shizuku uses). Turn that on once. If the Shizuku app is already running, this app uses it. You do not install a separate remote.",
            "Android normal app ko tap ya force-stop nahi deta. Ye app phone ke Wireless debugging se shell leti hai - wahi darwaza jo Shizuku use karta hai. Ek baar ON karo. Shizuku pehle se chalu ho to ye app use kar leti hai. Alag remote app nahi."
        )))
        root.addView(note(if (Shell.shizukuReady()) "Shell: ON" else "Shell: OFF"))
        root.addView(button(UiPref.t(this, "Open Wireless debugging", "Wireless debugging kholo")) {
            val intents = listOf(
                Intent("android.settings.APPLICATION_DEVELOPMENT_SETTINGS"),
                Intent(Settings.ACTION_SETTINGS)
            ).map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            Notify.startSafely(this, intents)
        })
        if (Shell.shizukuRunning() && !Shell.shizukuReady()) {
            root.addView(button(UiPref.t(this, "Allow shell", "Shell ki ijazat")) {
                Shell.requestShizukuPermission()
            })
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun pickTheme(value: String) {
        UiPref.setTheme(this, value)
        recreate()
    }

    private fun pickLang(value: String) {
        UiPref.setLang(this, value)
        recreate()
    }

    private fun heading(text: String) = TextView(this).apply {
        this.text = text
        textSize = 16f
        setPadding(0, dp(16), 0, dp(6))
    }

    private fun note(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setPadding(0, dp(4), 0, dp(8))
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
