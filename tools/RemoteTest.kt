/*
 * Telegram ke buttons aur control link ki jaanch - bina phone, bina Gradle.
 *
 * Yahan do cheezein pakdi jaati hain, aur dono chup-chaap toot-ne wali hain:
 *
 *   1. Telegram reply keyboard par button ka LABEL hi wo text hai jo bot ko
 *      jata hai. Agar kisi button ka label alias() mein nahi hai, to us
 *      button par tap karne se phone kehta hai "Samajh nahi aaya" - aur
 *      compiler kuch nahi bolta, kyunki dono sirf String hain.
 *
 *   2. "/start <code>" ka regex. Isi par poori control link tiki hui hai.
 *      Ye galat ho jaye to link par tap karne ke baad kuch nahi hota.
 *
 * Private members reflection se padhe jaate hain - jaan-boojh kar, taaki
 * sirf test ke liye Remote.kt ka koi hissa public na karna pade.
 */

import java.io.File

fun main(args: Array<String>) {
    var pass = 0
    var fail = 0
    fun check(name: String, got: Any?, want: Any?) {
        if (got == want) {
            pass++; println("  PASS  $name")
        } else {
            fail++; println("  FAIL  $name -> mila=$got chahiye=$want")
        }
    }

    val cls = Class.forName("com.mustfa.heatguard.Remote")
    val instance = cls.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)

    val aliasMethod = cls.getDeclaredMethod("alias", String::class.java)
        .apply { isAccessible = true }
    fun alias(text: String) = aliasMethod.invoke(instance, text) as String

    val start = cls.getDeclaredField("START").apply { isAccessible = true }.get(null) as Regex
    fun startArg(text: String) = start.find(text)?.groupValues?.get(1).orEmpty()

    val keyboard = cls.getDeclaredField("KEYBOARD").apply { isAccessible = true }.get(null) as String

    println("1) Har button ka label ek asli command banta hai")
    /*
     * Command ki list Remote.kt se hi nikali jaati hai (remote-test.sh),
     * haath se yahan nahi likhi - warna naya command jodne par ye test
     * jhooth bolne lagta.
     */
    val known = File(args[0]).readLines().map { it.trim() }.filter { it.isNotBlank() }
    check("commands ki list mili", known.size > 10, true)

    val inner = keyboard.substringAfter("\"keyboard\":[").substringBefore("]]")
    val labels = Regex("\"([^\"]+)\"").findAll(inner).map { it.groupValues[1] }.toList()
    check("keyboard mein buttons hain", labels.size >= 10, true)
    for (label in labels) {
        val command = alias(label)
        val word = command.split(" ")[0]
        check("button \"$label\" -> $command", word in known, true)
    }

    println("2) Jo pehle se command likhte the, unka kuch nahi bigda")
    check("/sh pass-through", alias("/sh getprop ro.build.id"), "/sh getprop ro.build.id")
    check("/open pass-through", alias("/open whatsapp"), "/open whatsapp")
    check("anjaan text waisa hi", alias("kuch bhi"), "kuch bhi")

    println("3) Control link ka /start <code>")
    check("seedha code", startArg("/start ab2cd3ef4gh5jk"), "ab2cd3ef4gh5jk")
    check("bot ke naam ke saath", startArg("/start@heat_guard_bot ab2cd3ef"), "ab2cd3ef")
    check("khali /start par code nahi", startArg("/start"), "")
    check("doosri command match nahi karti", startArg("/status"), "")
    check("/startle dhoka nahi deta", startArg("/startle abc"), "")
    check("do argument wala nahi chalta", startArg("/start abc def"), "")

    println()
    println("PASS=$pass FAIL=$fail")
    if (fail > 0) kotlin.system.exitProcess(1)
}
