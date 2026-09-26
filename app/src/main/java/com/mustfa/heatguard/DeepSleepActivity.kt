package com.mustfa.heatguard

/*
 * ===========================================================================
 *  DeepSleepActivity.kt - Samsung jaisa, par kaam karne wala
 *
 *  Pehla version sirf list dikhata tha. Wo galat tha: list dekhne se kuch
 *  hota nahi. Samsung mein ye aise chalta hai, aur ab yahan bhi:
 *
 *      Pehli screen : teen row, har ek par ginti
 *                     Deep sleeping (11) / Sleeping (169) / Never (5)
 *
 *      Kisi par tap : SAARI apps ki list, har app ke aage tick box.
 *                     Jo pehle se us list mein hain wo tick lagi aati hain.
 *                     Upar do button: SAB  aur  DONE.
 *
 *      DONE dabate  : tick wali apps us bucket mein chali jaati hain, aur
 *                     jinki tick HATAI gayi wo wapas normal ho jaati hain.
 *
 *  Yaani ye ek editor hai, list nahi. Tick hatana bhi utna hi asli kaam hai
 *  jitna tick lagana.
 *
 *  Ye sab Shizuku ke bina nahi ho sakta - bucket badalne ka adhikaar
 *  (CHANGE_APP_IDLE_STATE) Android kisi app ko deta hi nahi. Isliye Shizuku
 *  band ho to screen wahi saaf-saaf bolti hai, buttons dikha kar dhoka nahi
 *  deti.
 * ===========================================================================
 */

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class DeepSleepActivity : HgActivity() {

    /** Kaun si list khuli hai. null matlab teen row wali pehli screen. */
    private var editing: BucketGroup? = null

    private var apps: List<AppBucket> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.deep_title)
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    /*
     * Back ka matlab: picker khula ho to teen-row wali screen par lauto, app
     * se bahar nahi. Bina iske DONE ke alawa picker se nikalne ka rasta hi
     * nahi bachta.
     *
     * onBackPressed() deprecated hai (naya rasta OnBackPressedDispatcher hai,
     * jo androidx mein aata hai). Is app mein koi androidx dependency nahi
     * hai - jaan-boojh kar - isliye yahi purana tareeka use hota hai. Ye
     * chalta hai, bas warning deta hai.
     */
    @Deprecated("Platform Activity par OnBackPressedDispatcher androidx ke bina nahi milta")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (editing != null) {
            editing = null
            render()
            return
        }
        super.onBackPressed()
    }

    private fun reload() {
        apps = Buckets.read(this)
        render()
    }

    private fun render() {
        if (editing == null) renderGroups() else renderPicker(editing!!)
    }

    /* ==================== Pehli screen: teen row ==================== */

    private fun renderGroups() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(28))
        }

        if (!Shell.shizukuReady()) {
            root.addView(note(getString(R.string.deep_need_shizuku)))
            if (Shell.shizukuRunning()) {
                root.addView(button(getString(R.string.deep_shizuku_grant)) {
                    Shell.requestShizukuPermission()
                })
            } else {
                // Shizuku chal hi nahi rahi - ijazat maangne ka sawaal hi
                // nahi. Yahan sirf itna kar sakte hain: seedha Shizuku ki
                // screen tak pahuncha do, dhoondhna na pade.
                root.addView(button(getString(R.string.deep_shizuku_open)) {
                    if (!Shell.openShizukuApp(this)) toast(getString(R.string.deep_shizuku_missing))
                })
            }
        }

        if (apps.isEmpty()) {
            root.addView(note(getString(R.string.deep_cannot_read)))
            root.addView(button(getString(R.string.action_usage_access)) {
                Notify.startSafely(
                    this,
                    listOf(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                )
            })
            setContentView(ScrollView(this).apply { addView(root) })
            return
        }

        root.addView(note(getString(R.string.deep_groups_intro)))

        for (group in listOf(
            BucketGroup.DEEP_SLEEPING,
            BucketGroup.SLEEPING,
            BucketGroup.ACTIVE
        )) {
            val count = apps.count { it.group == group }
            root.addView(groupRow(group, count))
        }

        root.addView(note(getString(R.string.deep_keep_note)))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun groupRow(group: BucketGroup, count: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(16), dp(4), dp(16))
            isClickable = true
            setOnClickListener {
                if (!Shell.shizukuReady()) {
                    toast(getString(R.string.deep_need_shizuku_short)); return@setOnClickListener
                }
                editing = group
                render()
            }
        }
        row.addView(TextView(this).apply {
            text = getString(group.titleRes) + "    " + count
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
        })
        row.addView(TextView(this).apply {
            text = getString(group.helpRes)
            textSize = 12.5f
            alpha = 0.75f
            setPadding(0, dp(3), 0, 0)
        })
        return row
    }

    /* ==================== Picker: tick box wali list ==================== */

    private fun renderPicker(group: BucketGroup) {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        root.addView(TextView(this).apply {
            text = getString(group.titleRes)
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(18), dp(14), dp(18), dp(2))
        })
        root.addView(TextView(this).apply {
            text = getString(group.helpRes)
            textSize = 12.5f
            alpha = 0.75f
            setPadding(dp(18), 0, dp(18), dp(10))
        })

        // Sorted: jo pehle se is list mein hain wo upar, taki dikhein.
        val ordered = apps.sortedWith(
            compareByDescending<AppBucket> { it.group == group }.thenBy { it.label.lowercase() }
        )

        val list = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE
            adapter = ArrayAdapter(
                this@DeepSleepActivity,
                android.R.layout.simple_list_item_multiple_choice,
                ordered.map { it.label + "\n" + it.packageName }
            )
        }
        // Pehle se jo is bucket mein hain, unki tick lagi hui.
        ordered.forEachIndexed { index, app ->
            if (app.group == group) list.setItemChecked(index, true)
        }

        /* Upar do button: SAB aur DONE. */
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), 0, dp(14), dp(6))
        }
        bar.addView(
            button(getString(R.string.deep_select_all)) {
                val allOn = (0 until list.count).all { list.isItemChecked(it) }
                for (i in 0 until list.count) list.setItemChecked(i, !allOn)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        bar.addView(
            button(getString(R.string.deep_done)) {
                val chosen = ArrayList<String>()
                val removed = ArrayList<String>()
                ordered.forEachIndexed { index, app ->
                    val ticked = list.isItemChecked(index)
                    if (ticked) {
                        if (app.group != group) chosen.add(app.packageName)
                    } else if (app.group == group) {
                        // Tick hatai gayi - ise is list se bahar nikalna hai.
                        removed.add(app.packageName)
                    }
                }
                apply(group, chosen, removed)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        root.addView(bar)

        root.addView(list, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
    }

    /**
     * Tick lagi apps ko is bucket mein, tick hatai gayi apps ko wapas normal.
     *
     * Dono kaam ek hi shell call mein - 200 apps ke liye alag-alag binder call
     * karne par screen jam jaati hai.
     */
    private fun apply(group: BucketGroup, add: List<String>, remove: List<String>) {
        if (add.isEmpty() && remove.isEmpty()) {
            toast(getString(R.string.deep_nothing_changed))
            editing = null
            render()
            return
        }

        // Jinhe keep-list mein rakha hai unhe sulane se OTP aur alarm ruk
        // jate hain. Chup-chaap chhodna theek nahi - bata kar chhodte hain.
        val blocked = if (group == BucketGroup.ACTIVE) emptyList()
                      else add.filter { Keep.isEssential(it) }
        val safeAdd = add - blocked.toSet()

        toast(getString(R.string.deep_working))
        Thread {
            var ok = true
            if (safeAdd.isNotEmpty()) {
                ok = Shell.setBucket(this, safeAdd, group.bucketName) && ok
            }
            if (remove.isNotEmpty()) {
                ok = Shell.setBucket(this, remove, "active") && ok
            }
            val done = ok
            runOnUiThread {
                toast(
                    when {
                        !done -> getString(R.string.deep_failed)
                        blocked.isEmpty() ->
                            getString(R.string.deep_done_count, safeAdd.size, remove.size)
                        else ->
                            getString(R.string.deep_done_count, safeAdd.size, remove.size) +
                                "\n" + getString(R.string.deep_skipped, blocked.size)
                    }
                )
                editing = null
                reload()
            }
        }.start()
    }

    /* ==================== chhote helpers ==================== */

    private fun note(text: String): View = TextView(this).apply {
        this.text = text
        textSize = 12.5f
        alpha = 0.8f
        setPadding(0, dp(6), 0, dp(14))
    }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
