package com.mustfa.heatguard

/*
 * ===========================================================================
 *  Remote.kt - Samsung se is phone ko chalana, Telegram ke zariye
 *
 *  Samsung par koi nayi app nahi lagti. Aap apne hi Telegram se ek bot ko
 *  message bhejte hain, ye phone wo command chala kar jawab usi chat mein
 *  bhej deta hai. Duniya mein kahin se bhi chalega.
 *
 *  KYUN TELEGRAM, FIREBASE KYUN NAHI
 *  ---------------------------------
 *  Firebase ke liye ek project banana padta, google-services.json daalni
 *  padti, aur Samsung par ek DOOSRI app bhi likhni padti. Telegram mein ye
 *  teeno kaam khatam ho jate hain: bot banane mein 2 minute lagte hain aur
 *  remote ka UI khud Telegram hai.
 *
 *  SURAKSHA - ye hissa dhyan se padhiye
 *  ------------------------------------
 *  Ye ek asli remote control hai. Isliye:
 *
 *    - Bot sirf EK chat ki sunta hai. Pehla message bhejne wala chat "malik"
 *      ban jata hai aur usi ke baad se sirf usi ki chalti hai. Kisi aur ka
 *      message chup-chaap phenk diya jata hai.
 *    - Bot token app ke apne private folder mein rehta hai.
 *    - **Bot token kisi ko mat dijiye.** Jiske paas token hoga wo is phone ko
 *      command bhej sakta hai. Galti se kahin daal dein to BotFather se
 *      /revoke kar ke naya bana lijiye.
 *    - Status aur command ka output Telegram ke server se guzarta hai. Jo
 *      aap nahi chahte ki wahan jaye, wo command mat chalaiye.
 *
 *  DER KITNI LAGEGI - saaf baat
 *  ----------------------------
 *  Chetavni (phone -> Samsung) TURANT jaati hai, kyunki phone khud bhejta hai.
 *
 *  Command (Samsung -> phone) mein der lag sakti hai. Phone jab idle hota hai
 *  to Android use bar-bar jagne nahi deta (Doze), isliye phone har 2 minute
 *  poochh nahi sakta. Jab aap phone chala rahe hon to jawab jaldi aata hai;
 *  jab phone jeb mein pada ho to 10-15 minute lag sakte hain. Isse kam karne
 *  ka ek hi tareeka hai - ek permanent notification wali service - jo is app
 *  ke maqsad (battery bachana) ke khilaf hai.
 * ===========================================================================
 */

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Remote {

    private const val PREFS = "heatguard_remote"
    private const val K_TOKEN = "bot_token"
    private const val K_CHAT = "owner_chat"
    private const val K_OFFSET = "update_offset"
    private const val K_ENABLED = "remote_enabled"
    private const val K_USER = "bot_username"
    private const val K_PAIR = "pair_code"
    private const val K_PAIR_AT = "pair_made_at"

    /** Control link banne ke baad itni der tak hi chalti hai. */
    private const val PAIR_VALID_MS = 30L * 60 * 1000

    /*
     * Telegram ka reply keyboard.
     *
     * Dhyan dene wali ek baat: button par jo likha hai, BILKUL wahi text bot
     * ko jata hai. Telegram reply keyboard mein label aur command alag nahi
     * ho sakte. Isliye "Home" jaise seedhe naam bhi command bante hain -
     * neeche alias() dekhiye. Warna button par "/key 3" likhna padta.
     */
    private const val KEYBOARD = """{"keyboard":[["Status","CPU"],["Back","Home","Recents"],["Vol +","Vol -"],["Apps","Sleep all"],["Screen on","Screen off"],["Settings","Help"]],"resize_keyboard":true,"is_persistent":true}"""

    /** Ek command ka output itna hi - Telegram 4096 se bada message leta nahi. */
    private const val MAX_REPLY = 3500

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /* --------------------------------- Settings --------------------------------- */

    fun token(c: Context): String = prefs(c).getString(K_TOKEN, "").orEmpty()

    fun setToken(c: Context, value: String) {
        // Naya token matlab naya bot - purana malik aur purana offset bekaar.
        prefs(c).edit()
            .putString(K_TOKEN, value.trim())
            .remove(K_CHAT)
            .remove(K_OFFSET)
            .remove(K_USER)
            .remove(K_PAIR)
            .remove(K_PAIR_AT)
            .apply()
    }

    fun ownerChat(c: Context): Long = prefs(c).getLong(K_CHAT, 0L)

    fun isEnabled(c: Context): Boolean =
        prefs(c).getBoolean(K_ENABLED, false) && token(c).isNotBlank()

    fun setEnabled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean(K_ENABLED, on).apply()
    }

    fun forgetOwner(c: Context) {
        prefs(c).edit().remove(K_CHAT).apply()
    }

    /**
     * Clipboard mein token jaisi dikhne wali cheez dhoondo.
     *
     * BotFather ka token hamesha isi shakal mein aata hai: kuch ank, ek
     * colon, phir letters/numbers/`-`/`_`. Poora BotFather ka message copy
     * ho (jisme aage-peeche aur bhi text ho) tab bhi ye usi ke beech se
     * token nikaal leta hai.
     */
    private val TOKEN_SHAPE = Regex("""\b(\d{6,10}:[A-Za-z0-9_-]{30,45})\b""")

    fun looksLikeToken(text: String): String? = TOKEN_SHAPE.find(text)?.groupValues?.get(1)

    /** Remote tabhi asli mein "chalu" hai jab koi chat bhi judi ho. */
    fun isPaired(c: Context): Boolean = isEnabled(c) && ownerChat(c) != 0L

    fun botUsername(c: Context): String = prefs(c).getString(K_USER, "").orEmpty()

    /* --------------------------------- Control link --------------------------------- */

    /**
     * Nayi control link banao.
     *
     * Link aisi dikhti hai: https://t.me/<bot>?start=<code>
     *
     * Doosre phone par us par tap karte hi Telegram khulta hai, bot ki chat
     * khulti hai, aur START dabate hi bot ko "/start <code>" chala jata hai.
     * Code sahi nikla to wahi chat is phone ki MALIK ban jati hai aur neeche
     * buttons aa jate hain. Doosre phone par HeatGuard lagane ki zaroorat
     * nahi - sirf Telegram chahiye.
     *
     * Code ek baar ka hai: istemal hote hi mit jata hai, aur 30 minute mein
     * khud mar jata hai. Isliye link kisi purani chat mein padi reh jaye to
     * bhi usse koi phone nahi khol sakta.
     *
     * Bot ka @naam getMe se aata hai - yaani ye network kaam hai. Main thread
     * par mat bulaiye. Fail ho to null.
     */
    fun newInvite(c: Context): String? {
        if (token(c).isBlank()) return null
        val user = fetchUsername(c) ?: return null
        val code = randomCode()
        prefs(c).edit()
            .putString(K_PAIR, code)
            .putLong(K_PAIR_AT, System.currentTimeMillis())
            .putBoolean(K_ENABLED, true)
            .apply()
        return "https://t.me/$user?start=$code"
    }

    private fun fetchUsername(c: Context): String? {
        val cached = botUsername(c)
        if (cached.isNotBlank()) return cached
        val raw = get("https://api.telegram.org/bot" + token(c) + "/getMe") ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (!json.optBoolean("ok", false)) return null
        val name = json.optJSONObject("result")?.optString("username").orEmpty()
        if (name.isBlank()) return null
        prefs(c).edit().putString(K_USER, name).apply()
        return name
    }

    /**
     * Link ka code.
     *
     * Alphabet se l, o, 0, 1 nikle hue hain (Link.kt jaisa hi) - kabhi haath
     * se likhna pade to inme galti hoti hai. 14 akshar = 70 bit, yaani
     * andaze se nikalna mumkin nahi.
     */
    private fun randomCode(): String {
        val alphabet = "abcdefghijkmnpqrstuvwxyz23456789"
        val random = java.security.SecureRandom()
        return (1..14).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }

    /** Abhi zinda code, warna khali. Purana code apne aap bekaar ho jata hai. */
    private fun livePairCode(c: Context): String {
        val made = prefs(c).getLong(K_PAIR_AT, 0L)
        if (made == 0L || System.currentTimeMillis() - made > PAIR_VALID_MS) return ""
        return prefs(c).getString(K_PAIR, "").orEmpty()
    }

    /* --------------------------------- Bahar bhejna --------------------------------- */

    /** Malik ko message bhejo. Network par chalta hai - main thread par nahi. */
    fun send(context: Context, text: String): Boolean {
        val chat = ownerChat(context)
        if (chat == 0L) return false
        return sendTo(context, chat, text)
    }

    private fun sendTo(context: Context, chat: Long, text: String, keys: Boolean = false): Boolean {
        val token = token(context)
        if (token.isBlank()) return false
        val body = "chat_id=" + chat +
            "&text=" + URLEncoder.encode(text.take(MAX_REPLY), "UTF-8") +
            "&disable_web_page_preview=true" +
            (if (keys) "&reply_markup=" + URLEncoder.encode(KEYBOARD, "UTF-8") else "")
        return post("https://api.telegram.org/bot$token/sendMessage", body) != null
    }

    /* --------------------------------- Command lena aur chalana --------------------------------- */

    /**
     * Naye message uthao, chalao, jawab bhejo.
     *
     * @return kitne command chale
     */
    fun poll(context: Context): Int {
        if (!isEnabled(context)) return 0
        val token = token(context)
        val offset = prefs(context).getLong(K_OFFSET, 0L)

        // timeout=0 matlab long-poll nahi: jo pada hai wo lo aur turant hato.
        // Long-poll connection kholey rakhta, jo Doze mein waise bhi nahi
        // chalta aur battery kharch karta.
        val raw = get(
            "https://api.telegram.org/bot$token/getUpdates" +
                "?timeout=0&limit=20" + if (offset > 0) "&offset=$offset" else ""
        ) ?: return 0

        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return 0
        if (!json.optBoolean("ok", false)) return 0
        val updates = json.optJSONArray("result") ?: return 0

        var handled = 0
        var highest = offset
        for (i in 0 until updates.length()) {
            val update = updates.optJSONObject(i) ?: continue
            val updateId = update.optLong("update_id", 0L)
            if (updateId >= highest) highest = updateId + 1

            val message = update.optJSONObject("message") ?: continue
            val chat = message.optJSONObject("chat")?.optLong("id", 0L) ?: 0L
            val text = message.optString("text", "").trim()
            if (chat == 0L || text.isBlank()) continue

            val owner = ownerChat(context)
            val pair = livePairCode(context)

            /*
             * Control link se aaya hua "/start <code>".
             *
             * Ye sabse pehle dekha jata hai, malik tay hone ke baad bhi -
             * kyunki nayi link app mein button dabane se hi banti hai. Yaani
             * agar naya code sahi hai to phone ka malik khud chahta hai ki
             * control naye phone ko chala jaye.
             */
            val startArg = START.find(text)?.groupValues?.get(1).orEmpty()
            if (pair.isNotBlank() && startArg == pair) {
                prefs(context).edit()
                    .putLong(K_CHAT, chat)
                    .remove(K_PAIR)        // ek baar ka code - ab mar gaya
                    .remove(K_PAIR_AT)
                    .apply()
                sendTo(context, chat, greeting(context), keys = true)
                handled++
                continue
            }

            if (owner == 0L) {
                if (pair.isNotBlank()) {
                    // Link zinda hai, par ye chat us link se nahi aayi.
                    sendTo(context, chat, "Judne ke liye app wali control link par tap kijiye.")
                    handled++
                    continue
                }
                /*
                 * Koi link kabhi banai hi nahi gayi - purana tarika: pehla
                 * message bhejne wala malik ban jata hai. Ye sirf isliye
                 * bacha hua hai ki jo setup pehle se chal raha hai wo na
                 * toote. Naya setup hamesha link se hona chahiye.
                 */
                prefs(context).edit().putLong(K_CHAT, chat).apply()
                sendTo(context, chat, greeting(context), keys = true)
                handled++
                continue
            }
            if (chat != owner) continue   // anjaan chat - chup-chaap chhod do

            val reply = runCatching { execute(context, text) }
                .getOrElse { "Command fail hui: " + (it.message ?: "pata nahi") }
            sendTo(context, chat, reply, keys = true)
            handled++
        }

        if (highest != offset) {
            prefs(context).edit().putLong(K_OFFSET, highest).apply()
        }
        return handled
    }

    private fun greeting(context: Context): String = buildString {
        append("Heat Guard jud gaya. Ab ye chat us phone ka remote hai.\n\n")
        append("Neeche buttons aa gaye hain - unhe dabaiye. ")
        append("Sirf YE chat chalegi, koi aur nahi.\n\n")
        append(help(context))
    }

    /** "/start code" aur "/start@mera_bot code" - dono yahan se nikal jate hain. */
    private val START = Regex("^/start(?:@\\S+)?(?:\\s+(\\S+))?\\s*$")

    /*
     * Button ka label hi command ban jata hai.
     *
     * Telegram reply keyboard par label aur bheja gaya text ek hi cheez hai,
     * isliye buttons par saaf naam rakhne ke liye ye mapping chahiye. Typing
     * abhi bhi chalti hai - ye sirf uske uper ek parat hai.
     */
    private fun alias(text: String): String = when (text.trim().lowercase()) {
        "status" -> "/status"
        "cpu" -> "/top"
        "back" -> "/key 4"
        "home" -> "/key 3"
        "recents" -> "/key 187"
        "vol +", "vol+" -> "/key 24"
        "vol -", "vol-" -> "/key 25"
        "apps" -> "/apps"
        "sleep all" -> "/sleep"
        "screen on" -> "/screen on"
        "screen off" -> "/screen off"
        "settings" -> "/settings"
        "help" -> "/help"
        else -> text
    }

    fun help(context: Context): String = buildString {
        append("Neeche ke buttons se aam kaam ho jate hain. ")
        append("Baaki ke liye ye likhiye:\n\n")
        append("Commands:\n")
        append("/status - battery, garmi, drain\n")
        append("/top - kaun CPU kha raha hai\n")
        append("/apps - saari apps ke naam\n")
        append("/apps <tukda> - dhoondho\n")
        append("/open <naam> - app kholo\n")
        append("/stop <naam> - app band karo\n")
        append("/sleep - saari unused apps deep sleep\n")
        append("/sleep <naam> - ek app deep sleep\n")
        append("/wake <naam> - wapas jagao\n")
        append("/sh <command> - koi bhi shell command\n")
        append("/tap /swipe /key - screen session ke dauran\n")
        append("/screen off - screen session band\n")
        append("/help - yahi list\n")
        if (!Shell.shizukuReady()) {
            append("\nDhyan: Shizuku band hai, isliye /stop /sleep /wake aur ")
            append("/sh sirf utna kar payenge jitna app khud kar sakti hai.")
        }
    }

    /**
     * Ek command chala kar jawab lauta do.
     *
     * Public isliye ki ise DO jagah se bulaya jata hai: Telegram wala rasta
     * aur app-to-app wala rasta (Link.kt). Dono ke liye command ka matlab
     * bilkul ek jaisa rehna chahiye - warna do jagah do tarah ka behaviour
     * ban jata hai.
     */
    fun execute(context: Context, text: String): String {
        val parts = alias(text).trim().split(Regex("\\s+"), limit = 2)
        val command = parts[0].substringBefore('@').lowercase()
        val argument = parts.getOrNull(1)?.trim().orEmpty()

        return when (command) {
            "/start", "/help" -> help(context)
            "/status" -> status(context)
            "/top" -> topCpu(context)
            "/open" -> openApp(context, argument)
            "/apps" -> listApps(context, argument)
            "/stop" -> stopApp(context, argument)
            "/sleep" -> if (argument.isBlank()) sleepUnused(context)
                        else setSleep(context, argument, deep = true)
            "/wake" -> setSleep(context, argument, deep = false)
            "/sh" -> shell(context, argument)
            "/screen" -> screenOff(context, argument)
            "/camera" -> launch(context, "am start -a android.media.action.STILL_IMAGE_CAMERA", "Camera")
            "/gallery" -> launch(context, "am start -a android.intent.action.MAIN -c android.intent.category.APP_GALLERY", "Gallery")
            "/phone" -> launch(context, "am start -a android.intent.action.DIAL", "Phone")
            "/settings" -> launch(context, "am start -a android.settings.SETTINGS", "Settings")
            "/tap" -> pointer(context, argument, swipe = false)
            "/swipe" -> pointer(context, argument, swipe = true)
            "/key" -> keyPress(context, argument)
            "/text" -> typeText(context, argument)
            "/bright" -> brightness(context, argument)
            "/panel" -> panel(context, argument)
            "/wifi" -> svc(context, "wifi", argument)
            "/bt" -> svc(context, "bluetooth", argument)
            "/data" -> svc(context, "data", argument)
            "/rotate" -> rotate(context)
            "/torch" -> torch(context, argument)
            else -> "Samajh nahi aaya. /help bhejiye."
        }
    }

    /* --------------------------------- Har command --------------------------------- */

    private fun status(context: Context): String {
        val now = VitalsReader.read(context)
        val (previous, _) = Store.lastSample(context) to 0
        val drain = Rules.drainPerHour(now, previous)
        return buildString {
            append("Battery: ").append(now.level).append("%")
            if (now.charging) append(" (charge ho rahi hai)")
            append("\nTemperature: ").append("%.1f".format(now.tempC)).append(" C")
            if (drain > 0f) append("\nDrain: ").append("%.1f".format(drain)).append("%/ghanta")
            if (!now.headroom.isNaN()) {
                append("\nThermal headroom: ").append("%.0f".format(now.headroom * 100f)).append("%")
            }
            append("\nShizuku: ").append(if (Shell.shizukuReady()) "chalu" else "band")
            val events = Store.history(context)
            if (events.isNotEmpty()) {
                append("\n\nAakhri chetavni:\n").append(events.first().reason)
            }
        }
    }

    private fun topCpu(context: Context): String {
        val sample = CpuInspector.sample() ?: return "CPU list nahi mili (DUMP permission nahi hai?)"
        val (previous, time) = Store.cpuSample(context)
        val snapshot = CpuInspector.compare(
            context, previous, sample, System.currentTimeMillis() - time
        )
        if (!snapshot.available) return "Abhi tulna karne ke liye pichhla naap nahi hai. 15 minute baad dobara poochhiye."
        return "Sabse zyada CPU:\n" + snapshot.processes.take(8).joinToString("\n") {
            "  %.1f%%  %s".format(it.percent, it.packageName)
        }
    }

    /**
     * Doosre phone par koi app khol do.
     *
     * Ye Shizuku ke bina bharosemand nahi hai, aur iski wajah Android ki ek
     * paabandi hai: Android 10 se koi app BACKGROUND se doosri activity shuru
     * nahi kar sakti. Heat Guard yahan background mein hi hai (aap Samsung par
     * hain, is phone ko chhua tak nahi). Isliye wo khud startActivity() kare
     * to system use chup-chaap gira deta hai.
     *
     * Shizuku ho to `am start` shell ke roop mein chalta hai, aur shell par ye
     * paabandi nahi hai. Isliye pehle wahi.
     *
     * Launcher activity ka naam PackageManager se nikala jata hai - "monkey"
     * jaise jugaad se nahi, kyunki monkey random taps bhi bhej sakta hai.
     */
    private fun openApp(context: Context, name: String): String {
        if (name.isBlank()) return "Kaun si app? Jaise: /open whatsapp"
        val target = resolvePackage(context, name) ?: return "'$name' naam ki koi app nahi mili."

        val launch = runCatching {
            context.packageManager.getLaunchIntentForPackage(target)
        }.getOrNull() ?: return "$target ki koi khulne wali screen nahi hai."
        val component = launch.component
            ?: return "$target ka launcher nahi mila."

        if (Shell.shizukuReady()) {
            val out = Shell.run(
                context,
                "am start -n " + component.packageName + "/" + component.className +
                    " -a android.intent.action.MAIN -c android.intent.category.LAUNCHER"
            )
            return if (out.contains("Error", true) || out.contains("Exception", true)) {
                "Nahi khul payi:\n" + out.trim().take(400)
            } else {
                "$target khol di."
            }
        }

        // Shizuku nahi hai: khud koshish karte hain. Phone chalu ho to kabhi
        // kabhi chal jata hai; background mein Android ise rok dega.
        return runCatching {
            launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            "$target kholne ki koshish ki. (Shizuku band hai, isliye phone " +
                "locked/jeb mein ho to Android ise rok sakta hai.)"
        }.getOrElse {
            "Nahi khul payi - Shizuku band hai. Shizuku chalu ho to ye pakka chalega."
        }
    }

    /** Installed apps ki list, taki naam pata ho ki kya likhna hai. */
    private fun listApps(context: Context, filter: String): String {
        val packageManager = context.packageManager
        val apps = runCatching {
            packageManager.getInstalledApplications(0)
                .filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
                .map { packageManager.getApplicationLabel(it).toString() to it.packageName }
        }.getOrDefault(emptyList())
        if (apps.isEmpty()) return "Apps ki list nahi mili."

        val matched = if (filter.isBlank()) apps else apps.filter {
            it.first.contains(filter, true) || it.second.contains(filter, true)
        }
        if (matched.isEmpty()) return "'$filter' se koi app nahi mili."

        val sorted = matched.sortedBy { it.first.lowercase() }
        val shown = sorted.take(60)
        return buildString {
            append(sorted.size).append(" apps")
            if (sorted.size > shown.size) append(" (pehli ").append(shown.size).append(")")
            append(":\n")
            shown.forEach { append("  ").append(it.first).append(" - ").append(it.second).append("\n") }
            if (sorted.size > shown.size) {
                append("\nDhoondhne ke liye: /apps <naam ka tukda>")
            }
        }
    }

    private fun stopApp(context: Context, name: String): String {
        if (name.isBlank()) return "Kis app ko? Jaise: /stop com.miui.home"
        val target = resolvePackage(context, name) ?: return "'$name' naam ki koi app nahi mili."
        if (!Shell.shizukuReady()) {
            return "Shizuku band hai, isliye app band nahi ki ja sakti. Phone par Shizuku shuru kijiye."
        }
        return if (Shell.forceStop(context, target)) "$target band kar di."
               else "$target band nahi ho payi."
    }

    private fun setSleep(context: Context, name: String, deep: Boolean): String {
        if (name.isBlank()) return "Kis app ko? Jaise: /sleep com.facebook.katana"
        val target = resolvePackage(context, name) ?: return "'$name' naam ki koi app nahi mili."
        if (!Shell.shizukuReady()) return "Shizuku band hai - bucket nahi badal sakte."
        val ok = Shell.setDeepSleep(context, listOf(target), deep)
        val word = if (deep) "deep sleep mein daal di" else "jaga di"
        return if (ok) "$target $word." else "Nahi ho paya."
    }

    private fun sleepUnused(context: Context): String {
        if (!Shell.shizukuReady()) return "Shizuku band hai - bucket nahi badal sakte."
        val apps = Buckets.read(context)
        if (apps.isEmpty()) return "Apps ki list nahi mili (usage access nahi hai?)."
        val targets = apps.filter {
            it.group != BucketGroup.DEEP_SLEEPING && !Keep.isEssential(it.packageName)
        }
        if (targets.isEmpty()) return "Sab pehle se soyi hui hain."
        val ok = Shell.setDeepSleep(context, targets.map { it.packageName }, true)
        return if (ok) "${targets.size} apps deep sleep mein daal di.\n\n" +
            "Chhodi gayin (call/SMS/bank/2FA wagairah): " +
            apps.count { Keep.isEssential(it.packageName) }
        else "Nahi ho paya."
    }

    private fun screenOff(context: Context, argument: String): String {
        if (argument.equals("off", true)) {
            ScreenService.stop(context)
            return "Screen band."
        }
        ScreenService.start(context)
        return "Screen chalu."
    }

    private fun launch(context: Context, command: String, name: String): String {
        if (!Shell.shizukuReady()) return "$name ke liye is phone par shell chahiye."
        val out = Shell.run(context, command)
        return if (out.contains("Error", true) || out.contains("Exception", true)) out.take(180)
        else "$name khul gayi."
    }

    private fun pointer(context: Context, argument: String, swipe: Boolean): String {
        if (!Shell.shizukuReady()) return "Shizuku band hai - screen par haath nahi rakh sakte."
        val n = argument.split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
        val (w, h) = Shell.screenPixels(context)
            ?: return "Screen ka size nahi mila (wm size)."
        return if (!swipe) {
            if (n.size != 2) return "Tap: x y chahiye."
            val (x, y) = ScreenMath.toScreen(n[0], n[1], w, h)
            if (Shell.tap(context, x, y)) "Tap $x $y" else "Tap nahi hua."
        } else {
            if (n.size != 4) return "Swipe: x1 y1 x2 y2 chahiye."
            val (x1, y1) = ScreenMath.toScreen(n[0], n[1], w, h)
            val (x2, y2) = ScreenMath.toScreen(n[2], n[3], w, h)
            if (Shell.swipe(context, x1, y1, x2, y2)) "Swipe ho gaya." else "Swipe nahi hua."
        }
    }

    private fun typeText(context: Context, argument: String): String {
        if (argument.isBlank()) return "Kya likhna hai?"
        return if (Shell.typeText(context, argument)) "Likh diya." else "Text nahi gaya. Shell band hai, ya sirf ASCII chalta hai."
    }

    private fun brightness(context: Context, argument: String): String {
        if (!Shell.shizukuReady()) return "Shell band hai."
        val cur = Shell.run(context, "settings get system screen_brightness")
            .trim().toIntOrNull() ?: 128
        val next = if (argument == "down") (cur - 40).coerceAtLeast(1) else (cur + 40).coerceAtMost(255)
        val out = Shell.run(
            context,
            "settings put system screen_brightness_mode 0; settings put system screen_brightness $next"
        )
        return if (out.contains("Exception", true)) out.take(200) else "Brightness $next"
    }

    private fun panel(context: Context, argument: String): String {
        val cmd = when (argument.trim()) {
            "notifications" -> "cmd statusbar expand-notifications"
            "settings" -> "cmd statusbar expand-settings"
            "collapse" -> "cmd statusbar collapse"
            else -> return "notifications, settings, ya collapse"
        }
        if (!Shell.shizukuReady()) return "Shell band hai."
        val out = Shell.run(context, cmd).trim()
        return if (out.isBlank()) "Ho gaya." else out.take(200)
    }

    private fun svc(context: Context, what: String, argument: String): String {
        val on = argument.equals("on", true)
        val off = argument.equals("off", true)
        if (!on && !off) return "on ya off"
        if (!Shell.shizukuReady()) return "Shell band hai."
        val out = Shell.run(context, "svc $what ${if (on) "enable" else "disable"}").trim()
        return if (out.contains("Exception", true) || out.contains("Error", true)) out.take(200)
               else "$what ${if (on) "on" else "off"}"
    }

    private fun rotate(context: Context): String {
        if (!Shell.shizukuReady()) return "Shell band hai."
        val cur = Shell.run(context, "settings get system accelerometer_rotation").trim()
        val next = if (cur == "0") "1" else "0"
        Shell.run(context, "settings put system accelerometer_rotation $next")
        return if (next == "1") "Auto rotate on" else "Auto rotate off"
    }

    private fun torch(context: Context, argument: String): String {
        val on = argument.equals("on", true)
        val manager = context.getSystemService(CameraManager::class.java)
            ?: return "Camera service nahi."
        val id = manager.cameraIdList.firstOrNull { cam ->
            val chars = manager.getCameraCharacteristics(cam)
            chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "Flash nahi mila."
        return runCatching {
            manager.setTorchMode(id, on)
            if (on) "Torch on" else "Torch off"
        }.getOrElse { "Torch nahi chala: " + (it.message ?: "") }
    }

    private fun keyPress(context: Context, argument: String): String {
        val code = argument.trim().toIntOrNull() ?: return "Key code nahi samjha."
        return if (Shell.key(context, code)) "Key $code" else "Ye key nahi chalegi."
    }

    private fun shell(context: Context, command: String): String {
        if (command.isBlank()) return "Kya chalana hai? Jaise: /sh getprop ro.build.version.release"
        val output = Shell.run(context, command).trim()
        val where = if (Shell.shizukuReady()) "shell" else "app"
        return if (output.isBlank()) "($where: kuch output nahi aaya)"
               else "($where)\n" + output.take(MAX_REPLY - 20)
    }

    /** Aadha naam bhi chalega: "facebook" se com.facebook.katana mil jayega. */
    private fun resolvePackage(context: Context, name: String): String? {
        val wanted = name.trim()
        val packageManager = context.packageManager
        val installed = runCatching {
            packageManager.getInstalledApplications(0).map { it.packageName }
        }.getOrDefault(emptyList())
        if (installed.isEmpty()) return wanted
        installed.firstOrNull { it.equals(wanted, ignoreCase = true) }?.let { return it }
        return installed.firstOrNull { it.contains(wanted, ignoreCase = true) }
    }

    /* --------------------------------- HTTP --------------------------------- */

    private fun get(url: String): String? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
        }
        connection.use { it.body() }
    }.getOrNull()

    private fun post(url: String, body: String): String? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        connection.outputStream.use { it.write(body.toByteArray()) }
        connection.use { it.body() }
    }.getOrNull()

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T = try {
        block(this)
    } finally {
        disconnect()
    }

    private fun HttpURLConnection.body(): String? {
        val stream = if (responseCode in 200..299) inputStream else errorStream ?: return null
        return BufferedReader(InputStreamReader(stream)).use { it.readText() }
    }
}
