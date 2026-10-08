package com.keystone.rpg

import android.media.AudioTrack
import android.media.WebAudioUnlock
import org.khronos.webgl.Float32Array
import org.khronos.webgl.set

/**
 * Plays the game's synthesized sound through Web Audio. The browser asks for audio in
 * blocks; each request pulls the original mixer (Sound.kt) as many times as it needs.
 */
object WebAudioOut {
    private var context: dynamic = null
    private var node: dynamic = null

    fun stream(track: AudioTrack, keepGoing: () -> Boolean, fill: () -> ShortArray) {
        track.streaming = true
        stopNode()
        if (context == null) context = createContext(track.sampleRate)
        val ac = context
        val step = track.sampleRate / (ac.sampleRate as Double)
        var block: ShortArray? = null
        var pos = 0.0
        val n = ac.createScriptProcessor(2048, 0, 1)
        n.onaudioprocess = { e: dynamic ->
            val out = e.outputBuffer.getChannelData(0).unsafeCast<Float32Array>()
            val len = out.length
            var i = 0
            while (i < len) {
                var b = block
                if (b == null || pos >= b.size) {
                    if (!keepGoing()) {
                        while (i < len) out[i++] = 0f
                        stopNode()
                        break
                    }
                    // Carry the fractional position over into the next block.
                    val consumed = b?.size ?: 0
                    b = fill()
                    block = b
                    pos = (pos - consumed).coerceAtLeast(0.0)
                }
                out[i] = b[pos.toInt().coerceAtMost(b.size - 1)] / 32768f
                pos += step
                i++
            }
        }
        n.connect(ac.destination)
        node = n
        WebAudioUnlock.later { ac.resume() }
    }

    private fun createContext(rate: Int): dynamic {
        val w = kotlinx.browser.window.asDynamic()
        val ctor = w.AudioContext ?: w.webkitAudioContext
        return try {
            js("new ctor({ sampleRate: rate })")
        } catch (t: Throwable) {
            js("new ctor()")
        }
    }

    private fun stopNode() {
        val n = node ?: return
        n.onaudioprocess = null
        n.disconnect()
        node = null
    }
}
