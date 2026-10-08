package androidx.compose.ui.graphics

import android.graphics.Bitmap

/** Compose's Color: four floats, 0..1. */
data class Color(val red: Float, val green: Float, val blue: Float, val alpha: Float = 1f) {
    constructor(argb: Long) : this(
        ((argb shr 16) and 0xFF).toInt() / 255f,
        ((argb shr 8) and 0xFF).toInt() / 255f,
        (argb and 0xFF).toInt() / 255f,
        ((argb shr 24) and 0xFF).toInt() / 255f,
    )

    constructor(argb: Int) : this(argb.toLong() and 0xFFFFFFFFL)

    constructor(red: Int, green: Int, blue: Int, alpha: Int = 255) :
        this(red / 255f, green / 255f, blue / 255f, alpha / 255f)

    /** CSS colour string for drawing on a web canvas. */
    fun css(): String = "rgba(${(red * 255).toInt()},${(green * 255).toInt()},${(blue * 255).toInt()},$alpha)"

    companion object {
        val Black = Color(0f, 0f, 0f)
        val White = Color(1f, 1f, 1f)
        val Red = Color(1f, 0f, 0f)
        val Green = Color(0f, 1f, 0f)
        val Blue = Color(0f, 0f, 1f)
        val Yellow = Color(1f, 1f, 0f)
        val Gray = Color(0.53f, 0.53f, 0.53f)
        val DarkGray = Color(0.27f, 0.27f, 0.27f)
        val LightGray = Color(0.8f, 0.8f, 0.8f)
        val Transparent = Color(0f, 0f, 0f, 0f)
        val Unspecified = Color(0f, 0f, 0f, 0f)
    }
}

fun Color.toArgb(): Int =
    ((alpha * 255f + 0.5f).toInt() shl 24) or ((red * 255f + 0.5f).toInt() shl 16) or
        ((green * 255f + 0.5f).toInt() shl 8) or (blue * 255f + 0.5f).toInt()

class ImageBitmap(val bitmap: Bitmap) {
    val width get() = bitmap.width
    val height get() = bitmap.height
}

fun Bitmap.asImageBitmap() = ImageBitmap(this)

class Path {
    /** Recorded as commands, so a web canvas can replay them. */
    val ops = ArrayList<FloatArray>()

    fun moveTo(x: Float, y: Float) {
        ops.add(floatArrayOf(0f, x, y))
    }

    fun lineTo(x: Float, y: Float) {
        ops.add(floatArrayOf(1f, x, y))
    }

    fun quadraticTo(x1: Float, y1: Float, x2: Float, y2: Float) {
        ops.add(floatArrayOf(2f, x1, y1, x2, y2))
    }

    fun close() {
        ops.add(floatArrayOf(3f))
    }

    fun reset() = ops.clear()
}
