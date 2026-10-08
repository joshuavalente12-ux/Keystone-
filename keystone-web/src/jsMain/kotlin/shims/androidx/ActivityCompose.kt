package androidx.activity.compose

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable

/** Compile-only: WebMain.kt hosts the Compose content on the web. */
fun ComponentActivity.setContent(content: @Composable () -> Unit) {}
