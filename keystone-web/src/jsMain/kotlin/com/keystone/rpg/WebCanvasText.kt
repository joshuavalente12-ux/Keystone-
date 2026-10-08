package com.keystone.rpg

import org.jetbrains.skia.Canvas
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.Font
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Typeface

/**
 * Android's Canvas.drawText(text, x, y, Paint) on the Skia canvas Compose gives the map
 * and skill grid (nativeCanvas), with Paint's alignment, size, bold and shadow.
 */
fun Canvas.drawText(text: String, x: Float, y: Float, p: android.graphics.Paint) {
    val runs = WebFonts.runs(text, p.textSize, p.isFakeBoldText)
    val w = runs.sumOf { it.second.measureTextWidth(it.first).toDouble() }.toFloat()
    val left = when (p.textAlign) {
        android.graphics.Paint.Align.CENTER -> x - w / 2f
        android.graphics.Paint.Align.RIGHT -> x - w
        android.graphics.Paint.Align.LEFT -> x
    }
    if (p.shadowRadius > 0f && (p.shadowColor ushr 24) != 0) {
        val shadow = Paint().apply {
            color = p.shadowColor
            isAntiAlias = true
            maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, p.shadowRadius / 2f)
        }
        drawRuns(runs, left + p.shadowDx, y + p.shadowDy, shadow)
    }
    drawRuns(runs, left, y, Paint().apply {
        color = p.color
        isAntiAlias = p.isAntiAlias
    })
}

private fun Canvas.drawRuns(runs: List<Pair<String, Font>>, x: Float, y: Float, paint: Paint) {
    var cx = x
    for ((s, f) in runs) {
        drawString(s, cx, y, f, paint)
        cx += f.measureTextWidth(s)
    }
}

/** Fonts for drawText; the typeface is set by WebMain once the page font is loaded. */
object WebFonts {
    var regular: Typeface? = null
    var bold: Typeface? = null
    /** Emoji and symbol typefaces for characters the main font doesn't have. */
    var fallbacks: List<Typeface> = emptyList()
    private val cache = HashMap<String, Font>()

    /** Splits [text] into pieces that each use one font: the main one, or an emoji/symbol fallback. */
    fun runs(text: String, size: Float, bold: Boolean): List<Pair<String, Font>> {
        val main = font(size, bold)
        val out = ArrayList<Pair<String, Font>>()
        val sb = StringBuilder()
        var current: Font? = null
        var i = 0
        while (i < text.length) {
            val cp = codePointAt(text, i)
            val len = if (cp > 0xFFFF) 2 else 1
            val f = pick(cp, main, size)
            if (current != null && f !== current) {
                out += sb.toString() to current
                sb.clear()
            }
            current = f
            sb.append(text, i, i + len)
            i += len
        }
        if (current != null && sb.isNotEmpty()) out += sb.toString() to current
        return out
    }

    private fun pick(cp: Int, main: Font, size: Float): Font {
        if (cp == 0xFE0F || cp == 0x200D || cp < 0x2000) return main
        if (main.typeface?.getUTF32Glyph(cp)?.toInt() ?: 0 != 0) return main
        for (t in fallbacks) {
            if (t.getUTF32Glyph(cp).toInt() != 0) return cache.getOrPut("fb${t.hashCode()}/$size") { Font(t, size) }
        }
        return main
    }

    private fun codePointAt(s: String, i: Int): Int {
        val c = s[i]
        if (c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) {
            return ((c.code - 0xD800) shl 10) + (s[i + 1].code - 0xDC00) + 0x10000
        }
        return c.code
    }

    fun font(size: Float, bold: Boolean): Font = cache.getOrPut("$size/$bold") {
        Font(if (bold) this.bold ?: regular else regular, size).apply {
            isEmboldened = bold && this@WebFonts.bold == null
        }
    }
}
