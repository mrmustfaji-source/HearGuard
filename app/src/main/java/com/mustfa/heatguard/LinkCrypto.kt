package com.mustfa.heatguard

/*
 * Relay (ntfy.sh) command aur screen dono dekhta hai. Topic ka naam pairing
 * code nahi hai - code ka hash hai. Key alag hash hai. Jiske paas code nahi,
 * wo na topic bana sakta hai, na tasveer khol sakta hai.
 *
 * Server ko topic dikhta hai, code nahi. Isliye wahan seedhi tasveer nahi
 * padhi ja sakti.
 */

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object LinkCrypto {

    private const val NONCE = 12
    private const val TAG_BITS = 128

    fun topic(code: String, kind: String): String {
        val hex = sha256("$code|hg-topic-v1|$kind").joinToString("") { "%02x".format(it) }
        return hex.take(32)
    }

    fun seal(code: String, plain: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE)
        SecureRandom().nextBytes(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(code), GCMParameterSpec(TAG_BITS, nonce))
        val enc = cipher.doFinal(plain)
        return nonce + enc
    }

    fun open(code: String, sealed: ByteArray): ByteArray? {
        if (sealed.size <= NONCE) return null
        return runCatching {
            val nonce = sealed.copyOfRange(0, NONCE)
            val enc = sealed.copyOfRange(NONCE, sealed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(code), GCMParameterSpec(TAG_BITS, nonce))
            cipher.doFinal(enc)
        }.getOrNull()
    }

    fun sealText(code: String, text: String): String {
        val clipped = if (text.length <= 2700) text else text.take(2700) + "\n...(kata)"
        return Base64.getEncoder().encodeToString(seal(code, clipped.toByteArray(Charsets.UTF_8)))
    }

    fun openText(code: String, message: String): String? {
        val raw = runCatching { Base64.getDecoder().decode(message.trim()) }.getOrNull() ?: return null
        val plain = open(code, raw) ?: return null
        return plain.toString(Charsets.UTF_8)
    }

    private fun key(code: String): SecretKeySpec =
        SecretKeySpec(sha256("$code|hg-key-v1"), "AES")

    private fun sha256(text: String): ByteArray =
        java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
}
