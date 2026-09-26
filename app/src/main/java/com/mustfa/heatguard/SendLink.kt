package com.mustfa.heatguard

/*
 * Is phone se doosre phone ko app bhejna - do ALAG links, do ALAG buttons.
 *
 * Pehle ye ek hi message mein APK aur pairing code dono bhejta tha. User ne
 * saaf mana kiya: "voh link alag banao aur download ka link alag se banao".
 * Ab do kaam, do buttons:
 *
 *   1. shareApk()      -> sirf APK download karne ki link (GitHub Releases)
 *   2. sharePairing()  -> sirf pairing code/link (ntfy/deep-link wala)
 *
 * Pehla ek hi baar chahiye (jab tak app update na ho). Doosra tab tak jab
 * naya phone jodna ho.
 *
 * APK GitHub Releases se aati hai, ntfy.sh se nahi (14MB ki seemaa thi aur
 * kabhi-kabhi expire ho jaati). Wo hi ek link hamesha sabse NAYI build
 * deti hai - "iski-wajah-se" agar naya build banega, link wahi rahegi,
 * bas file peeche se badal jaati hai. Ye kaam BUILD_AND_INSTALL.bat
 * karta hai (publish-github-release.ps1 ke zariye), Android app khud
 * kabhi apna APK GitHub par nahi chadhati.
 */

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast

object SendLink {

    /**
     * Static hai - build script hi is jagah par naya APK chadhata hai. App
     * khud isse kabhi nahi badalti, isliye ek constant kaafi hai.
     */
    private const val APK_URL =
        "https://github.com/mrmustfaji-source/HearGuard/releases/latest/download/heatguard.apk"

    /** Button 1: doosre phone ko sirf app ki download link bhejo. */
    fun shareApk(activity: Activity) {
        val text = activity.getString(R.string.send_apk_message, APK_URL)
        activity.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Heat Guard", text))
        shareText(activity, text)
    }

    /** Button 2: doosre phone ko sirf pairing code/link bhejo. */
    fun sharePairing(activity: Activity, done: (String) -> Unit = {}) {
        if (Link.code(activity).isBlank()) Link.newCode(activity)
        Link.setRole(activity, LinkRole.CONTROLLER)
        val code = Link.code(activity)
        val text = activity.getString(R.string.send_link_body, code)
        activity.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Heat Guard", text))
        shareText(activity, text)
        done(text)
    }

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

    /** Doosre phone par message copy ho to code apne aap lag jayega. */
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
