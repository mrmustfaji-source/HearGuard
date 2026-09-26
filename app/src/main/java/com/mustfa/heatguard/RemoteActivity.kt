package com.mustfa.heatguard

/*
 * ===========================================================================
 *  RemoteActivity.kt - Samsung se control karne ka setup
 *
 *  Poora setup ek token daalne jitna hai. Chat id khud pata chal jaati hai:
 *  aap bot ko pehla message bhejte hain, app use pakad kar "malik" bana
 *  leti hai. Haath se koi id nikalne ki zaroorat nahi.
 * ===========================================================================
 */

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
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

        container.addView(note(getString(R.string.remote_how)))

        /*
         * Sabse uper, sabse bada kaam: control link Telegram par bhej dena.
         *
         * Doosre phone par kuch install nahi karna padta. Wahan sirf link par
         * tap hoti hai, Telegram khulta hai, START dabta hai - aur us chat se
         * ye phone chalne lagta hai.
         */
        container.addView(button(getString(R.string.remote_link_send)) { sendInvite() })

        linkText = TextView(this).apply {
            textSize = 12f
            alpha = 0.85f
            setPadding(0, dp(4), 0, dp(14))
            setTextIsSelectable(true)
            text = getString(R.string.remote_link_none)
        }
        container.addView(linkText)

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
            val value = tokenField.text.toString().trim()
            if (value.isBlank()) {
                toast(getString(R.string.remote_need_token)); return@button
            }
            Remote.setToken(this, value)
            Remote.setEnabled(this, true)
            toast(getString(R.string.remote_saved))
            refresh()
        })

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
        refresh()
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

    private fun note(text: String): View = TextView(this).apply {
        this.text = text
        textSize = 12.5f
        alpha = 0.8f
        setPadding(0, dp(6), 0, dp(12))
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
