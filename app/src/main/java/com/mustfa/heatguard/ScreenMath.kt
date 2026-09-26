package com.mustfa.heatguard

/*
 * Controller ki screen par ungli, aur doosre phone ke asli pixel.
 * Beech mein 0..10000 ka hisaab, taki JPEG chhoti ho ya badi, tap sahi pade.
 */

object ScreenMath {

    data class Norm(val x: Int, val y: Int)

    /**
     * ImageView FIT_CENTER: tasveer beech mein hoti hai, kinaare khaali.
     * Khaali hisse par ungli null lautati hai - wahan tap nahi bhejna.
     */
    fun normalize(
        touchX: Float,
        touchY: Float,
        viewW: Int,
        viewH: Int,
        imgW: Int,
        imgH: Int
    ): Norm? {
        if (viewW <= 0 || viewH <= 0 || imgW <= 0 || imgH <= 0) return null
        val scale = minOf(viewW.toFloat() / imgW, viewH.toFloat() / imgH)
        val dispW = imgW * scale
        val dispH = imgH * scale
        val left = (viewW - dispW) / 2f
        val top = (viewH - dispH) / 2f
        val x = touchX - left
        val y = touchY - top
        if (x < 0f || y < 0f || x > dispW || y > dispH) return null
        return Norm(
            (x / dispW * 10000f).toInt().coerceIn(0, 10000),
            (y / dispH * 10000f).toInt().coerceIn(0, 10000)
        )
    }

    fun toScreen(nx: Int, ny: Int, screenW: Int, screenH: Int): Pair<Int, Int> {
        if (screenW <= 0 || screenH <= 0) return 0 to 0
        val x = nx.coerceIn(0, 10000) * screenW / 10000
        val y = ny.coerceIn(0, 10000) * screenH / 10000
        return x to y
    }
}
