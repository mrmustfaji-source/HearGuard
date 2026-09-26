package com.mustfa.heatguard

/*
 * Doosre phone ki screen. Tasveer ~10 second mein badalti hai (ntfy ki
 * limit). Tap aur swipe usi hisaab se jaate hain. Session doosre phone
 * par chalu honi chahiye.
 */

import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.atomic.AtomicReference

class ScreenActivity : HgActivity() {

    private lateinit var image: ImageView
    private lateinit var status: TextView
    private val pending = AtomicReference<String?>(null)
    @Volatile private var generation = 0
    private var shown = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.screen_title)

        image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF111111.toInt())
            setOnTouchListener { v, event -> onFinger(v, event) }
        }
        status = TextView(this).apply {
            text = getString(R.string.screen_waiting)
            textSize = 13f
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val keys = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        keys.addView(keyButton(getString(R.string.screen_back)) { pending.set("/key 4") }, weight())
        keys.addView(keyButton(getString(R.string.screen_home)) { pending.set("/key 3") }, weight())
        keys.addView(keyButton(getString(R.string.screen_recents)) { pending.set("/key 187") }, weight())

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(status)
        root.addView(
            image,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        root.addView(keys)
        root.addView(Button(this).apply {
            text = getString(R.string.screen_stop)
            setOnClickListener {
                pending.set("/screen off")
                status.text = getString(R.string.screen_stopping)
            }
        })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val gen = ++generation
        Thread { loop(gen) }.start()
    }

    override fun onPause() {
        generation++
        super.onPause()
    }

    private fun loop(gen: Int) {
        var quiet = 0
        while (gen == generation) {
            val cmd = pending.getAndSet(null)
            if (cmd != null) {
                val ok = Link.sendCommand(this, cmd)
                runOnUiThread {
                    if (gen != generation) return@runOnUiThread
                    status.text = if (ok) getString(R.string.screen_sent) else getString(R.string.link_send_failed)
                    if (cmd == "/screen off" && ok) finish()
                }
            } else {
                val jpeg = Link.latestFrame(this)
                if (gen != generation) return
                if (jpeg != null) {
                    quiet = 0
                    val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                    runOnUiThread {
                        if (gen != generation || bmp == null) return@runOnUiThread
                        image.setImageBitmap(bmp)
                        shown++
                        status.text = getString(R.string.screen_frames, shown)
                    }
                } else {
                    quiet++
                    if (quiet == 2) {
                        runOnUiThread {
                            if (gen == generation && shown == 0) status.text = getString(R.string.screen_waiting)
                        }
                    }
                }
            }
        }
    }

    private fun onFinger(v: View, event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            v.tag = floatArrayOf(event.x, event.y)
            return true
        }
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        val start = v.tag as? FloatArray ?: return true
        val d = image.drawable ?: return true
        val n1 = ScreenMath.normalize(start[0], start[1], v.width, v.height, d.intrinsicWidth, d.intrinsicHeight)
        val n2 = ScreenMath.normalize(event.x, event.y, v.width, v.height, d.intrinsicWidth, d.intrinsicHeight)
        if (n1 == null || n2 == null) return true
        val dx = event.x - start[0]
        val dy = event.y - start[1]
        val slop = 24f * resources.displayMetrics.density
        pending.set(
            if (dx * dx + dy * dy < slop * slop) "/tap ${n2.x} ${n2.y}"
            else "/swipe ${n1.x} ${n1.y} ${n2.x} ${n2.y}"
        )
        status.text = getString(R.string.screen_sent)
        return true
    }

    private fun keyButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }

    private fun weight() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
