package com.mustfa.heatguard

/*
 * ===========================================================================
 *  ShellService.kt - wo hissa jo `shell` user ban kar chalta hai
 *
 *  Is class ka code HAMARI app ke process mein nahi chalta. Shizuku ise
 *  alag se, `shell` (uid 2000) ke roop mein shuru karta hai - wahi uid jo
 *  `adb shell` ko milti hai.
 *
 *  Isi ek farak se wo sab ho jata hai jo app khud kabhi nahi kar sakti thi.
 *  Maine ye dono app ki haisiyat se test kiye the aur dono mana hue the:
 *
 *      am set-standby-bucket <pkg> restricted
 *          -> SecurityException: requires CHANGE_APP_IDLE_STATE
 *      am force-stop <pkg>
 *          -> FORCE_STOP_PACKAGES chahiye, jo signature|privileged hai
 *
 *  Yahan se dono chal jaate hain, kyunki `shell` ke paas ye adhikaar hain.
 *
 *  SAAVDHANI: yahan jo bhi aata hai wo shell ke adhikaar se chalta hai.
 *  Isliye command sirf app ke andar se banti hai, user se kabhi nahi li
 *  jaati - Shell.kt dekhiye.
 * ===========================================================================
 */

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader

class ShellService : IShellService.Stub() {

    override fun exec(command: String?): String {
        val cmd = command ?: return ""
        return runCatching {
            val process = ProcessBuilder("sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val text = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            process.waitFor()
            text
        }.getOrElse { "ERROR: ${it.message}" }
    }

    /**
     * Shizuku ise band karne ke liye bulata hai.
     *
     * exitProcess isliye ki ye poora ek alag process hai; sirf lautne se wo
     * zinda pada rehta aur battery kharch karta - jo is app ke maqsad ke
     * bilkul khilaf hai.
     */
    /**
     * screencap PNG /data/local/tmp par likhta hai. App wo path padh nahi
     * sakti, shell padh sakta hai. Yahan JPEG bana kar bytes lautate hain.
     * Binder ki had ~1 MB hai, isliye chaudaai 480 aur quality 35.
     */
    override fun capture(): ByteArray {
        val path = "/data/local/tmp/heatguard-frame.png"
        val file = File(path)
        return runCatching {
            val process = ProcessBuilder("sh", "-c", "screencap -p $path")
                .redirectErrorStream(true)
                .start()
            process.inputStream.readBytes()
            val code = process.waitFor()
            if (code != 0 || !file.exists() || file.length() < 32) return@runCatching ByteArray(0)
            val full = BitmapFactory.decodeFile(path) ?: return@runCatching ByteArray(0)
            val maxW = 480
            val scaled = if (full.width > maxW) {
                val h = (full.height.toLong() * maxW / full.width).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(full, maxW, h, true)
            } else {
                full
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 35, out)
            if (scaled !== full) scaled.recycle()
            full.recycle()
            out.toByteArray()
        }.getOrDefault(ByteArray(0)).also {
            runCatching { file.delete() }
        }
    }

    override fun destroy() {
        kotlin.system.exitProcess(0)
    }
}
