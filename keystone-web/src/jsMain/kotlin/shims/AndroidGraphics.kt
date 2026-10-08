package android.graphics

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/**
 * An image in memory (32-bit ARGB pixels) backed by a Skia bitmap, so Compose can draw it
 * with asImageBitmap() and see later setPixel changes, like on Android.
 */
class Bitmap private constructor(val width: Int, val height: Int) {
    val pixels = IntArray(width * height)
    private val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    val skia = org.jetbrains.skia.Bitmap().apply {
        check(allocPixels(info)) { "Couldn't allocate a ${width}x$height bitmap" }
        erase(0)
    }
    /** Writes into the bitmap's own pixel memory (installPixels would hand Skia temporary memory). */
    private val skiaCanvas = org.jetbrains.skia.Canvas(skia)
    private val copyPaint = org.jetbrains.skia.Paint().apply { blendMode = org.jetbrains.skia.BlendMode.SRC }
    private var dirty = true

    enum class Config { ARGB_8888, RGB_565, ALPHA_8 }

    private fun changed() {
        if (!dirty) {
            dirty = true
            pendingFlush += this
        }
    }

    fun setPixel(x: Int, y: Int, color: Int) {
        pixels[y * width + x] = color
        changed()
    }

    fun getPixel(x: Int, y: Int): Int = pixels[y * width + x]

    fun setPixels(src: IntArray, offset: Int, stride: Int, x: Int, y: Int, w: Int, h: Int) {
        for (row in 0 until h) src.copyInto(pixels, (y + row) * width + x, offset + row * stride, offset + row * stride + w)
        changed()
    }

    fun getPixels(dst: IntArray, offset: Int, stride: Int, x: Int, y: Int, w: Int, h: Int) {
        for (row in 0 until h) pixels.copyInto(dst, offset + row * stride, (y + row) * width + x, (y + row) * width + x + w)
    }

    fun eraseColor(color: Int) {
        pixels.fill(color)
        changed()
    }

    /** Copies the ARGB pixels into the Skia bitmap (RGBA bytes). */
    fun flush() {
        if (!dirty) return
        dirty = false
        val bytes = ByteArray(pixels.size * 4)
        for (i in pixels.indices) {
            val c = pixels[i]
            bytes[i * 4] = (c shr 16).toByte()
            bytes[i * 4 + 1] = (c shr 8).toByte()
            bytes[i * 4 + 2] = c.toByte()
            bytes[i * 4 + 3] = (c ushr 24).toByte()
        }
        val img = org.jetbrains.skia.Image.makeRaster(info, bytes, width * 4)
        skiaCanvas.drawImage(img, 0f, 0f, copyPaint)
        img.close()
        skia.notifyPixelsChanged()
    }

    companion object {
        private val pendingFlush = ArrayList<Bitmap>()

        fun createBitmap(width: Int, height: Int, config: Config): Bitmap = Bitmap(width, height).also { pendingFlush += it }

        /** Called once per frame by the web host, before Compose draws. */
        fun flushAll() {
            if (pendingFlush.isEmpty()) return
            val list = ArrayList(pendingFlush)
            pendingFlush.clear()
            for (b in list) b.flush()
        }
    }
}

object Color {
    const val BLACK = -0x1000000
    const val WHITE = -0x1
    const val TRANSPARENT = 0
    const val RED = -0x10000
    const val GREEN = -0xff0100
    const val BLUE = -0xffff01
    const val YELLOW = -0x100
    const val GRAY = -0x777778

    fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    fun rgb(r: Int, g: Int, b: Int) = argb(255, r, g, b)
    fun alpha(c: Int) = c ushr 24
    fun red(c: Int) = (c shr 16) and 0xFF
    fun green(c: Int) = (c shr 8) and 0xFF
    fun blue(c: Int) = c and 0xFF
}

/** The text-drawing parts of android.graphics.Paint (see drawText in WebCanvasText.kt). */
class Paint {
    enum class Align { LEFT, CENTER, RIGHT }

    var isAntiAlias = false
    var color = Color.BLACK
    var textAlign = Align.LEFT
    var textSize = 12f
    var isFakeBoldText = false
    var shadowRadius = 0f
    var shadowDx = 0f
    var shadowDy = 0f
    var shadowColor = 0

    var alpha: Int
        get() = color ushr 24
        set(v) {
            color = (color and 0x00FFFFFF) or (v.coerceIn(0, 255) shl 24)
        }

    fun setShadowLayer(radius: Float, dx: Float, dy: Float, shadowColor: Int) {
        shadowRadius = radius
        shadowDx = dx
        shadowDy = dy
        this.shadowColor = shadowColor
    }
}
