package android.app

import android.content.Context
import android.content.ContextWrapper

/** Only needed so Android lifecycle code compiles; the web page is the "activity". */
open class Activity : ContextWrapper(Context()) {
    /** Restarting the activity is reloading the page. */
    fun recreate() = kotlinx.browser.window.location.reload()
    fun finish() {}
}
