package com.mustfa.heatguard

/*
 * ===========================================================================
 *  Link.kt - ek hi app, do phone, ek doosre ko control kare
 *
 *  Ek phone "CONTROLLED" ban jata hai (jise control kiya jayega - Redmi),
 *  doosra "CONTROLLER" (jis se control karenge - Samsung). App dono mein
 *  wahi hai; sirf role alag hai.
 *
 *  KOI PASSWORD TYPE NAHI KARNA PADTA
 *  ----------------------------------
 *  Setup mein ek baar ek PAIRING CODE doosre phone par daalna hota hai -
 *  bilkul Bluetooth pair karne ki tarah. Uske baad kabhi kuch nahi. Button
 *  dabaiye, kaam ho jata hai.
 *
 *  Wo code app khud banati hai (32 random akshar) - aap sochte nahi. Aur wo
 *  zaroori hai: wahi ek cheez hai jo tay karti hai ki aapke phone ko sirf
 *  AAP control kar sakein. Bina uske duniya ka koi bhi aapke phone ko command
 *  bhej sakta. Isliye ye nikala nahi ja sakta.
 *
 *  DONO PHONE SEEDHA BAAT KYUN NAHI KARTE
 *  --------------------------------------
 *  Internet par do phone seedha nahi jud sakte - dono kisi router ke peechhe
 *  hote hain (NAT). Beech mein koi na koi chahiye. Iske liye ntfy.sh use hota
 *  hai: ek free, khula HTTP relay jisme na account banana padta hai, na
 *  server chalana padta hai, na Firebase project.
 *
 *  Kaam aise hota hai:
 *      Samsung  --(command)-->  ntfy.sh/<code>-cmd  --(poll)-->  Redmi
 *      Samsung  <--(poll)--     ntfy.sh/<code>-res  <--(jawab)-- Redmi
 *
 *  Topic ka naam pairing code nahi hai. Code ka hash topic hai, alag hash
 *  encryption key hai (LinkCrypto). Relay seedha text ya tasveer nahi padh
 *  sakta. Phir bhi code kisi aur ko mat dijiye.
 * ===========================================================================
 */

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom

enum class LinkRole { OFF, CONTROLLED, CONTROLLER }

object Link {

    private const val PREFS = "heatguard_link"
    private const val K_CODE = "pair_code"
    private const val K_ROLE = "role"
    private const val K_SEEN = "last_seen_id"
    private const val K_SEEN_PIC = "last_seen_pic"

    /**
     * ntfy.sh default: 60 request ka burst, phir 1 request / 5 second.
     * Isse tez par 429 aata hai. Screen isi raftaar par chalti hai.
     */
    private const val PACE_MS = 5000L

    private const val BASE = "https://ntfy.sh/"

    /** Relay par bheja gaya message itni der baad khud mit jata hai. */
    private const val CACHE = "12h"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /* --------------------------- Pairing --------------------------- */

    fun code(c: Context): String = prefs(c).getString(K_CODE, "").orEmpty()

    fun setCode(c: Context, value: String) {
        prefs(c).edit()
            .putString(K_CODE, value.trim())
            // Naya code matlab nayi jodi - purane message ka hisaab bekaar.
            .remove(K_SEEN)
            .remove(K_SEEN_PIC)
            .apply()
    }

    /**
     * Naya pairing code.
     *
     * 12 akshar, 32 akshar ke alphabet se = 60 bit. Itna andaze se nikalna
     * mumkin nahi, aur haath se likhna bhi mushkil nahi - pehle 32 akshar the
     * jo type karna sazaa thi.
     *
     * Alphabet se l, o, 0, 1 nikaal diye hain: haath se likhte waqt inme
     * galti hoti hai.
     */
    fun newCode(c: Context): String {
        val alphabet = "abcdefghijkmnpqrstuvwxyz23456789"
        val random = SecureRandom()
        val code = (1..12).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        setCode(c, code)
        return code
    }

    fun role(c: Context): LinkRole = runCatching {
        LinkRole.valueOf(prefs(c).getString(K_ROLE, LinkRole.OFF.name)!!)
    }.getOrDefault(LinkRole.OFF)

    fun setRole(c: Context, value: LinkRole) {
        prefs(c).edit().putString(K_ROLE, value.name).apply()
    }

    fun isControlled(c: Context): Boolean =
        role(c) == LinkRole.CONTROLLED && code(c).isNotBlank()

    /* --------------------------- Controlled side --------------------------- */

    /**
     * Naye command uthao, chalao, jawab bhejo. Redmi par chalta hai.
     *
     * @return kitne command chale
     */
    fun pollAndRun(context: Context): Int {
        if (!isControlled(context)) return 0
        val code = code(context)
        val messages = poll(code, "cmd", prefs(context).getString(K_SEEN, "").orEmpty())
        if (messages.isEmpty()) return 0

        var done = 0
        var lastId = prefs(context).getString(K_SEEN, "").orEmpty()
        for ((id, text) in messages) {
            lastId = id
            if (text.isBlank()) continue
            val reply = runCatching { Remote.execute(context, text) }
                .getOrElse { "Command fail hui: " + (it.message ?: "pata nahi") }
            publish(code, "res", reply)
            done++
        }
        prefs(context).edit().putString(K_SEEN, lastId).apply()
        return done
    }

    /* --------------------------- Controller side --------------------------- */

    /** Samsung se command bhejo. */
    fun sendCommand(context: Context, command: String): Boolean {
        val code = code(context)
        if (code.isBlank()) return false
        return publish(code, "cmd", command)
    }

    /**
     * Jawab ka intezaar karo.
     *
     * Redmi turant jawab nahi de sakta: Android use lagataar jagne nahi deta.
     * Isliye yahan thodi der tak baar-baar poochha jata hai, aur na mile to
     * saaf bata diya jata hai ki der lag rahi hai - chup-chaap atakne se
     * behtar hai.
     */
    fun awaitResult(context: Context, waitSeconds: Int = 25): String? {
        val code = code(context)
        if (code.isBlank()) return null
        val deadline = System.currentTimeMillis() + waitSeconds * 1000L
        var seen = prefs(context).getString(K_SEEN, "").orEmpty()
        while (System.currentTimeMillis() < deadline) {
            val messages = poll(code, "res", seen)
            if (messages.isNotEmpty()) {
                prefs(context).edit().putString(K_SEEN, messages.last().first).apply()
                return messages.joinToString("\n\n") { it.second }
            }
            // poll() khud 5 second ka intezaar kar leta hai (ntfy ki limit).
        }
        return null
    }

    /** Jo purane jawab pade hain unhe padha hua maan lo - warna wo agli baar aayenge. */
    fun clearPending(context: Context) {
        val code = code(context)
        if (code.isBlank()) return
        val messages = poll(code, "res", "")
        if (messages.isNotEmpty()) {
            prefs(context).edit().putString(K_SEEN, messages.last().first).apply()
        }
    }

    /**
     * Nayi tasveer. Purani frames download nahi hoti - sirf sabse nayi.
     * null = abhi kuch naya nahi, ya relay ne rok diya.
     */
    fun latestFrame(context: Context): ByteArray? {
        val code = code(context)
        if (code.isBlank()) return null
        val seen = prefs(context).getString(K_SEEN_PIC, "").orEmpty()
        val frames = pollRaw(code, "pic", seen)
        if (frames.isEmpty()) return null
        val last = frames.last()
        val url = last.attachment
        if (url.isNullOrBlank()) {
            prefs(context).edit().putString(K_SEEN_PIC, last.id).apply()
            return null
        }
        val bytes = download(url) ?: return null
        prefs(context).edit().putString(K_SEEN_PIC, last.id).apply()
        return LinkCrypto.open(code, bytes)
    }

    /** Screen ki encrypted tasveer bhejo. */
    fun publishFrame(context: Context, jpeg: ByteArray): Boolean {
        val code = code(context)
        if (code.isBlank() || jpeg.isEmpty()) return false
        return publishBytes(LinkCrypto.topic(code, "pic"), LinkCrypto.seal(code, jpeg))
    }

    /* --------------------------- Relay --------------------------- */

    private val httpLock = Any()
    private var nextAt = 0L

    private fun <T> paced(block: () -> T): T = synchronized(httpLock) {
        val wait = nextAt - System.currentTimeMillis()
        if (wait > 0) Thread.sleep(wait)
        try {
            block()
        } finally {
            val pace = System.currentTimeMillis() + PACE_MS
            if (nextAt < pace) nextAt = pace
        }
    }

    private fun coolDown() {
        nextAt = System.currentTimeMillis() + 20_000L
    }

    private fun publish(code: String, kind: String, text: String): Boolean = paced {
        runCatching { publishPlain(code, kind, text) }.getOrDefault(false)
    }

    private fun publishPlain(code: String, kind: String, text: String): Boolean {
        val body = LinkCrypto.sealText(code, text)
        val connection = (URL(BASE + LinkCrypto.topic(code, kind)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Cache", CACHE)
        }
        connection.outputStream.use { it.write(body.toByteArray()) }
        val codeHttp = connection.responseCode
        connection.disconnect()
        if (codeHttp == 429) coolDown()
        return codeHttp in 200..299
    }

    private fun publishBytes(topic: String, bytes: ByteArray): Boolean = paced {
        runCatching { publishBytesPlain(topic, bytes) }.getOrDefault(false)
    }

    private fun publishBytesPlain(topic: String, bytes: ByteArray): Boolean {
        val connection = (URL(BASE + topic).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Filename", "f.bin")
            setRequestProperty("Content-Type", "application/octet-stream")
            setRequestProperty("Cache", "30m")
        }
        connection.outputStream.use { it.write(bytes) }
        val codeHttp = connection.responseCode
        connection.disconnect()
        if (codeHttp == 429) coolDown()
        return codeHttp in 200..299
    }

    private data class Raw(val id: String, val text: String, val attachment: String?)

    private fun poll(code: String, kind: String, after: String): List<Pair<String, String>> {
        return pollRaw(code, kind, after).mapNotNull { raw ->
            val text = LinkCrypto.openText(code, raw.text) ?: return@mapNotNull null
            raw.id to text
        }
    }

    /**
     * poll=1 se connection turant band ho jata hai. Stream mode nahi:
     * wo battery khata hai, aur Doze mein kat jata hai.
     */
    private fun pollRaw(code: String, kind: String, after: String): List<Raw> = paced {
        runCatching { pollRawPlain(code, kind, after) }.getOrDefault(emptyList())
    }

    private fun pollRawPlain(code: String, kind: String, after: String): List<Raw> {
        val topic = LinkCrypto.topic(code, kind)
        val url = buildString {
            append(BASE).append(topic).append("/json?poll=1")
            if (after.isNotBlank()) {
                append("&since=").append(URLEncoder.encode(after, "UTF-8"))
            }
        }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 25000
        }
        val codeHttp = connection.responseCode
        val body = if (codeHttp in 200..299) {
            BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
        } else {
            ""
        }
        connection.disconnect()
        if (codeHttp == 429) {
            coolDown()
            return emptyList()
        }

        val all = ArrayList<Raw>()
        for (line in body.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val json = runCatching { JSONObject(trimmed) }.getOrNull() ?: continue
            if (json.optString("event") != "message") continue
            val id = json.optString("id")
            if (id.isBlank()) continue
            val attachment = json.optJSONObject("attachment")?.optString("url")?.takeIf { it.isNotBlank() }
            all.add(Raw(id, json.optString("message"), attachment))
        }
        if (after.isBlank()) return all
        val index = all.indexOfFirst { it.id == after }
        return if (index < 0) all else all.drop(index + 1)
    }

    private fun download(url: String): ByteArray? = paced {
        runCatching { downloadPlain(url) }.getOrNull()
    }

    private fun downloadPlain(url: String): ByteArray? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 30000
        }
        val codeHttp = connection.responseCode
        val bytes = if (codeHttp in 200..299) connection.inputStream.use { it.readBytes() } else null
        connection.disconnect()
        if (codeHttp == 429) coolDown()
        return bytes
    }
}
