package android.graphics

/** An image in memory: 32-bit ARGB pixels, row by row. */
class Bitmap private constructor(val width: Int, val height: Int) {
    val pixels = IntArray(width * height)
    /** Bumped on every change, so whoever draws it knows to refresh. */
    var version = 0
        private set

    enum class Config { ARGB_8888, RGB_565, ALPHA_8 }

    fun setPixel(x: Int, y: Int, color: Int) {
        pixels[y * width + x] = color
        version++
    }

    fun getPixel(x: Int, y: Int): Int = pixels[y * width + x]

    fun setPixels(src: IntArray, offset: Int, stride: Int, x: Int, y: Int, w: Int, h: Int) {
        for (row in 0 until h) src.copyInto(pixels, (y + row) * width + x, offset + row * stride, offset + row * stride + w)
        version++
    }

    fun getPixels(dst: IntArray, offset: Int, stride: Int, x: Int, y: Int, w: Int, h: Int) {
        for (row in 0 until h) pixels.copyInto(dst, offset + row * stride, (y + row) * width + x, (y + row) * width + x + w)
    }

    fun eraseColor(color: Int) {
        pixels.fill(color)
        version++
    }

    companion object {
        fun createBitmap(width: Int, height: Int, config: Config): Bitmap = Bitmap(width, height)
    }
}
