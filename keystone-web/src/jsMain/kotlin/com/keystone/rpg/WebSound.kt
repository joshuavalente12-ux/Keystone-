package com.keystone.rpg

import android.content.Context

/** What your feet are landing on, for the footstep sound (same values as the Android build). */
object Surface {
    const val GRASS = 0
    const val DIRT = 1
    const val SAND = 2
    const val SNOW = 3
    const val STONE = 4
    const val WOOD = 5
    const val WATER = 6
    const val ROCK = 7
    const val COUNT = 8
}

/**
 * The web build's sound. Same interface as the Android Sound object, so the game code is
 * unchanged; it is silent for now and will be wired to the browser's Web Audio later.
 */
object Sound {
    @Volatile var wind = 0f
    @Volatile var rain = 0f
    @Volatile var indoors = false

    @Volatile var volSteps = 1f
    @Volatile var volWind = 1f
    @Volatile var volRain = 1f
    @Volatile var volAnimals = 1f
    @Volatile var volMusic = 0.6f

    @Volatile var wanted = false

    fun load(ctx: Context) {}
    fun save(ctx: Context) {}
    fun step(surface: Int, loud: Float) {}
    fun call(id: Int, loud: Float) {}
    fun thunder(delay: Float, loud: Float) {}
    fun setMusicVolume(v: Float) {
        volMusic = v
    }

    fun playVoice(id: String, onEnd: () -> Unit) {
        onEnd()
    }

    fun stopVoice() {}
    fun start() {}
    fun stop() {}
}
