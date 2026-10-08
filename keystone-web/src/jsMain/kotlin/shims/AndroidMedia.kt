package android.media

import android.content.Context
import com.keystone.rpg.WebResources
import org.w3c.dom.Audio
import org.w3c.dom.HTMLAudioElement

class AudioAttributes private constructor() {
    class Builder {
        fun setUsage(u: Int) = this
        fun setContentType(t: Int) = this
        fun build() = AudioAttributes()
    }

    companion object {
        const val USAGE_MEDIA = 1
        const val USAGE_GAME = 14
        const val CONTENT_TYPE_MUSIC = 2
        const val CONTENT_TYPE_SONIFICATION = 4
    }
}

class AudioFormat private constructor(val sampleRate: Int) {
    class Builder {
        private var rate = 44100
        fun setSampleRate(r: Int) = also { rate = r }
        fun setEncoding(e: Int) = this
        fun setChannelMask(m: Int) = this
        fun build() = AudioFormat(rate)
    }

    companion object {
        const val CHANNEL_OUT_MONO = 4
        const val CHANNEL_OUT_STEREO = 12
        const val ENCODING_PCM_16BIT = 2
        const val ENCODING_PCM_FLOAT = 4
    }
}

/**
 * AudioTrack for the web. The game's mixer is pulled by Web Audio (see WebAudioOut), so
 * while streaming, pause/flush/release from the original loop's cleanup are ignored.
 */
class AudioTrack private constructor(val sampleRate: Int) {
    var streaming = false

    fun play() {}
    fun write(data: ShortArray, offset: Int, size: Int): Int = size
    fun pause() {}
    fun flush() {}
    fun stop() {}
    fun release() {}

    class Builder {
        private var rate = 44100
        fun setAudioAttributes(a: AudioAttributes) = this
        fun setAudioFormat(f: AudioFormat) = also { rate = f.sampleRate }
        fun setBufferSizeInBytes(n: Int) = this
        fun setTransferMode(m: Int) = this
        fun build() = AudioTrack(rate)
    }

    companion object {
        const val MODE_STATIC = 0
        const val MODE_STREAM = 1
        fun getMinBufferSize(rate: Int, channels: Int, encoding: Int) = 4096
    }
}

/** MediaPlayer on an HTML audio element, for music (and voice lines once they exist). */
class MediaPlayer private constructor(private val audio: HTMLAudioElement) {
    fun interface OnCompletionListener {
        fun onCompletion(mp: MediaPlayer)
    }

    var isLooping: Boolean
        get() = audio.loop
        set(v) {
            audio.loop = v
        }

    val isPlaying: Boolean get() = !audio.paused && !audio.ended

    fun start() {
        audio.play().catch { _ ->
            // Browsers block sound until the player interacts; retry on the first tap or key.
            WebAudioUnlock.later { audio.play() }
            null
        }
    }

    fun pause() = audio.pause()

    fun stop() {
        audio.pause()
        audio.currentTime = 0.0
    }

    fun release() {
        audio.pause()
        audio.src = ""
    }

    fun setVolume(left: Float, right: Float) {
        audio.volume = left.toDouble().coerceIn(0.0, 1.0)
    }

    fun setOnCompletionListener(l: OnCompletionListener) {
        audio.onended = { l.onCompletion(this) }
    }

    companion object {
        fun create(ctx: Context, resId: Int): MediaPlayer? = WebResources.files[resId]?.let { MediaPlayer(Audio(it)) }
    }
}

/** Runs things that need sound once the player has tapped or pressed a key. */
object WebAudioUnlock {
    private val waiting = ArrayList<() -> Unit>()
    var unlocked = false
        private set

    init {
        val w = kotlinx.browser.window
        val go: (org.w3c.dom.events.Event) -> Unit = {
            unlocked = true
            val list = ArrayList(waiting)
            waiting.clear()
            for (f in list) f()
        }
        w.addEventListener("pointerdown", go)
        w.addEventListener("keydown", go)
        w.addEventListener("touchend", go)
    }

    fun later(f: () -> Unit) {
        if (unlocked) f() else waiting += f
    }
}
