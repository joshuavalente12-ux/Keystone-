package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter

/** Images decoded at startup (WebMain loads every drawable before showing the game). */
object WebImages {
    val byId = HashMap<Int, ImageBitmap>()
}

@Composable
fun painterResource(id: Int): Painter =
    WebImages.byId[id]?.let { BitmapPainter(it) } ?: ColorPainter(androidx.compose.ui.graphics.Color.Transparent)

@Composable
fun imageResource(id: Int): ImageBitmap = WebImages.byId.getValue(id)
