package com.keystone.rpg

import java.util.concurrent.Runnable
import kotlinx.browser.window
import kotlin.js.Date
import kotlin.math.PI

// JVM pieces the game code uses without importing them (java.lang.*), for the browser.

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
annotation class Volatile

/** Browsers run the game on one thread, so a lock is never contended. */
inline fun <R> synchronized(lock: Any, block: () -> R): R = block()

object System {
    fun currentTimeMillis(): Long = Date.now().toLong()
    fun nanoTime(): Long = (window.performance.now() * 1_000_000.0).toLong()
}

object Math {
    fun toRadians(deg: Double): Double = deg * (PI / 180.0)
    fun toDegrees(rad: Double): Double = rad * (180.0 / PI)
    fun abs(v: Float) = kotlin.math.abs(v)
    fun abs(v: Double) = kotlin.math.abs(v)
    fun abs(v: Int) = kotlin.math.abs(v)
    fun sin(v: Double) = kotlin.math.sin(v)
    fun cos(v: Double) = kotlin.math.cos(v)
    fun sqrt(v: Double) = kotlin.math.sqrt(v)
    fun floorMod(a: Int, b: Int) = a.mod(b)
}

object Integer {
    fun bitCount(i: Int): Int = i.countOneBits()
}

/** Threads don't exist in the browser; their work runs through java.util.concurrent.WebTasks. */
class Thread(val target: Runnable? = null, val name: String = "") {
    var isDaemon = false
    var priority = NORM_PRIORITY

    fun start() {
        val t = target ?: return
        java.util.concurrent.WebTasks.post { t.run() }
    }

    companion object {
        const val MIN_PRIORITY = 1
        const val NORM_PRIORITY = 5
        const val MAX_PRIORITY = 10
    }
}
