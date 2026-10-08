package androidx.compose.ui.geometry

import kotlin.math.sqrt

data class Offset(val x: Float, val y: Float) {
    operator fun plus(o: Offset) = Offset(x + o.x, y + o.y)
    operator fun minus(o: Offset) = Offset(x - o.x, y - o.y)
    operator fun times(k: Float) = Offset(x * k, y * k)
    operator fun div(k: Float) = Offset(x / k, y / k)
    operator fun unaryMinus() = Offset(-x, -y)
    fun getDistance() = sqrt(x * x + y * y)
    fun getDistanceSquared() = x * x + y * y

    companion object {
        val Zero = Offset(0f, 0f)
    }
}

data class Size(val width: Float, val height: Float) {
    val minDimension: Float get() = minOf(width, height)
    val maxDimension: Float get() = maxOf(width, height)
    val center: Offset get() = Offset(width / 2f, height / 2f)
    operator fun times(k: Float) = Size(width * k, height * k)
    operator fun div(k: Float) = Size(width / k, height / k)

    companion object {
        val Zero = Size(0f, 0f)
    }
}

data class CornerRadius(val x: Float, val y: Float = x) {
    companion object {
        val Zero = CornerRadius(0f, 0f)
    }
}
