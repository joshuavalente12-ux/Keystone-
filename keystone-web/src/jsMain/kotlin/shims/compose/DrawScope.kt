package androidx.compose.ui.graphics.drawscope

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

sealed class DrawStyle
object Fill : DrawStyle()
class Stroke(val width: Float = 0f) : DrawStyle()

/** Compose's drawing API, implemented by the web host on a 2D canvas. */
interface DrawScope {
    val size: Size

    fun drawRect(color: Color, topLeft: Offset = Offset.Zero, size: Size = this.size, alpha: Float = 1f, style: DrawStyle = Fill)
    fun drawRoundRect(color: Color, topLeft: Offset = Offset.Zero, size: Size = this.size, cornerRadius: CornerRadius = CornerRadius.Zero, alpha: Float = 1f, style: DrawStyle = Fill)
    fun drawOval(color: Color, topLeft: Offset = Offset.Zero, size: Size = this.size, alpha: Float = 1f, style: DrawStyle = Fill)
    fun drawCircle(color: Color, radius: Float = size.minDimension / 2f, center: Offset = size.center, alpha: Float = 1f, style: DrawStyle = Fill)
    fun drawArc(color: Color, startAngle: Float, sweepAngle: Float, useCenter: Boolean, topLeft: Offset = Offset.Zero, size: Size = this.size, alpha: Float = 1f, style: DrawStyle = Fill)
    fun drawPath(path: Path, color: Color, alpha: Float = 1f, style: DrawStyle = Fill)
    fun drawLine(color: Color, start: Offset, end: Offset, strokeWidth: Float = 0f, alpha: Float = 1f)
}
