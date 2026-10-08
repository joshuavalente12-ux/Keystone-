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
    val font = WebFonts.font(p.textSize, p.isFakeBoldText)
    val w = font.measureTextWidth(text)
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
        drawString(text, left + p.shadowDx, y + p.shadowDy, font, shadow)
    }
    drawString(text, left, y, font, Paint().apply {
        color = p.color
        isAntiAlias = p.isAntiAlias
    })
}

/** Fonts for drawText; the typeface is set by WebMain once the page font is loaded. */
object WebFonts {
    var regular: Typeface? = null
    var bold: Typeface? = null
    private val cache = HashMap<String, Font>()

    fun font(size: Float, bold: Boolean): Font = cache.getOrPut("$size/$bold") {
        Font(if (bold) this.bold ?: regular else regular, size).apply {
            isEmboldened = bold && this@WebFonts.bold == null
        }
    }
}
