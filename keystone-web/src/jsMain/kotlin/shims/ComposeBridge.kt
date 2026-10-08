package androidx.compose.ui.graphics

/** Android's Bitmap.asImageBitmap(): shares the Skia pixels, so later changes show up. */
fun android.graphics.Bitmap.asImageBitmap(): ImageBitmap = skia.asComposeImageBitmap()
