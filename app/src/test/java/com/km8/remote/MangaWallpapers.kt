package com.km8.remote

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws the MangaHome theme's TV wallpapers (1920x1080): ink-black pages with white screentone, speed lines,
 * panel frames, a sound effect and Remo bursting in from one side, keeping the middle calm for the home screen.
 * Run: MANGA_OUT=../manga-theme/wallpapers ./gradlew testDebugUnitTest --tests '*MangaWallpapers*'
 */
class MangaWallpapers {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6)

    private val ink = 0xFF121212.toInt()
    private val paper = 0xFFFBF8F1.toInt()
    private val red = 0xFFE0262B.toInt()
    private val gold = 0xFFFFC94A.toInt()

    @Test
    fun draw() {
        val out = System.getenv("MANGA_OUT")
        assumeTrue(out != null)
        File(out!!).mkdirs()
        data class Page(val word: String, val accent: Int, val remoLeft: Boolean, val fx: Float, val fy: Float, val seed: Int)
        listOf(
            Page("DON!", red, false, 0.82f, 0.62f, 1),
            Page("DOKI DOKI", red, true, 0.15f, 0.55f, 2),
            Page("ZAWA ZAWA", gold, false, 0.9f, 0.3f, 3),
            Page("GOGOGO", red, true, 0.1f, 0.35f, 4),
            Page("KIRA!", gold, false, 0.75f, 0.8f, 5),
            Page("SHAAA!", red, true, 0.25f, 0.75f, 6),
        ).forEachIndexed { i, p ->
            val bmp = page(p.word, p.accent, p.remoLeft, p.fx, p.fy, p.seed)
            File(out, "manga-%02d.png".format(i + 1)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun page(word: String, accent: Int, remoLeft: Boolean, fx: Float, fy: Float, seed: Int): Bitmap {
        val w = 1920
        val h = 1080
        val rnd = Random(seed)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawColor(ink)
        // Speed lines towards a focal point near one edge.
        val cx = w * fx
        val cy = h * fy
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 160) {
            val a = rnd.nextDouble(0.0, Math.PI * 2)
            val r0 = 220f + rnd.nextFloat() * 380f
            p.color = 0x00FFFFFF or ((18 + rnd.nextInt(40)) shl 24)
            p.strokeWidth = 1.5f + rnd.nextFloat() * 4f
            c.drawLine(cx + cos(a).toFloat() * r0, cy + sin(a).toFloat() * r0,
                cx + cos(a).toFloat() * 2600, cy + sin(a).toFloat() * 2600, p)
        }
        // Screentone, heavier towards the bottom corners.
        p.color = 0x22FFFFFF
        var y = 0f
        var row = 0
        while (y < h) {
            var x = if (row % 2 == 0) 0f else 13f
            while (x < w) {
                val edge = maxOf(kotlin.math.abs(x - w / 2f) / (w / 2f), y / h)
                c.drawCircle(x, y, 0.6f + 3.6f * edge * edge, p)
                x += 26f
            }
            y += 22.5f
            row++
        }
        // Panel frames in two corners.
        p.style = Paint.Style.STROKE
        p.strokeWidth = 10f
        p.color = 0x55FBF8F1
        c.drawRect(RectF(40f, 40f, 560f, 330f), p)
        c.drawRect(RectF(w - 600f, h - 330f, w - 40f, h - 40f), p)
        p.strokeWidth = 4f
        c.drawRect(RectF(60f, 60f, 540f, 310f), p)
        p.style = Paint.Style.FILL
        // Remo bursting in from one side, on a jagged paper burst.
        val rx = if (remoLeft) 230f else w - 230f
        val ry = h * 0.62f
        burst(c, rx, ry, 260f, paper, ink)
        remo(c, rx, ry + 10f, 300f)
        // The sound effect, big and tilted, outlined in ink over paper.
        val sx = if (remoLeft) w - 330f else 360f
        val sy = 330f + (seed % 3) * 70f
        c.save()
        c.rotate(if (remoLeft) 10f else -10f, sx, sy)
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
            textSize = if (word.length > 6) 120f else 170f
            textAlign = Paint.Align.CENTER
        }
        t.style = Paint.Style.STROKE
        t.strokeWidth = 22f
        t.color = paper
        c.drawText(word, sx, sy, t)
        t.style = Paint.Style.FILL
        t.color = accent
        c.drawText(word, sx, sy, t)
        c.restore()
        return bmp
    }

    private fun burst(c: Canvas, x: Float, y: Float, r: Float, fill: Int, line: Int) {
        val path = Path()
        val spikes = 22
        for (i in 0 until spikes * 2) {
            val a = i * Math.PI / spikes
            val rr = if (i % 2 == 0) r else r * 0.78f
            val px = x + cos(a).toFloat() * rr
            val py = y + sin(a).toFloat() * rr
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = fill
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 8f
        p.color = line
        c.drawPath(path, p)
    }

    /** Remo, as in Manga.kt's Mascot, drawn with android.graphics; {@code s} is his box size. */
    private fun remo(c: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val l = cx - s / 2
        val t = cy - s / 2
        fun X(f: Float) = l + f * s
        fun Y(f: Float) = t + f * s
        p.color = ink
        p.strokeWidth = s * 0.03f
        p.strokeCap = Paint.Cap.ROUND
        c.drawLine(X(0.5f), Y(0.2f), X(0.62f), Y(0.04f), p)
        p.color = red
        c.drawCircle(X(0.63f), Y(0.04f), s * 0.04f, p)
        p.color = ink
        val hair = Path().apply {
            moveTo(X(0.14f), Y(0.55f)); lineTo(X(0.08f), Y(0.30f)); lineTo(X(0.24f), Y(0.36f))
            lineTo(X(0.26f), Y(0.14f)); lineTo(X(0.40f), Y(0.28f)); lineTo(X(0.50f), Y(0.10f))
            lineTo(X(0.60f), Y(0.28f)); lineTo(X(0.76f), Y(0.15f)); lineTo(X(0.77f), Y(0.36f))
            lineTo(X(0.93f), Y(0.31f)); lineTo(X(0.86f), Y(0.55f)); close()
        }
        c.drawPath(hair, p)
        val fx = X(0.5f)
        val fy = Y(0.58f)
        val fr = s * 0.34f
        p.color = paper
        c.drawCircle(fx, fy, fr, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.035f
        p.color = ink
        c.drawCircle(fx, fy, fr, p)
        p.style = Paint.Style.FILL
        val fringe = Path().apply {
            moveTo(fx - fr * 0.95f, fy - fr * 0.25f); lineTo(fx - fr * 0.55f, fy - fr * 0.55f)
            lineTo(fx - fr * 0.35f, fy - fr * 0.18f); lineTo(fx - fr * 0.05f, fy - fr * 0.62f)
            lineTo(fx + fr * 0.15f, fy - fr * 0.2f); lineTo(fx + fr * 0.45f, fy - fr * 0.6f)
            lineTo(fx + fr * 0.6f, fy - fr * 0.22f); lineTo(fx + fr * 0.95f, fy - fr * 0.3f)
            lineTo(fx + fr, fy - fr * 0.9f); lineTo(fx - fr, fy - fr * 0.9f); close()
        }
        c.drawPath(fringe, p)
        for (side in listOf(-1, 1)) {
            val ex = fx + side * fr * 0.42f
            val ey = fy + fr * 0.12f
            p.color = ink
            c.drawOval(RectF(ex - fr * 0.17f, ey - fr * 0.26f, ex + fr * 0.17f, ey + fr * 0.26f), p)
            p.color = paper
            c.drawCircle(ex + fr * 0.05f, ey - fr * 0.1f, fr * 0.08f, p)
            c.drawCircle(ex - fr * 0.06f, ey + fr * 0.12f, fr * 0.04f, p)
            p.color = 0x59E0262B
            c.drawOval(RectF(ex - fr * 0.2f, ey + fr * 0.32f, ex + fr * 0.1f, ey + fr * 0.44f), p)
        }
        p.color = ink
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.035f
        val m = Path().apply { moveTo(fx - fr * 0.16f, fy + fr * 0.5f); quadTo(fx, fy + fr * 0.72f, fx + fr * 0.16f, fy + fr * 0.5f) }
        c.drawPath(m, p)
    }
}
