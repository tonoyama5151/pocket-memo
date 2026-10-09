package com.tonoyama.pocketmemo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Draws what sits behind a sticky note: a built-in pattern or the user's photo,
 * adjusted so the note's text stays readable.
 */
object Backgrounds {
    const val PATTERN_PREFIX = "pattern:"

    data class PatternInfo(val key: String, val name: String)

    val PATTERNS = listOf(
        PatternInfo("grid", "方眼"),
        PatternInfo("ruled", "罫線"),
        PatternInfo("dots", "ドット"),
        PatternInfo("asanoha", "麻の葉"),
        PatternInfo("seigaiha", "青海波"),
        PatternInfo("ichimatsu", "市松"),
        PatternInfo("shippo", "七宝"),
        PatternInfo("watercolor", "水彩"),
        PatternInfo("gradient", "グラデーション"),
        PatternInfo("paper", "紙"),
        PatternInfo("sakura", "桜"),
        PatternInfo("momiji", "紅葉"),
        PatternInfo("snow", "雪の結晶"),
    )

    fun isPattern(bg: String) = bg.startsWith(PATTERN_PREFIX)
    fun patternKey(bg: String) = bg.removePrefix(PATTERN_PREFIX)
    fun patternName(bg: String) = PATTERNS.firstOrNull { it.key == patternKey(bg) }?.name ?: ""

    // Fixed inks so notes look the same in light and dark mode.
    private fun tagInk(color: String): Int = when (color) {
        "red" -> 0xFFD2564B.toInt()
        "yellow" -> 0xFFC9921A.toInt()
        "green" -> 0xFF3C8F5A.toInt()
        "blue" -> 0xFF3B6FD0.toInt()
        "" -> 0xFFB48A2C.toInt()
        else -> 0xFF24427F.toInt()
    }

    private fun notePaper(color: String): Int = when (color) {
        "red" -> 0xFFF9D3CE.toInt()
        "yellow" -> 0xFFFFE27A.toInt()
        "green" -> 0xFFCFEBD6.toInt()
        "blue" -> 0xFFD3E1F7.toInt()
        "" -> 0xFFFFF3B8.toInt()
        else -> 0xFFFAFAF7.toInt()
    }

    fun textColor(textColor: String): Int = if (textColor == "light") Color.WHITE else 0xFF2A2A2A.toInt()

    /**
     * Renders the background at exactly [w]×[h] pixels. [unit] is pixels per dp at that size.
     * Returns null when the memo has no background.
     */
    fun render(context: Context, bg: String, color: String, textColor: String, w: Int, h: Int, unit: Float): Bitmap? {
        if (bg.isEmpty() || w <= 0 || h <= 0) return null
        val light = textColor == "light"
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val canvas = Canvas(out)
        if (isPattern(bg)) {
            val clearish = color == "clear" || color == ""
            val base: Int
            val ink: Int
            if (light) {
                base = if (color == "clear" || color == "") 0xFF1F2A44.toInt() else ColorUtils.blendARGB(tagInk(color), Color.BLACK, 0.55f)
                ink = 0xFFFFFFFF.toInt()
            } else {
                base = notePaper(color)
                ink = tagInk(color)
            }
            val seasonal = seasonalInk(patternKey(bg), clearish, light, ink)
            drawPattern(canvas, patternKey(bg), w, h, unit, base, ink, seasonal, light)
        } else {
            val photo = MemoImages.loadRaw(context, bg) ?: return null
            drawPhoto(canvas, photo, w, h, color, light)
            photo.recycle()
        }
        return out
    }

    private fun seasonalInk(key: String, clearish: Boolean, light: Boolean, ink: Int): Int {
        if (!clearish) return ink
        return when (key) {
            "sakura" -> if (light) 0xFFF4C7D3.toInt() else 0xFFE58FA8.toInt()
            "momiji" -> if (light) 0xFFF2A07E.toInt() else 0xFFD9603B.toInt()
            "snow" -> if (light) 0xFFDDE8F7.toInt() else 0xFF6F9AD3.toInt()
            else -> ink
        }
    }

    private fun drawPhoto(canvas: Canvas, photo: Bitmap, w: Int, h: Int, color: String, light: Boolean) {
        val scale = max(w.toFloat() / photo.width, h.toFloat() / photo.height)
        val m = Matrix().apply {
            postScale(scale, scale)
            postTranslate((w - photo.width * scale) / 2f, (h - photo.height * scale) / 2f)
        }
        val tinted = !light && color != "clear"
        // Dark text: wash the photo toward white. White text: dim it.
        val f = when {
            light -> 0.45f
            tinted -> 0.20f
            else -> 0.40f
        }
        val cm = if (light) {
            ColorMatrix(floatArrayOf(1 - f, 0f, 0f, 0f, 0f, 0f, 1 - f, 0f, 0f, 0f, 0f, 0f, 1 - f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
        } else {
            val o = 255f * f
            ColorMatrix(floatArrayOf(1 - f, 0f, 0f, 0f, o, 0f, 1 - f, 0f, 0f, o, 0f, 0f, 1 - f, 0f, o, 0f, 0f, 0f, 1f, 0f))
        }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) }
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(photo, m, paint)
        if (tinted) {
            // Label color laid over the photo at about 55%.
            canvas.drawColor(ColorUtils.setAlphaComponent(notePaper(color), 0x8C))
        }
    }

    private fun a(color: Int, alpha: Int) = ColorUtils.setAlphaComponent(color, alpha)

    private fun drawPattern(canvas: Canvas, key: String, w: Int, h: Int, u: Float, base: Int, ink: Int, seasonal: Int, light: Boolean) {
        canvas.drawColor(base)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, u)
            color = ink
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = ink }
        val boost = if (light) 0.6f else 1f
        fun al(v: Int) = (v * boost).toInt().coerceIn(0, 255)

        when (key) {
            "grid" -> {
                val step = 16 * u
                var i = 0
                var x = 0f
                while (x <= w) {
                    stroke.color = a(ink, al(if (i % 4 == 0) 110 else 60)); canvas.drawLine(x, 0f, x, h.toFloat(), stroke)
                    x += step; i++
                }
                i = 0
                var y = 0f
                while (y <= h) {
                    stroke.color = a(ink, al(if (i % 4 == 0) 110 else 60)); canvas.drawLine(0f, y, w.toFloat(), y, stroke)
                    y += step; i++
                }
            }
            "ruled" -> {
                val step = 26 * u
                stroke.color = a(ink, al(90))
                var y = step
                while (y <= h) {
                    canvas.drawLine(0f, y, w.toFloat(), y, stroke); y += step
                }
                stroke.color = a(seasonal, al(70))
                canvas.drawLine(22 * u, 0f, 22 * u, h.toFloat(), stroke)
            }
            "dots" -> {
                val step = 16 * u
                fill.color = a(ink, al(120))
                var y = step / 2
                while (y <= h) {
                    var x = step / 2
                    while (x <= w) { canvas.drawCircle(x, y, 1.4f * u, fill); x += step }
                    y += step
                }
            }
            "asanoha" -> {
                val s = 30 * u
                val th = s * sqrt(3f) / 2f
                stroke.color = a(ink, al(95))
                stroke.strokeWidth = max(1f, 0.9f * u)
                var r = -1
                while (r * th <= h + th) {
                    val y0 = r * th
                    val y1 = y0 + th
                    val xo = if (((r % 2) + 2) % 2 == 1) s / 2f else 0f
                    var i = -2
                    while (i * s <= w + s) {
                        val bx = xo + i * s
                        tri(canvas, stroke, bx, y1, bx + s, y1, bx + s / 2, y0)
                        tri(canvas, stroke, bx + s / 2, y0, bx + 1.5f * s, y0, bx + s, y1)
                        i++
                    }
                    r++
                }
            }
            "seigaiha" -> {
                val rad = 22 * u
                stroke.color = a(ink, al(120))
                stroke.strokeWidth = max(1f, 1.1f * u)
                val cover = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = base; style = Paint.Style.FILL }
                var k = 0
                var y = -rad
                while (y <= h + rad) {
                    val off = if (k % 2 == 1) rad else 0f
                    var x = -2 * rad + off
                    while (x <= w + 2 * rad) {
                        canvas.drawCircle(x, y, rad, cover)
                        for (f in floatArrayOf(1f, 0.75f, 0.5f, 0.25f)) canvas.drawCircle(x, y, rad * f, stroke)
                        x += 2 * rad
                    }
                    y += rad / 2; k++
                }
            }
            "ichimatsu" -> {
                val size = 22 * u
                fill.color = a(ink, al(42))
                var j = 0
                var y = 0f
                while (y < h) {
                    var i = 0
                    var x = 0f
                    while (x < w) {
                        if ((i + j) % 2 == 0) canvas.drawRect(x, y, x + size, y + size, fill)
                        x += size; i++
                    }
                    y += size; j++
                }
            }
            "shippo" -> {
                val r = 18 * u
                stroke.color = a(ink, al(105))
                var y = -2 * r
                while (y <= h + 2 * r) {
                    var x = -2 * r
                    while (x <= w + 2 * r) {
                        canvas.drawCircle(x, y, r, stroke)
                        canvas.drawCircle(x + r, y + r, r, stroke)
                        x += 2 * r
                    }
                    y += 2 * r
                }
            }
            "watercolor" -> {
                val rnd = Random(7)
                val big = max(w, h).toFloat()
                repeat(7) {
                    val cx = rnd.nextFloat() * w
                    val cy = rnd.nextFloat() * h
                    val rr = big * (0.25f + rnd.nextFloat() * 0.3f)
                    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
                    p.shader = RadialGradient(cx, cy, rr, intArrayOf(a(ink, al(70)), a(ink, 0)), null, Shader.TileMode.CLAMP)
                    canvas.drawCircle(cx, cy, rr, p)
                }
            }
            "gradient" -> {
                val p = Paint(Paint.DITHER_FLAG)
                p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), base, ColorUtils.blendARGB(base, ink, 0.35f), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            }
            "paper" -> {
                val rnd = Random(11)
                val n = (w * h / (9 * u * u)).toInt().coerceAtMost(9000)
                repeat(n) {
                    fill.color = a(ink, al(18 + rnd.nextInt(26)))
                    canvas.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, (0.3f + rnd.nextFloat() * 0.6f) * u, fill)
                }
                stroke.strokeWidth = max(1f, 0.6f * u)
                repeat((n / 12).coerceAtLeast(20)) {
                    stroke.color = a(ink, al(28))
                    val x = rnd.nextFloat() * w
                    val y = rnd.nextFloat() * h
                    val len = (4 + rnd.nextFloat() * 8) * u
                    val ang = rnd.nextFloat() * 6.283f
                    canvas.drawLine(x, y, x + cos(ang) * len, y + sin(ang) * len, stroke)
                }
            }
            "sakura" -> scatter(canvas, w, h, u, 3, 70f) { c, x, y, size, rot, rnd ->
                if (rnd.nextFloat() < 0.65f) flower(c, x, y, size, rot, seasonal) else petal(c, x, y, size * 0.6f, rot, seasonal)
            }
            "momiji" -> scatter(canvas, w, h, u, 5, 72f) { c, x, y, size, rot, _ ->
                maple(c, x, y, size * 1.15f, rot, seasonal)
            }
            "snow" -> scatter(canvas, w, h, u, 9, 60f) { c, x, y, size, rot, _ ->
                snowflake(c, x, y, size, rot, seasonal, u)
            }
        }
    }

    private fun tri(c: Canvas, p: Paint, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        val cx = (x1 + x2 + x3) / 3f
        val cy = (y1 + y2 + y3) / 3f
        c.drawLine(x1, y1, x2, y2, p); c.drawLine(x2, y2, x3, y3, p); c.drawLine(x3, y3, x1, y1, p)
        c.drawLine(cx, cy, x1, y1, p); c.drawLine(cx, cy, x2, y2, p); c.drawLine(cx, cy, x3, y3, p)
    }

    private fun scatter(
        canvas: Canvas, w: Int, h: Int, u: Float, seed: Int, spacingDp: Float,
        draw: (Canvas, Float, Float, Float, Float, Random) -> Unit,
    ) {
        val rnd = Random(seed)
        val cell = spacingDp * u
        var y = 0f
        var row = 0
        while (y < h + cell) {
            var x = if (row % 2 == 1) -cell / 2 else 0f
            while (x < w + cell) {
                val px = x + (rnd.nextFloat() - 0.5f) * cell * 0.7f
                val py = y + (rnd.nextFloat() - 0.5f) * cell * 0.7f
                val size = (7 + rnd.nextFloat() * 6) * u
                draw(canvas, px, py, size, rnd.nextFloat() * 360f, rnd)
                x += cell
            }
            y += cell * 0.85f; row++
        }
    }

    private fun petalPath(r: Float) = Path().apply {
        moveTo(0f, 0f)
        cubicTo(-0.6f * r, -0.35f * r, -0.5f * r, -1.0f * r, -0.14f * r, -r)
        lineTo(0f, -0.84f * r)
        lineTo(0.14f * r, -r)
        cubicTo(0.5f * r, -1.0f * r, 0.6f * r, -0.35f * r, 0f, 0f)
        close()
    }

    private fun flower(c: Canvas, x: Float, y: Float, r: Float, rot: Float, ink: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = a(ink, 165) }
        val path = petalPath(r)
        c.save(); c.translate(x, y); c.rotate(rot)
        repeat(5) { c.drawPath(path, p); c.rotate(72f) }
        p.color = a(ColorUtils.blendARGB(ink, Color.BLACK, 0.25f), 200)
        c.drawCircle(0f, 0f, r * 0.14f, p)
        c.restore()
    }

    private fun petal(c: Canvas, x: Float, y: Float, r: Float, rot: Float, ink: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = a(ink, 120) }
        c.save(); c.translate(x, y); c.rotate(rot)
        c.drawPath(petalPath(r), p)
        c.restore()
    }

    private fun maple(c: Canvas, x: Float, y: Float, r: Float, rot: Float, ink: Int) {
        // Lobe angles (degrees, 0 = right, -90 = up) and their lengths.
        val lobes = listOf(-150f to 0.75f, -115f to 0.9f, -90f to 1.0f, -65f to 0.9f, -30f to 0.75f, 20f to 0.5f, 160f to 0.5f)
        val path = Path()
        var first = true
        lobes.forEachIndexed { i, (ang, len) ->
            val next = lobes[(i + 1) % lobes.size]
            var gap = next.first - ang
            if (gap <= 0) gap += 360f
            val tipX = cos(Math.toRadians(ang.toDouble())).toFloat() * r * len
            val tipY = sin(Math.toRadians(ang.toDouble())).toFloat() * r * len
            if (first) { path.moveTo(tipX, tipY); first = false } else path.lineTo(tipX, tipY)
            val mid = ang + gap / 2f
            val inner = if (gap > 100f) 0.2f else 0.42f
            path.lineTo(
                cos(Math.toRadians(mid.toDouble())).toFloat() * r * inner,
                sin(Math.toRadians(mid.toDouble())).toFloat() * r * inner,
            )
        }
        path.close()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = a(ink, 160) }
        c.save(); c.translate(x, y); c.rotate(rot)
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.08f
        c.drawLine(0f, 0f, 0f, r * 0.75f, p)
        c.restore()
    }

    private fun snowflake(c: Canvas, x: Float, y: Float, r: Float, rot: Float, ink: Int, u: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = a(ink, 175); style = Paint.Style.STROKE; strokeWidth = max(1f, 1.1f * u); strokeCap = Paint.Cap.ROUND
        }
        c.save(); c.translate(x, y); c.rotate(rot)
        repeat(6) {
            c.drawLine(0f, 0f, 0f, -r, p)
            for (t in floatArrayOf(0.5f, 0.75f)) {
                val b = r * 0.28f * (1.2f - t)
                c.drawLine(0f, -r * t, -b, -r * t - b, p)
                c.drawLine(0f, -r * t, b, -r * t - b, p)
            }
            c.rotate(60f)
        }
        c.restore()
    }
}
