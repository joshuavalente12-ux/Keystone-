package com.keystone.rpg

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.browser.window

/** The web page is the game's one Activity. */
private val webActivity = Activity()

fun Context.keystoneActivity(): Activity? = webActivity

/**
 * The web build's CloudSave: same interface and rules as the Android one (Google Play Games
 * snapshots), with the "cloud" being a backup slot next to the save. On Keystone Arcade the
 * whole store, slot included, is kept in the player's account, so Save and Load work across
 * devices; anywhere else it stays in the browser.
 */
object CloudSave {
    const val NOT_SET_UP = 0
    const val CHECKING = 1
    const val SIGNED_IN = 2
    const val GUEST = 3

    private const val PREFS = "keystone_save"
    private const val SLOT = "keystone_cloud_v1"
    private const val FLAGS = "keystone_cloud_flags"

    var state by mutableIntStateOf(SIGNED_IN)
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

    private val ctx = Context()

    /** Set by the Keystone Arcade player: who is playing, or no one when logged out. */
    private val arcade: dynamic get() = window.asDynamic().keystoneArcade

    fun start(activity: Activity) {
        val a = arcade
        playerName = if (a != null && a.player != null) a.player.toString() else ""
    }

    fun signIn(activity: Activity) {}

    private fun prefs() = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun slot() = ctx.getSharedPreferences(SLOT, Context.MODE_PRIVATE)
    private fun flags() = ctx.getSharedPreferences(FLAGS, Context.MODE_PRIVATE)

    private fun localTime() = prefs().getLong("savedAt", 0L)

    /** Everything in the save, as text. Null if there is no adventure yet to save. */
    private fun exportLocal(): String? {
        val p = prefs()
        if (p.getString("weapon", "").isNullOrEmpty()) return null
        val data = js("({})")
        for ((k, v) in p.all) if (v != null) data[k] = v.toString()
        val root = js("({})")
        root.v = 1
        root.savedAt = p.getLong("savedAt", 0L).toString()
        root.data = data
        return JSON.stringify(root)
    }

    private fun importLocal(text: String): Boolean = try {
        val data = JSON.parse<dynamic>(text).data
        val e = prefs().edit().clear()
        for (k in js("Object").keys(data).unsafeCast<Array<String>>()) e.putString(k, data[k].toString())
        e.commit()
    } catch (t: Throwable) {
        false
    }

    private fun readSlot(): String? {
        val text = slot().getString("save", null) ?: return null
        return if (isGrave(text)) null else text
    }

    private fun writeSlot(text: String) {
        slot().edit().putString("save", text).commit()
    }

    private fun isGrave(text: String) = try {
        JSON.parse<dynamic>(text).dead == true
    } catch (t: Throwable) {
        false
    }

    private fun slotTime(text: String): Long = try {
        JSON.parse<dynamic>(text).savedAt.toString().toLong()
    } catch (t: Throwable) {
        0L
    }

    private fun grave() = "{\"v\":1,\"dead\":true,\"savedAt\":\"${System.currentTimeMillis()}\"}"

    private fun deathPending() = flags().getBoolean("deathPending", false)

    fun markDeathPending(ctx: Context) {
        flags().edit().putBoolean("deathPending", true).commit()
    }

    /** A hardcore hero died: the backup must not bring them back. */
    fun buryCloud(activity: Activity) {
        if (!deathPending()) return
        writeSlot(grave())
        flags().edit().putBoolean("deathPending", false).commit()
    }

    /** Backs up the save. The quiet version never overwrites a newer backup; [force] does. */
    fun upload(activity: Activity, force: Boolean = false) {
        val json = exportLocal()
        if (json == null) {
            if (force) note = "Nothing to save yet. Start a game first."
            return
        }
        val backup = readSlot()
        if (!force && backup != null && slotTime(backup) > localTime()) return
        writeSlot(json)
        if (force) note = if (playerName.isEmpty()) "Saved." else "Saved to your account."
    }

    /** Replaces the adventure with the backup. */
    fun download(activity: Activity) {
        val backup = readSlot()
        note = when {
            backup == null -> "No saved backup found yet."
            importLocal(backup) -> {
                restoredTick += 1
                "Your saved adventure was loaded."
            }
            else -> "That save couldn't be read."
        }
    }

    fun statusText(): String = when {
        playerName.isNotEmpty() -> "Signed in as $playerName. Progress saves to your account."
        arcade != null -> "Guest. Progress is saved in this browser only. Log in to keep it."
        else -> "Progress is saved in this browser."
    }
}
