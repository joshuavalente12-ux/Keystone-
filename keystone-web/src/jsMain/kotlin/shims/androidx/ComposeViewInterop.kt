package androidx.compose.ui.viewinterop

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Views that live outside Compose (the 3D view) attach while their AndroidView is on screen. */
interface WebHostedView {
    fun attach()
    fun detach()
}

/**
 * AndroidView for the web: the 3D view is a separate canvas behind the Compose layer, so
 * this clears its area of the Compose canvas (whatever was drawn underneath, like a dark
 * background) to let the 3D world show through, just as the real view would cover it.
 */
@Composable
fun <T : View> AndroidView(factory: (Context) -> T, modifier: Modifier = Modifier, update: (T) -> Unit = {}) {
    val ctx = LocalContext.current
    val view = remember { factory(ctx) }
    DisposableEffect(view) {
        (view as? WebHostedView)?.attach()
        onDispose { (view as? WebHostedView)?.detach() }
    }
    update(view)
    Box(modifier.drawBehind { drawRect(Color.Transparent, blendMode = BlendMode.Clear) })
}
