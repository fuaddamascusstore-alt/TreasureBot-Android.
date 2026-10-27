package com.treasurewatch.bot

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * On-device RGB template matching for a user-cropped screenshot of the chest.
 * The supplied crop must come from the same device/orientation at approximately the same scale.
 * Not an AI detector; false negatives are expected if TikTok changes icon size or appearance.
 */
object VisualMatcher {
    data class Hit(val x: Int, val y: Int, val score: Double)

    fun search(screen: Bitmap, pattern: Bitmap, fromY: Double, toY: Double): Hit? {
        val factor = min(1.0, 360.0 / screen.width)
        val w = max(1, (screen.width * factor).toInt())
        val h = max(1, (screen.height * factor).toInt())
        val tw = max(1, (pattern.width * factor).toInt())
        val th = max(1, (pattern.height * factor).toInt())
        if (tw !in 7..(w / 3) || th !in 7..(h / 3)) return null
        val small = Bitmap.createScaledBitmap(screen, w, h, false)
        val templ = Bitmap.createScaledBitmap(pattern, tw, th, false)
        try {
            val s = IntArray(w * h); small.getPixels(s, 0, w, 0, 0, w, h)
            val t = IntArray(tw * th); templ.getPixels(t, 0, tw, 0, 0, tw, th)
            // Flat, uniform crops produce false positives across generic buttons/backgrounds.
            var minIntensity = 255
            var maxIntensity = 0
            var opaque = 0
            for (px in t) {
                if (Color.alpha(px) < 200) continue
                opaque++
                val intensity = (Color.red(px) + Color.green(px) + Color.blue(px)) / 3
                minIntensity = min(minIntensity, intensity)
                maxIntensity = max(maxIntensity, intensity)
            }
            if (opaque < 32 || maxIntensity - minIntensity < 28) return null
            val y0 = max(0, (fromY * h).toInt())
            val y1 = min(h - th, (toY * h).toInt())
            if (y1 < y0) return null
            var best = 0.0; var bx = -1; var by = -1
            val dx = max(1, tw / 7); val dy = max(1, th / 7)
            // Check <=64 pixels per candidate, skipping transparent samples.
            for (y in y0..y1 step 3) for (x in 0..(w - tw) step 3) {
                var total = 0L; var count = 0
                for (j in 0 until th step dy) for (i in 0 until tw step dx) {
                    val a = t[j * tw + i]
                    if (Color.alpha(a) < 200) continue
                    val b = s[(y + j) * w + x + i]
                    total += abs(Color.red(a) - Color.red(b))
                    total += abs(Color.green(a) - Color.green(b))
                    total += abs(Color.blue(a) - Color.blue(b))
                    count++
                }
                if (count < 8) continue
                val score = 1.0 - total.toDouble() / (count * 765.0)
                if (score > best) { best = score; bx = x; by = y }
            }
            return if (best >= 0.90 && bx >= 0) Hit(
                ((bx + tw / 2.0) / factor).toInt(),
                ((by + th / 2.0) / factor).toInt(), best
            ) else null
        } finally {
            if (small !== screen) small.recycle()
            if (templ !== pattern) templ.recycle()
        }
    }
}
