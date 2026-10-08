package androidx.activity

import android.app.Activity
import android.os.Bundle

/** Compile-only: on the web, WebMain.kt starts the game instead of an Activity. */
open class ComponentActivity : Activity() {
    open fun onCreate(savedInstanceState: Bundle?) {}
    open fun onStart() {}
    open fun onResume() {}
    open fun onPause() {}
    open fun onStop() {}
    open fun onDestroy() {}
}

class SystemBarStyle private constructor() {
    companion object {
        fun dark(scrim: Int) = SystemBarStyle()
        fun light(scrim: Int, darkScrim: Int) = SystemBarStyle()
        fun auto(lightScrim: Int, darkScrim: Int) = SystemBarStyle()
    }
}

fun ComponentActivity.enableEdgeToEdge(statusBarStyle: SystemBarStyle? = null, navigationBarStyle: SystemBarStyle? = null) {}
