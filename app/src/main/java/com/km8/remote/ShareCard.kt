package com.km8.remote

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * "Share it": a manga-page card of what the sweep did (apps swept, the home screen, the TV), drawn as a picture and
 * handed to Android's share sheet. Nothing is sent anywhere unless the person picks an app to share it with.
 */
object ShareCard {

    fun share(context: Context, tvName: String, swept: Int, home: String) {
        val file = File(File(context.cacheDir, "share").apply { mkdirs() }, "km8-sweep.png")
        file.outputStream().use { draw(tvName, swept, home).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, "I swept $swept apps off my TV with RemoTV!")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share your sweep").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun draw(tvName: String, swept: Int, home: String): Bitmap {
        val w = 1080
        val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paper = 0xFFFBF8F1.toInt()
        val ink = 0xFF141414.toInt()
        val red = 0xFFE0262B.toInt()
        c.drawColor(paper)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // screentone
        p.color = 0xFFD9D4C7.toInt()
        var y = 0f
        var row = 0
        while (y < h) {
            var x = if (row % 2 == 0) 0f else 18f
            while (x < w) {
                c.drawCircle(x, y, 1.5f + 5f * y / h, p)
                x += 36f
            }
            y += 31f
            row++
        }
        // speed lines round the middle panel
        p.color = 0x22141414
        p.strokeWidth = 4f
        for (i in 0 until 70) {
            val a = i * Math.PI * 2 / 70
            c.drawLine(540f + cos(a).toFloat() * 330, 600f + sin(a).toFloat() * 330,
                540f + cos(a).toFloat() * 1200, 600f + sin(a).toFloat() * 1200, p)
        }
        // panels with hard shadows
        fun panel(r: RectF, fill: Int) {
            p.style = Paint.Style.FILL
            p.color = ink
            c.drawRoundRect(RectF(r.left + 14, r.top + 14, r.right + 14, r.bottom + 14), 18f, 18f, p)
            p.color = fill
            c.drawRoundRect(r, 18f, 18f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 9f
            p.color = ink
            c.drawRoundRect(r, 18f, 18f, p)
            p.style = Paint.Style.FILL
        }
        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        fun text(s: String, x: Float, yy: Float, size: Float, color: Int, center: Boolean = false) {
            p.typeface = bold
            p.textSize = size
            p.color = color
            p.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
            c.drawText(s, x, yy, p)
        }
        panel(RectF(70f, 70f, 1010f, 250f), ink)
        text("MY TV GOT SWEPT!", 540f, 195f, 92f, paper, center = true)
        panel(RectF(150f, 380f, 930f, 820f), paper)
        text("$swept", 540f, 640f, 260f, red, center = true)
        text(if (swept == 1) "APP SWEPT AWAY" else "APPS SWEPT AWAY", 540f, 760f, 62f, ink, center = true)
        panel(RectF(70f, 930f, 1010f, 1180f), paper)
        text(tvName.take(30), 110f, 1020f, 50f, ink)
        text("Home: $home".take(34), 110f, 1100f, 46f, ink)
        // sound effect
        c.save()
        c.rotate(-12f, 860f, 330f)
        text("SHAAA!", 860f, 350f, 96f, red, center = true)
        c.restore()
        text("RemoTV · disabled, never deleted · undo any time", 540f, 1290f, 34f, ink, center = true)
        return bmp
    }
}
