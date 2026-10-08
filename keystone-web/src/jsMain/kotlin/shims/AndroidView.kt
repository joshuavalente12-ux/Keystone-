package android.view

import android.content.Context

/** Base of Android views; only what the game touches. */
open class View(val context: Context) {
    var keepScreenOn = false
        set(v) {
            field = v
            KeepAwake.set(v)
        }
}

/** Keeps the screen on while playing, where the browser allows it (Screen Wake Lock API). */
object KeepAwake {
    private var lock: dynamic = null

    fun set(on: Boolean) {
        val nav = kotlinx.browser.window.navigator.asDynamic()
        if (on && lock == null && nav.wakeLock != undefined) {
            nav.wakeLock.request("screen").then({ l: dynamic -> lock = l }, { _: dynamic -> })
        } else if (!on && lock != null) {
            lock.release()
            lock = null
        }
    }
}
