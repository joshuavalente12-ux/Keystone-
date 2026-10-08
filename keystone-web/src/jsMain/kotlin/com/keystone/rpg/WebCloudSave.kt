package com.keystone.rpg

import android.content.Context

/**
 * Google Play cloud saves don't exist on the web. The web build saves to the browser
 * (and later to the player's Keystone Arcade account), so this only keeps the flags the
 * game sets.
 */
object CloudSave {
    @Volatile var locked = false
    @Volatile var restoredTick = 0

    fun markDeathPending(ctx: Context) {
        ctx.getSharedPreferences("keystone_cloud_flags", Context.MODE_PRIVATE).edit().putBoolean("deathPending", true).commit()
    }
}
