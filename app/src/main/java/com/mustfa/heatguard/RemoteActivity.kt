package com.mustfa.heatguard

/*
 * ===========================================================================
 *  RemoteActivity.kt - Samsung se control karne ka setup
 *
 *  User yahan atak gaya tha: "token kahan se banega, kahan se banana hai
 *  sab waste hai" - matlab pehla version ye maan kar chal raha tha ki user
 *  ko pata hai BotFather kya hai aur wo Telegram mein use kaise dhoondhna
 *  hai. Ab teen alag, gine hue kadam hain, aur pehle kadam ka apna button
 *  hai jo seedha BotFather ki chat khol deta hai - dhoondhna nahi padta.
 *
 *  Ek cheez jo kabhi khatam nahi hogi: bot BANANA sirf BotFather se hi ho
 *  sakta hai, Telegram ka yahi (aur ekmatra) tarika hai. Iska koi seedha
 *  raasta nahi hai jo HeatGuard khud kar sake - token har insaan ke apne
 *  Telegram account se juda hota hai. Jo ho sakta tha: us ek zaroori kadam
 *  ko jitna aasan ho sake utna aasan banana. Token type karna bhi nahi
 *  padta - BotFather ka poora message copy karo, wapas is app mein aao,
 *  token khud aa jata hai (clipboard se, jaisa pairing code ke liye
 *  SendLink.acceptFromClipboard() karta hai).
 * ===========================================================================
 */

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class RemoteActivity : HgActivity() {

    private lateinit var container: LinearLayout
    private lateinit var tokenField: EditText
    private lateinit var statusText: TextView
    private lateinit var linkText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.remote_title)

        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(28))
        }
        setContentView(ScrollView(this).apply { addView(container) })

        /* ---------------- Kadam 1: bot banao ---------------- */
        container.addView(heading("1"))
        container.addView(note(getString(R.string.remote_step1_text)))
        container.addView(button(getString(R.string.remote_open_botfather)) { openBotFather() })

        /* ---------------- Kadam 2: token yahan aayega ---------------- */
        container.addView(heading("2"))
        container.addView(note(getString(R.string.remote_step2_text)))

        tokenField = EditText(this).apply {
            hint = getString(R.string.remote_token_hint)
            // Token ek lambi line hai; multiline off rakhna zaroori hai warna
            // paste karne par beech mein newline aa jaati hai.
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setSingleLine(true)
            setText(Remote.token(this@RemoteActivity))
        }
        container.addView(tokenField)

        container.addView(button(getString(R.string.remote_save)) {
            val value = Remote.looksLikeToken(tokenField.text.toString()) ?: tokenField.text.toString().trim()
            if (value.isBlank()) {
                toast(getString(R.string.remote_need_token)); return@button
            }
            tokenField.setText(value)
            Remote.setToken(this, value)
            Remote.setEnabled(this, true)
            toast(getString(R.string.remote_saved))
            refresh()
        })

        /* ---------------- Kadam 3: control link Telegram par bhejo ---------------- */
        container.addView(heading("3"))
        container.addView(note(getString(R.string.remote_step3_text)))
        container.addView(button(getString(R.string.remote_link_send)) { sendInvite() })

        linkText = TextView(this).apply {
            textSize = 12f
            alpha = 0.85f
            setPadding(0, dp(4), 0, dp(14))
            setTextIsSelectable(true)
            text = getString(R.string.remote_link_none)
        }
        container.addView(linkText)

        container.addView(button(getString(R.string.remote_test)) { test() })

        container.addView(button(getString(R.string.remote_forget)) {
            Remote.forgetOwner(this)
            toast(getString(R.string.remote_forgot))
            refresh()
        })

        container.addView(button(getString(R.string.remote_off)) {
            Remote.setEnabled(this, false)
            toast(getString(R.string.remote_turned_off))
            refresh()
        })

        statusText = TextView(this).apply {
            textSize = 13f
            setPadding(0, dp(18), 0, 0)
        }
        container.addView(statusText)
        container.addView(note(getString(R.string.remote_security)))
        container.addView(note(getString(R.string.remote_delay)))
    }

    override fun onResume() {
        super.onResume()
        // Jud chuke hain to tezi se sunna shuru karo, warna command 15 minute
        // tak pada rehta hai (Doze). Service khud 2 ghante baad hat jati hai.
        if (Remote.isPaired(this)) ListenService.start(this)
        autoFillTokenFromClipboard()
        refresh()
    }

    /**
     * BotFather ka poora message copy karke wapas aane par, token khud aa
     * jata hai - type karne ki zaroorat nahi. Sirf tab jab abhi tak koi
     * token nahi bacha ya field khaali hai, taaki purana kaam kar rahe
     * token par ye chup-chaap kuch aur na thop de.
     */
    private fun autoFillTokenFromClipboard() {
        if (Remote.token(this).isNotBlank() && tokenField.text.isNotBlank()) return
        val clip = getSystemService(ClipboardManager::class.java)
            ?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        val found = Remote.looksLikeToken(clip) ?: return
        if (found == tokenField.text.toString()) return
        tokenField.setText(found)
        toast(getString(R.string.remote_token_found))
    }

    /**
     * BotFather ki chat seedha khol do - Telegram mein "BotFather" dhoondhna
     * na pade. `tg://` scheme Telegram khud pehchanta hai; wo na chale to
     * `https://t.me/...` link se bhi wahi jagah khulti hai (browser wale
     * raaste se sahi, par Telegram installed ho to seedha wahi khulta hai).
     */
    private fun openBotFather() {
        val tries = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=BotFather")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/BotFather"))
        )
        if (!Notify.startSafely(this, tries)) toast(getString(R.string.remote_telegram_missing))
    }

    /**
     * Nayi control link banao aur Telegram par bhej do.
     *
     * getMe ka call network par hota hai, isliye alag thread. Link ban jaye
     * to wo clipboard mein bhi chali jati hai - taaki share sheet band ho
     * jaye to bhi haath se paste ki ja sake.
     */
    private fun sendInvite() {
        if (Remote.token(this).isBlank()) {
            toast(getString(R.string.remote_need_token)); return
        }
        toast(getString(R.string.remote_link_making))
        Thread {
            val link = runCatching { Remote.newInvite(this) }.getOrNull()
            runOnUiThread {
                if (link == null) {
                    toast(getString(R.string.remote_link_fail))
                    return@runOnUiThread
                }
                linkText.text = link
                getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("Heat Guard", link))
                shareToTelegram(link)
                refresh()
            }
        }.start()
    }

    /**
     * Pehle seedha Telegram, phir aam share sheet.
     *
     * setPackage us app par nahi chalta jo lagi hi nahi hai - wahan
     * startActivity phenk deta hai. startSafely ek ek karke koshish karta
     * hai, to jo bhi Telegram is phone par hai wahi khulta hai.
     */
    private fun shareToTelegram(link: String) {
        val text = getString(R.string.remote_link_message, link)
        val base = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        val tries = TELEGRAM.map { Intent(base).setPackage(it) } +
            Intent.createChooser(base, getString(R.string.remote_link_send))
        Notify.startSafely(this, tries)
    }

    private fun refresh() {
        val chat = Remote.ownerChat(this)
        statusText.text = buildString {
            append(getString(R.string.remote_state_on, if (Remote.isEnabled(this@RemoteActivity)) "HAAN" else "NAHI"))
            append("\n")
            if (chat == 0L) {
                append(getString(R.string.remote_state_waiting))
            } else {
                append(getString(R.string.remote_state_paired, chat.toString()))
            }
            val bot = Remote.botUsername(this@RemoteActivity)
            if (bot.isNotBlank()) append("\nBot: @").append(bot)
        }
    }

    /**
     * Test: naye message uthao (jisse pairing ho jaye) aur ek message bhejo.
     *
     * Network kaam hai, to alag thread par - warna Android app ko hi maar
     * deta hai (NetworkOnMainThreadException).
     */
    private fun test() {
        if (Remote.token(this).isBlank()) {
            toast(getString(R.string.remote_need_token)); return
        }
        toast(getString(R.string.remote_testing))
        Thread {
            runCatching { Remote.poll(this) }
            val chat = Remote.ownerChat(this)
            val ok = if (chat == 0L) false
                     else Remote.send(this, getString(R.string.remote_test_message))
            runOnUiThread {
                toast(
                    when {
                        chat == 0L -> getString(R.string.remote_test_no_chat)
                        ok -> getString(R.string.remote_test_ok)
                        else -> getString(R.string.remote_test_fail)
                    }
                )
                refresh()
            }
        }.start()
    }

    private fun heading(step: String): View = TextView(this).apply {
        text = getString(R.string.remote_step_label, step)
        textSize = 16f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(16), 0, dp(2))
    }

    private fun note(text: String): View = TextView(this).apply {
        this.text = text
        textSize = 13.5f
        alpha = 0.85f
        setPadding(0, dp(4), 0, dp(10))
    }

    private fun button(text: String, onClick: () -> Unit): View = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        /** Telegram ke asli package - jo bhi mile, usi mein share ho jayega. */
        val TELEGRAM = listOf(
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.telegram.plus",
            "nekox.messenger",
            "org.thunderdog.challegram"
        )
    }
}
