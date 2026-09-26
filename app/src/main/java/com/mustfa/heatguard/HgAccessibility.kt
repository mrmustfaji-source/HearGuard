package com.mustfa.heatguard

/*
 * ===========================================================================
 *  HgAccessibility.kt - screen dekhna/chalana BINA Shizuku ke
 *
 *  User ne sahi sawaal poocha: "itni saari remote apk hain jinme Shizuku
 *  nahi lagta, phone connect ho jate hain" - aur wo sahi hain. Wo apps
 *  Android ke apne Accessibility Service se ye karti hain:
 *
 *    - Screenshot lena       -> takeScreenshot() (Android 11+)
 *    - Tap/swipe bhejna      -> dispatchGesture()
 *    - Back/Home/Recents     -> performGlobalAction()
 *
 *  Sabka fayda: ek baar Settings > Accessibility mein ON karo, uske baad
 *  HAMESHA chalta hai - koi wireless debugging, koi pairing code, koi
 *  dobara popup nahi. Isiliye ScreenService/ScreenActivity ab isi se chalte
 *  hain, Shizuku se nahi.
 *
 *  Jo isse NAHI ho sakta (aur kisi Accessibility Service se kabhi nahi
 *  hoga, ye Android ka apna security wall hai): kisi DOOSRI app ko force-
 *  stop karna, ya uska standby bucket (deep sleep) badalna. Wahan `Shell.kt`
 *  abhi bhi Shizuku hi maangega - koi Accessibility trick wahan kaam nahi
 *  karti, kyunki wo do cheezein signature-level permission maangti hain,
 *  UI simulate karne se nahi milti.
 * ===========================================================================
 */

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HgAccessibility : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Kuch nahi sunna - hume sirf screenshot/gesture/global-action ki
        // "power" chahiye, events ki nahi.
    }

    override fun onInterrupt() {}

    companion object {
        @Volatile private var instance: HgAccessibility? = null

        /** User ne Settings > Accessibility mein ON kiya hua hai kya. */
        fun isEnabled(): Boolean = instance != null

        /**
         * Screenshot lo, JPEG bana kar do. Async API ko yahan synchronous
         * bana diya hai (CountDownLatch se) - caller hamesha background
         * thread par hota hai (ScreenService.loop), to blocking theek hai.
         */
        fun captureJpeg(): ByteArray? {
            val svc = instance ?: return null
            val latch = CountDownLatch(1)
            var result: ByteArray? = null
            val started = runCatching {
                svc.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    svc.mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            result = runCatching {
                                val hw = screenshot.hardwareBuffer
                                val bmp = Bitmap.wrapHardwareBuffer(hw, screenshot.colorSpace)
                                hw.close()
                                // Hardware bitmap seedha compress nahi hoti - normal
                                // bitmap mein copy karna padta hai.
                                val soft = bmp?.copy(Bitmap.Config.ARGB_8888, false)
                                bmp?.recycle()
                                soft?.let { s ->
                                    val out = ByteArrayOutputStream()
                                    s.compress(Bitmap.CompressFormat.JPEG, 55, out)
                                    s.recycle()
                                    out.toByteArray()
                                }
                            }.getOrNull()
                            latch.countDown()
                        }

                        override fun onFailure(errorCode: Int) {
                            latch.countDown()
                        }
                    }
                )
                true
            }.getOrDefault(false)
            if (!started) return null
            latch.await(4, TimeUnit.SECONDS)
            return result?.takeIf { it.size > 100 }
        }

        fun tap(x: Int, y: Int): Boolean =
            gesture(Path().apply { moveTo(x.toFloat(), y.toFloat()) }, 60)

        fun swipe(x1: Int, y1: Int, x2: Int, y2: Int): Boolean =
            gesture(
                Path().apply {
                    moveTo(x1.toFloat(), y1.toFloat())
                    lineTo(x2.toFloat(), y2.toFloat())
                },
                280
            )

        private fun gesture(path: Path, durationMs: Long): Boolean {
            val svc = instance ?: return false
            val latch = CountDownLatch(1)
            var ok = false
            val description = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
            val started = runCatching {
                svc.dispatchGesture(
                    description,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            ok = true
                            latch.countDown()
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            latch.countDown()
                        }
                    },
                    null
                )
            }.getOrDefault(false)
            if (!started) return false
            latch.await(2, TimeUnit.SECONDS)
            return ok
        }

        fun back(): Boolean = runCatching {
            instance?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false
        }.getOrDefault(false)

        fun home(): Boolean = runCatching {
            instance?.performGlobalAction(GLOBAL_ACTION_HOME) ?: false
        }.getOrDefault(false)

        fun recents(): Boolean = runCatching {
            instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) ?: false
        }.getOrDefault(false)
    }
}
