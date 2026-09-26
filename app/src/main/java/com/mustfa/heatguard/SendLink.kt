package com.mustfa.heatguard

/*
 * Is phone se doosre phone ko app bhejna.
 *
 * Android background mein APK install nahi karne deta. Isliye yahan file
 * WhatsApp wagairah se jaati hai, aur doosre phone par ek baar Install
 * dabana padta hai. Code message mein hota hai, taaki type na karna pade.
 */

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object SendLink {

    fun share(activity: Activity, done: (String) -> Unit = {}) {
        if (Link.code(activity).isBlank()) Link.newCode(activity)
        Link.setRole(activity, LinkRole.CONTROLLER)
        val code = Link.code(activity)
        val join = "https://heatguard.app/p/$code"
        Toast.makeText(activity, "Link ban rahi hai...", Toast.LENGTH_SHORT).show()
        Thread {
            val fileUrl = uploadApk(activity, code)
            val text = if (fileUrl != null) "$fileUrl\n$join" else join
            activity.runOnUiThread {
                activity.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("Heat Guard", text))
                shareText(activity, text)
                done(text)
                Toast.makeText(
                    activity,
                    if (fileUrl != null) "Ye link APK download karegi. Install dabana padega."
                    else "APK link nahi bani. Sirf judne wali link gayi.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }.start()
    }

    /** APK ntfy par rakho. Wapas https link aati hai jis se file download hoti hai. */
    private fun uploadApk(activity: Activity, code: String): String? = runCatching {
        val apk = File(activity.applicationInfo.sourceDir)
        if (!apk.exists() || apk.length() > 14L * 1024 * 1024) return null
        val topic = LinkCrypto.topic(code, "apk")
        val connection = (URL("https://ntfy.sh/$topic").openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            connectTimeout = 20000
            readTimeout = 120000
            setRequestProperty("Filename", "hg$code.apk")
            setRequestProperty("Content-Type", "application/vnd.android.package-archive")
            setRequestProperty("Message", "https://heatguard.app/p/$code")
        }
        apk.inputStream().use { input -> connection.outputStream.use { input.copyTo(it) } }
        val body = if (connection.responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            ""
        }
        connection.disconnect()
        JSONObject(body).optJSONObject("attachment")?.optString("url")?.takeIf { it.startsWith("https://") }
    }.getOrNull()

    /** Link dabane se code khud lag jata hai. Likhna nahi padta. */
    fun acceptUri(activity: Activity, uri: android.net.Uri?): Boolean {
        if (uri == null) return false
        val code = uri.path.orEmpty().substringAfterLast('/').trim()
            .ifBlank { uri.lastPathSegment.orEmpty() }
        if (!code.matches(Regex("[abcdefghijkmnpqrstuvwxyz23456789]{12}"))) return false
        if (Link.role(activity) == LinkRole.CONTROLLER && Link.code(activity) == code) return false
        Link.setCode(activity, code)
        Link.setRole(activity, LinkRole.CONTROLLED)
        ListenService.start(activity)
        Toast.makeText(activity, activity.getString(R.string.send_link_joined), Toast.LENGTH_LONG).show()
        return true
    }

    /** Doosre phone par message copy ho to code apne aap lag jata hai. */
    fun acceptFromClipboard(activity: Activity): Boolean {
        if (Link.role(activity) == LinkRole.CONTROLLER && Link.code(activity).isNotBlank()) return false
        val clip = activity.getSystemService(ClipboardManager::class.java)
            ?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        val code = Regex("(?:heatguard://p/|https://heatguard.app/p/|HG1 code: )([abcdefghijkmnpqrstuvwxyz23456789]{12})")
            .find(clip)?.groupValues?.get(1) ?: return false
        if (code == Link.code(activity) && Link.role(activity) == LinkRole.CONTROLLED) return false
        Link.setCode(activity, code)
        Link.setRole(activity, LinkRole.CONTROLLED)
        ListenService.start(activity)
        Toast.makeText(activity, activity.getString(R.string.send_link_joined), Toast.LENGTH_LONG).show()
        return true
    }

    private fun shareText(activity: Activity, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        Notify.startSafely(activity, listOf(Intent.createChooser(send, null)))
    }
}
