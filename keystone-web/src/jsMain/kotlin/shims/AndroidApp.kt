package android.app

import android.content.Context
import android.content.ContextWrapper
import kotlinx.browser.window

/** Only needed so Android lifecycle code compiles; the web page is the "activity". */
open class Activity : ContextWrapper(Context()) {
    /**
     * Restarting the activity is starting the page over. In the Keystone Arcade player the
     * player does that (a plain reload would lose the game's files); elsewhere it's a reload.
     */
    fun recreate() {
        val arcade = window.asDynamic().keystoneArcade
        if (arcade != null && arcade.restart != null) arcade.restart() else window.location.reload()
    }

    fun finish() {}
}
