package com.mustfa.heatguard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView

/** Remote jaisa ghar: link, phir seedhe button. Koi command nahi. */
class MainActivity : HgActivity() {

    private lateinit var status: TextView
    private lateinit var steps: TextView
    private lateinit var reply: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        steps = findViewById(R.id.steps)
        reply = findViewById(R.id.reply)

        findViewById<Button>(R.id.send_link).setOnClickListener { SendLink.share(this) }
        findViewById<Button>(R.id.btn_deep).setOnClickListener {
            startActivity(Intent(this, DeepSleepActivity::class.java))
        }
        // Ye do beech mein gayab ho gaye the. Pehra chalu karne wala button
        // sirf GuardActivity par hai - uske bina garmi ka check chalta hi nahi.
        findViewById<Button>(R.id.btn_guard).setOnClickListener {
            startActivity(Intent(this, GuardActivity::class.java))
        }
        findViewById<Button>(R.id.btn_telegram).setOnClickListener {
            startActivity(Intent(this, RemoteActivity::class.java))
        }
        findViewById<Button>(R.id.btn_screen).setOnClickListener {
            fire("/screen on")
            startActivity(Intent(this, ScreenActivity::class.java))
        }
        findViewById<Button>(R.id.btn_whatsapp).setOnClickListener { fire("/open com.whatsapp") }
        findViewById<Button>(R.id.btn_camera).setOnClickListener { fire("/camera") }
        findViewById<Button>(R.id.btn_gallery).setOnClickListener { fire("/gallery") }
        findViewById<Button>(R.id.btn_chrome).setOnClickListener { fire("/open com.android.chrome") }
        findViewById<Button>(R.id.btn_phone).setOnClickListener { fire("/phone") }
        findViewById<Button>(R.id.btn_youtube).setOnClickListener { fire("/open com.google.android.youtube") }
        findViewById<Button>(R.id.key_back).setOnClickListener { fire("/key 4") }
        findViewById<Button>(R.id.key_home).setOnClickListener { fire("/key 3") }
        findViewById<Button>(R.id.btn_recents).setOnClickListener { fire("/key 187") }
        findViewById<Button>(R.id.vol_up).setOnClickListener { fire("/key 24") }
        findViewById<Button>(R.id.vol_down).setOnClickListener { fire("/key 25") }

        Perms.ask(this)
        SendLink.acceptUri(this, intent?.data)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SendLink.acceptUri(this, intent.data)
        showRole()
    }

    override fun onResume() {
        super.onResume()
        if (SendLink.acceptFromClipboard(this) || Link.isControlled(this) || Remote.isPaired(this)) {
            ListenService.start(this)
        }
        showRole()
    }

    private fun showRole() {
        val controls = intArrayOf(
            R.id.btn_screen, R.id.btn_whatsapp, R.id.btn_camera, R.id.btn_gallery,
            R.id.btn_chrome, R.id.btn_phone, R.id.btn_youtube,
            R.id.key_back, R.id.key_home, R.id.btn_recents, R.id.vol_up, R.id.vol_down
        )
        when (Link.role(this)) {
            LinkRole.CONTROLLED -> {
                status.text = getString(R.string.home_controlled_title)
                steps.text = getString(R.string.home_controlled_steps)
                findViewById<View>(R.id.send_link).visibility = View.GONE
                controls.forEach { findViewById<View>(it).visibility = View.GONE }
            }
            LinkRole.CONTROLLER -> {
                status.text = getString(R.string.home_controller_title)
                steps.text = getString(R.string.home_controller_steps)
                findViewById<View>(R.id.send_link).visibility = View.VISIBLE
                controls.forEach { findViewById<View>(it).visibility = View.VISIBLE }
            }
            LinkRole.OFF -> {
                status.text = getString(R.string.home_off_title)
                steps.text = getString(R.string.home_off_steps)
                findViewById<View>(R.id.send_link).visibility = View.VISIBLE
                controls.forEach { findViewById<View>(it).visibility = View.GONE }
            }
        }
    }

    private fun fire(command: String) {
        if (Link.role(this) != LinkRole.CONTROLLER || Link.code(this).isBlank()) {
            reply.text = getString(R.string.home_off_steps)
            return
        }
        reply.text = getString(R.string.home_sending)
        Thread {
            val text = if (!Link.sendCommand(this, command)) {
                getString(R.string.link_send_failed)
            } else {
                Link.awaitResult(this, 20) ?: getString(R.string.home_no_reply)
            }
            runOnUiThread { reply.text = text.take(400) }
        }.start()
    }
}
