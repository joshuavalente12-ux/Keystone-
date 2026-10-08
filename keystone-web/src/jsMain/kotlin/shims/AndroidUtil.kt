package android.util

/** android.util.Log, printed to the browser console. */
object Log {
    fun d(tag: String, msg: String): Int = 0.also { console.log("$tag: $msg") }
    fun i(tag: String, msg: String): Int = 0.also { console.info("$tag: $msg") }
    fun w(tag: String, msg: String): Int = 0.also { console.warn("$tag: $msg") }
    fun e(tag: String, msg: String): Int = 0.also { console.error("$tag: $msg") }
    fun e(tag: String, msg: String, t: Throwable): Int = 0.also { console.error("$tag: $msg", t) }
}
