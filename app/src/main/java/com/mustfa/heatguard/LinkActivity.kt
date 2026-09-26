package com.mustfa.heatguard

/*
 * ===========================================================================
 *  LinkActivity.kt - "app dono phone mein, ek doosre ko control kare"
 *
 *  Pehla version par user atak gaya tha, aur wajah sahi thi: screen sirf
 *  "Pairing code" ka khaali box dikhati thi, bina ye bataye ki wo code aata
 *  kahan se hai. Aur role ek baar chun lene ke baad badalne ka koi rasta hi
 *  nahi tha.
 *
 *  Ab teen cheezein hamesha screen par hain:
 *    1. Ye phone kis role mein hai, saaf shabdon mein
 *    2. Role badalne ka button - kabhi bhi
 *    3. Agla kadam kya hai, ginti ke saath (1, 2, 3)
 * ===========================================================================
 */

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class LinkActivity : HgActivity() {

    private lateinit var container: LinearLayout
    private var output: TextView? = null
    private var linkShown: TextView? = null

    /** Doosre phone se aayi app list, taki har app ke aage button lag sake. */
    private var remoteApps: List<Pair<String, String>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.link_title)
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(28))
        }
        setContentView(ScrollView(this).apply { addView(container) })
    }

    override fun onResume() {
        super.onResume()
        Perms.ask(this)
        SendLink.acceptFromClipboard(this)
        render()
    }

    private fun render() {
        container.removeAllViews()

        // Role hamesha upar - taki kabhi ye sawaal na uthe ki "ye phone kya hai".
        container.addView(heading(
            when (Link.role(this)) {
                LinkRole.OFF -> getString(R.string.link_role_none)
                LinkRole.CONTROLLED -> getString(R.string.link_role_controlled)
                LinkRole.CONTROLLER -> getString(R.string.link_role_controller)
            }
        ))

        when (Link.role(this)) {
            LinkRole.OFF -> renderChooser()
            LinkRole.CONTROLLED -> renderControlled()
            LinkRole.CONTROLLER -> renderController()
        }

        if (Link.role(this) != LinkRole.OFF) {
            container.addView(spacer(dp(20)))
            container.addView(button(getString(R.string.link_change_role)) {
                Link.setRole(this, LinkRole.OFF)
                render()
            })
        }
    }

    /* ---------------- Role chunna ---------------- */

    private fun renderChooser() {
        container.addView(note(getString(R.string.link_intro)))
        container.addView(button(getString(R.string.link_be_controlled)) {
            Link.setRole(this, LinkRole.CONTROLLED)
            if (Link.code(this).isBlank()) Link.newCode(this)
            render()
        })
        container.addView(button(getString(R.string.link_be_controller)) {
            Link.setRole(this, LinkRole.CONTROLLER)
            render()
        })
        container.addView(note(getString(R.string.link_privacy)))
    }

    /* ---------------- Jise control kiya jayega ---------------- */

    private fun renderControlled() {
        container.addView(note(getString(R.string.link_controlled_how)))

        val code = Link.code(this)
        container.addView(TextView(this).apply {
            text = code
            textSize = 26f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(18), dp(8), dp(18))
            setTextIsSelectable(true)
        })

        container.addView(button(getString(R.string.link_copy)) {
            getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("Heat Guard pairing code", code))
            toast(getString(R.string.link_copied))
        })
        // Share sheet: code WhatsApp se khud ko bhej dijiye, doosre phone par
        // kholiye - haath se likhne se aasan.
        container.addView(button(getString(R.string.link_share)) {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Heat Guard\nHG1 code: $code")
            }
            Notify.startSafely(this, listOf(Intent.createChooser(share, null)))
        })
        container.addView(button(getString(R.string.link_new_code)) {
            Link.newCode(this)
            toast(getString(R.string.link_new_code_done))
            render()
        })
        container.addView(note(getString(R.string.link_controlled_note)))
        container.addView(spacer(dp(8)))
        if (!Shell.shizukuReady()) {
            container.addView(note(getString(R.string.screen_need_shizuku)))
        } else if (ScreenService.running) {
            container.addView(note(getString(R.string.screen_running)))
            container.addView(button(getString(R.string.screen_stop)) {
                ScreenService.stop(this)
                render()
            })
        } else {
            container.addView(note(getString(R.string.screen_controlled_note)))
            container.addView(button(getString(R.string.screen_start)) {
                ScreenService.start(this)
                toast(getString(R.string.screen_running))
                render()
            })
        }
    }

    /* ---------------- Jo control karega ---------------- */

    private fun renderController() {
        container.addView(button(getString(R.string.send_link_button)) {
            SendLink.share(this) { sent ->
                runOnUiThread {
                    linkShown?.text = sent
                }
            }
        })
        linkShown = TextView(this).apply {
            text = "Is button se WhatsApp khulega. Pehli link APK download karegi. Samsung par Install dabao."
            textSize = 15f
            setPadding(0, dp(8), 0, dp(12))
        }
        container.addView(linkShown)

        if (Link.code(this).isBlank()) {
            container.addView(note(getString(R.string.link_enter_code)))
            val field = EditText(this).apply {
                hint = getString(R.string.link_code_hint)
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                setSingleLine(true)
            }
            container.addView(field)
            container.addView(button(getString(R.string.link_save_code)) {
                val value = field.text.toString().trim()
                if (value.length < 8) { toast(getString(R.string.link_code_short)); return@button }
                Link.setCode(this, value)
                Thread { Link.clearPending(this) }.start()
                render()
            })
            return
        }

        container.addView(note(getString(R.string.link_controller_ready)))

        container.addView(button(getString(R.string.screen_open)) {
            startActivity(Intent(this, ScreenActivity::class.java))
        })
        container.addView(button(getString(R.string.link_load_apps)) { loadApps() })
        container.addView(button(getString(R.string.link_cmd_status)) { fire("/status") })
        container.addView(button(getString(R.string.link_cmd_top)) { fire("/top") })
        container.addView(button(getString(R.string.link_cmd_sleep)) { fire("/sleep") })

        /*
         * Apps ki list aa chuki ho to har app ke aage seedha do button.
         *
         * Pehle sirf ek text box tha jisme "/open com.whatsapp" likhna padta
         * tha - yaani package ka poora naam yaad rakhna padta tha. Wo remote
         * control nahi, command line hai.
         */
        if (remoteApps.isNotEmpty()) {
            container.addView(heading(getString(R.string.link_apps_title)))
            remoteApps.forEach { (label, pkg) ->
                container.addView(appRow(label, pkg))
            }
        }

        container.addView(heading(getString(R.string.link_free_title)))
        val field = EditText(this).apply {
            hint = getString(R.string.link_free_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        container.addView(field)
        container.addView(button(getString(R.string.link_send)) {
            val text = field.text.toString().trim()
            if (text.isBlank()) { toast(getString(R.string.link_type_something)); return@button }
            fire(text)
        })

        output = TextView(this).apply {
            textSize = 13f
            setTypeface(Typeface.MONOSPACE)
            setPadding(0, dp(16), 0, 0)
            setTextIsSelectable(true)
            text = getString(R.string.link_no_output)
        }
        container.addView(output)
        container.addView(note(getString(R.string.link_delay_note)))
    }

    private fun appRow(label: String, pkg: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 15f
        })
        row.addView(TextView(this).apply {
            text = pkg
            textSize = 11f
            alpha = 0.6f
        })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(
            button(getString(R.string.link_app_open)) { fire("/open $pkg") },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        buttons.addView(
            button(getString(R.string.link_app_stop)) { fire("/stop $pkg") },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(buttons)
        return row
    }

    /** Doosre phone se app list mangwao aur use rows mein badal do. */
    private fun loadApps() {
        output?.text = getString(R.string.link_sending)
        Thread {
            if (!Link.sendCommand(this, "/apps")) {
                runOnUiThread { output?.text = getString(R.string.link_send_failed) }
                return@Thread
            }
            val reply = Link.awaitResult(this)
            runOnUiThread {
                if (reply == null) {
                    output?.text = getString(R.string.link_no_reply)
                    return@runOnUiThread
                }
                // Jawab ki har line "Label - package" hoti hai (Remote.listApps).
                remoteApps = reply.lineSequence()
                    .map { it.trim() }
                    .filter { it.contains(" - ") && !it.startsWith("/") }
                    .mapNotNull {
                        val i = it.lastIndexOf(" - ")
                        if (i < 1) null else it.substring(0, i) to it.substring(i + 3)
                    }
                    .filter { it.second.contains('.') }
                    .toList()
                output?.text = if (remoteApps.isEmpty()) reply
                               else getString(R.string.link_apps_loaded, remoteApps.size)
                render()
            }
        }.start()
    }

    private fun fire(command: String) {
        output?.text = getString(R.string.link_sending)
        Thread {
            if (!Link.sendCommand(this, command)) {
                runOnUiThread { output?.text = getString(R.string.link_send_failed) }
                return@Thread
            }
            runOnUiThread { output?.text = getString(R.string.link_waiting) }
            val reply = Link.awaitResult(this)
            runOnUiThread { output?.text = reply ?: getString(R.string.link_no_reply) }
        }.start()
    }

    /* ---------------- chhote helpers ---------------- */

    private fun heading(text: String): View = TextView(this).apply {
        this.text = text
        textSize = 17f
        setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(10), 0, dp(6))
    }

    private fun note(text: String): View = TextView(this).apply {
        this.text = text
        textSize = 12.5f
        alpha = 0.8f
        setPadding(0, dp(6), 0, dp(12))
    }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun spacer(height: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
