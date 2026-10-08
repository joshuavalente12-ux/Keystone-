package androidx.compose.ui.platform

import android.content.Context
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf

/** Android's LocalContext / LocalView for the web: one shared context for the whole page. */
val LocalContext = staticCompositionLocalOf<Context> { error("LocalContext not provided") }
val LocalView = staticCompositionLocalOf<View> { error("LocalView not provided") }

/** Screen size in dp (CSS pixels), provided by WebMain and updated when the window resizes. */
val LocalConfiguration = staticCompositionLocalOf { android.content.res.Configuration() }
