package com.mustfa.heatguard

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Saare remote controls. Menu se khulta hai. */
class PadActivity : HgActivity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = UiPref.t(this, "Controls", "Controls")
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }
        root.addView(TextView(this).apply {
            text = UiPref.t(
                this@PadActivity,
                "Controller role sends these to the other phone. Otherwise they run on this phone.",
                "Agar ye phone controller hai to ye doosre phone par jayenge. Warna isi phone par chalenge."
            )
            textSize = 13f
            setPadding(0, 0, 0, dp(8))
        })
        status = TextView(this).apply {
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setPadding(0, 0, 0, dp(8))
            text = shellLine()
        }
        root.addView(status)

        val field = EditText(this).apply {
            hint = UiPref.t(this@PadActivity, "Type text (ASCII)", "Text likho (ASCII)")
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        root.addView(field)
        root.addView(Button(this).apply {
            text = UiPref.t(this@PadActivity, "Send text", "Text bhejo")
            setOnClickListener {
                val raw = field.text.toString()
                if (raw.isBlank()) return@setOnClickListener
                fire("/text $raw")
            }
        })

        Pad.groups.forEach { group ->
            root.addView(TextView(this).apply {
                text = if (UiPref.hi(this@PadActivity)) group.hi else group.en
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(14), 0, dp(4))
            })
            var row = newRow()
            group.keys.forEachIndexed { index, key ->
                if (index > 0 && index % 3 == 0) {
                    root.addView(row)
                    row = newRow()
                }
                row.addView(Button(this).apply {
                    text = if (UiPref.hi(this@PadActivity)) key.hi else key.en
                    setOnClickListener { fire(key.command) }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            if (row.childCount > 0) root.addView(row)
        }

        setContentView(ScrollView(this).apply { addView(root) })
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 41)
        }
    }

    private fun fire(command: String) {
        status.text = command
        val remote = Link.role(this) == LinkRole.CONTROLLER && Link.code(this).isNotBlank()
        Thread {
            val reply = if (remote) {
                if (!Link.sendCommand(this, command)) {
                    getString(R.string.link_send_failed)
                } else {
                    Link.awaitResult(this, 20) ?: getString(R.string.link_no_reply)
                }
            } else {
                runCatching { Remote.execute(this, command) }.getOrElse { it.message ?: "fail" }
            }
            runOnUiThread {
                status.text = reply.take(500)
                if (!remote && reply.length < 80) {
                    Toast.makeText(this, reply, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun shellLine(): String {
        val ready = Shell.shizukuReady()
        return UiPref.t(
            this,
            if (ready) "Shell: ready" else "Shell: off. Tap, WiFi, force-stop need it. Settings mein Wireless debugging.",
            if (ready) "Shell: chalu" else "Shell: band. Tap, WiFi, force-stop ke liye Settings se Wireless debugging."
        )
    }

    private fun newRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
