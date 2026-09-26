package com.mustfa.heatguard

/*
 * ===========================================================================
 *  Shell.kt - command chalane ka ek hi darwaza
 *
 *  Do raaste hain, aur app apne aap behtar wala chun leti hai:
 *
 *    1. SHIZUKU - `shell` ke adhikaar. Isse app ka naam bhi pata chalta hai,
 *       deep sleep bhi lagta hai, aur app band bhi ho jaati hai.
 *    2. KHUD (plain exec) - sirf wahi jo app ki apni haisiyat se ho sake.
 *       DUMP mili ho to batterystats padh lena, bas.
 *
 *  Shizuku na ho to app band nahi hoti - raasta 2 par chalti rehti hai.
 *
 *  Shizuku ka process alag hai, isliye usse baat karna ASYNC hai: bind karo,
 *  connection ka intezaar karo, phir call karo. Yahan uspar ek seedha
 *  "command do, output lo" wala parda daala gaya hai, jismein intezaar ka
 *  hisaab andar chhupa hai. Isi wajah se [run] ko kabhi bhi main thread par
 *  nahi bulana - wo block karta hai.
 * ===========================================================================
 */

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object Shell {

    private val KEYS = intArrayOf(
        3, 4, 19, 20, 21, 22, 23, 24, 25, 26, 27,
        61, 62, 66, 67, 82, 84, 85, 86, 87, 88, 91, 92, 93,
        111, 120, 122, 123, 164, 168, 169, 187,
        220, 221, 223, 224, 231, 277, 278, 279, 284, 285, 318
    )

    /** Bind hone ka intezaar itna hi - iske baad Shizuku ko mara hua maano. */
    private const val BIND_TIMEOUT_SECONDS = 12L

    /** requestPermission() ke liye code; Activity isi se jawab pehchanti hai. */
    const val SHIZUKU_PERMISSION_REQUEST = 4242

    @Volatile
    private var service: IShellService? = null
    private var latch: CountDownLatch? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = if (binder != null && binder.pingBinder()) {
                IShellService.Stub.asInterface(binder)
            } else {
                null
            }
            latch?.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    // Method ke naam jar se dekhe gaye hain, yaad se nahi: builder mein
    // processNameSuffix() hai, processName() nahi - pehli koshish usi par
    // fail hui thi.
    private fun userServiceArgs(context: Context) =
        Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellService::class.java.name))
            .daemon(false)      // kaam khatam, process khatam - battery ke liye
            .processNameSuffix("shell")
            .version(1)

    /* --------------------------------------------- Shizuku ka haal --------------------------------------------- */

    /** Shizuku app chal rahi hai ya nahi. */
    fun shizukuRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    /** Chal rahi hai AUR humein ijazat de chuki hai. */
    fun shizukuReady(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestShizukuPermission() {
        runCatching { Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST) }
    }

    /** Shizuku manager app ka asli package - "moe.shizuku.manager.permission.API_V23" isi se aati hai. */
    private const val SHIZUKU_PACKAGE = "moe.shizuku.manager"

    /**
     * Shizuku app khud khol do.
     *
     * HeatGuard Shizuku ko khud chalu NAHI kar sakti - iske liye wahi Binder
     * chahiye jo abhi maujood hi nahi hai (murgi-anda wali baat). Jo ho sakta
     * hai wo bas itna hai: user ko seedha Shizuku ki screen tak pahuncha
     * dena, taaki use dhoondhna na pade.
     *
     * @return false matlab Shizuku app hi installed nahi hai.
     */
    fun openShizukuApp(context: Context): Boolean = runCatching {
        val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) ?: return false
        launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        true
    }.getOrDefault(false)

    /* --------------------------------------------- Command chalana --------------------------------------------- */

    /**
     * Command chalao aur output lauta do.
     *
     * Shizuku maujood ho to uske zariye (shell ke adhikaar se), warna khud se.
     * Main thread par kabhi mat bulao.
     */
    fun run(context: Context, command: String): String {
        if (shizukuReady()) {
            runAsShell(context, command)?.let { return it }
        }
        return runAsSelf(command)
    }

    /** true matlab command `shell` ke adhikaar se chalega. */
    fun canRunAsShell(context: Context): Boolean = shizukuReady()

    private fun runAsShell(context: Context, command: String): String? {
        val bound = ensureBound(context) ?: return null
        return runCatching { bound.exec(command) }.getOrNull()
    }

    private fun ensureBound(context: Context): IShellService? {
        service?.let { existing ->
            // Shizuku band ho gaya ho to purana binder murda hota hai.
            if (runCatching { existing.asBinder().pingBinder() }.getOrDefault(false)) return existing
            service = null
        }
        val waiter = CountDownLatch(1)
        latch = waiter
        val started = runCatching {
            Shizuku.bindUserService(userServiceArgs(context), connection)
        }.isSuccess
        if (!started) return null
        runCatching { waiter.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        return service
    }

    private fun runAsSelf(command: String): String = runCatching {
        val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        val text = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
        process.waitFor()
        text
    }.getOrDefault("")

    /* --------------------------------------------- Wo kaam jo sirf shell kar sakta hai --------------------------------------------- */

    /**
     * App ko deep sleep (restricted bucket) mein daalo, ya wapas nikalo.
     *
     * @return false matlab Shizuku nahi hai - app khud ye kabhi nahi kar sakti.
     */
    fun setDeepSleep(context: Context, packages: List<String>, deep: Boolean): Boolean =
        setBucket(context, packages, if (deep) "restricted" else "active")

    /**
     * Kai apps ka standby bucket ek saath badlo.
     *
     * @param bucket "restricted" (deep sleep), "rare" (sleeping), "active"
     * @return false matlab Shizuku nahi hai - app khud ye kabhi nahi kar sakti.
     */
    fun setBucket(context: Context, packages: List<String>, bucket: String): Boolean {
        if (!shizukuReady() || packages.isEmpty()) return false
        // Ek hi command mein sab - har package ke liye alag binder call karne se
        // 200 apps par screen jam jaati hai.
        val script = packages.joinToString("; ") { "am set-standby-bucket $it $bucket" }
        runAsShell(context, script) ?: return false
        return true
    }

    /*
     * Screen dekhna/chalana - pehle Accessibility (HgAccessibility.kt),
     * na ho to Shizuku. Pehle ye SIRF Shizuku se hota tha - "itni saari
     * remote apps hain jinme Shizuku nahi lagta" (user ka sawaal, sahi
     * tha) ke jawab mein ye tarteeb ban gayi. Accessibility ek baar ON
     * karne se hamesha chalta hai, na koi wireless debugging na koi
     * dobara popup.
     */

    /** Screen ki chhoti JPEG. Dono na hon to null. */
    fun captureJpeg(context: Context): ByteArray? {
        HgAccessibility.captureJpeg()?.let { return it }
        if (!shizukuReady()) return null
        val bound = ensureBound(context) ?: return null
        val bytes = runCatching { bound.capture() }.getOrNull() ?: return null
        return bytes.takeIf { it.size > 100 }
    }

    /**
     * Asli pixel size. `wm size` (Shizuku) ho to wahi, warna Android se
     * seedha poochho - iske liye koi permission chahiye hi nahi.
     */
    fun screenPixels(context: Context): Pair<Int, Int>? {
        if (shizukuReady()) {
            val out = runAsShell(context, "wm size")
            if (out != null) {
                val override = Regex("Override size:\\s*(\\d+)x(\\d+)").find(out)
                val physical = Regex("Physical size:\\s*(\\d+)x(\\d+)").find(out)
                val match = override ?: physical
                val w = match?.groupValues?.get(1)?.toIntOrNull()
                val h = match?.groupValues?.get(2)?.toIntOrNull()
                if (w != null && h != null && w > 0 && h > 0) return w to h
            }
        }
        val metrics = context.resources.displayMetrics
        return if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
            metrics.widthPixels to metrics.heightPixels
        } else {
            null
        }
    }

    fun tap(context: Context, x: Int, y: Int): Boolean {
        if (x < 0 || y < 0) return false
        if (HgAccessibility.tap(x, y)) return true
        return inject(context, "input tap $x $y")
    }

    fun swipe(context: Context, x1: Int, y1: Int, x2: Int, y2: Int): Boolean {
        if (x1 < 0 || y1 < 0 || x2 < 0 || y2 < 0) return false
        if (HgAccessibility.swipe(x1, y1, x2, y2)) return true
        return inject(context, "input swipe $x1 $y1 $x2 $y2 250")
    }

    /**
     * Remote pad ki keys. Sirf yahi codes - jo number user ne type kiya
     * ho, wo seedha shell mein nahi jaata. Back/Home/Recents Accessibility
     * ke apne "global action" se milte hain - baaki (volume wagairah) ke
     * liye aisa koi raasta nahi hai, unhe Shizuku hi chahiye.
     */
    fun key(context: Context, code: Int): Boolean {
        if (code !in KEYS) return false
        val viaAccessibility = when (code) {
            4 -> HgAccessibility.back()
            3 -> HgAccessibility.home()
            187 -> HgAccessibility.recents()
            else -> false
        }
        if (viaAccessibility) return true
        if (!shizukuReady()) return false
        val out = runAsShell(context, "input keyevent $code") ?: return false
        return !out.contains("Exception", ignoreCase = true)
    }

    private fun inject(context: Context, command: String): Boolean {
        if (!shizukuReady()) return false
        val out = runAsShell(context, command) ?: return false
        return !out.contains("Exception", ignoreCase = true)
    }

    /**
     * App ko sach mein band karo. FORCE_STOP_PACKAGES signature-level hai.
     * Shizuku ke bina ye kabhi nahi chalega.
     */
    /** input text sirf ASCII. Space %s ban jata hai. Baaki hat jata hai. */
    fun typeText(context: Context, raw: String): Boolean {
        val safe = buildString {
            for (ch in raw) {
                when {
                    ch == ' ' -> append("%s")
                    ch.isLetterOrDigit() || ch in "._@+-," -> append(ch)
                }
            }
        }
        if (safe.isEmpty()) return false
        if (!shizukuReady()) return false
        val out = runAsShell(context, "input text $safe") ?: return false
        return !out.contains("Exception", ignoreCase = true)
    }

    fun forceStop(context: Context, packageName: String): Boolean {
        if (!shizukuReady()) return false
        val out = runAsShell(context, "am force-stop $packageName") ?: return false
        return !out.contains("Exception", ignoreCase = true) &&
            !out.contains("Permission Denial", ignoreCase = true)
    }
}
