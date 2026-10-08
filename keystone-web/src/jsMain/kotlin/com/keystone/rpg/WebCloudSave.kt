package com.keystone.rpg

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** On the web there is no Activity behind the page's Context. */
fun Context.keystoneActivity(): Activity? = null

/**
 * The web build's CloudSave: same interface as the Android one (Google Play Games), which
 * doesn't exist in a browser. Saves stay in the browser; on Keystone Arcade they will go to
 * the player's account instead.
 */
object CloudSave {
    const val NOT_SET_UP = 0
    const val CHECKING = 1
    const val SIGNED_IN = 2
    const val GUEST = 3

    var state by mutableIntStateOf(NOT_SET_UP)
        private set
    var playerName by mutableStateOf("")
        private set
    var working by mutableStateOf(false)
        private set
    var note by mutableStateOf("")
        private set
    var restoredTick by mutableIntStateOf(0)
        private set

    @Volatile var locked = false

    fun start(activity: Activity) {}
    fun signIn(activity: Activity) {}
    fun upload(activity: Activity, force: Boolean = false) {}
    fun download(activity: Activity) {}
    fun buryCloud(activity: Activity) {}

    fun markDeathPending(ctx: Context) {
        ctx.getSharedPreferences("keystone_cloud_flags", Context.MODE_PRIVATE).edit().putBoolean("deathPending", true).commit()
    }

    fun statusText(): String = "Saved in this browser"
}
