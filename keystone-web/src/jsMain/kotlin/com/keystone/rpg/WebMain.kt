package com.keystone.rpg

import android.content.Context
import android.opengl.GLES20
import java.util.concurrent.WebTasks
import kotlinx.browser.document
import kotlinx.browser.window
import org.khronos.webgl.WebGLRenderingContext
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Runs Keystone in a browser: a WebGL canvas in place of GLSurfaceView, the browser's
 * animation frames in place of the GL thread, and keyboard, mouse and touch in place of
 * the Android touch layer. The game itself is the same code as the Android app.
 */
private class WebHost(private val canvas: HTMLCanvasElement, private val hud: Hud) {
    private val context = Context()
    private lateinit var game: Game
    private lateinit var renderer: WorldRenderer
    private val keys = HashSet<String>()
    private var width = 0
    private var height = 0

    // Touch: left half walks (floating stick), right half looks.
    private var stickId = -1
    private var stickX = 0.0
    private var stickY = 0.0
    private var lookId = -1
    private var lookX = 0.0
    private var lookY = 0.0
    private var touchMove = false

    fun start() {
        val gl = (canvas.getContext("webgl", js("({antialias: true, depth: true, powerPreference: 'high-performance'})"))
            ?: canvas.getContext("experimental-webgl")) as? WebGLRenderingContext
        if (gl == null) {
            hud.fatal("Your browser doesn't support WebGL, which Keystone needs for 3D graphics.")
            return
        }
        GLES20.reset(gl)
        hud.status("Building the world…")
        // Let the loading message paint before the heavy world generation starts.
        window.setTimeout({ build() }, 50)
    }

    private fun build() {
        try {
            game = Game(World())
            game.loadSave(context)
            Sound.load(context)
            renderer = WorldRenderer(game)
            renderer.onSurfaceCreated(null, null)
            resize()
            window.addEventListener("resize", { resize() })
            bindInput()
            hud.ready()
            window.requestAnimationFrame(::frame)
        } catch (t: Throwable) {
            console.error(t)
            hud.fatal("Keystone couldn't start: ${t.message}")
        }
    }

    private fun resize() {
        val dpr = min(window.devicePixelRatio, 1.5)
        width = (canvas.clientWidth * dpr).toInt().coerceAtLeast(1)
        height = (canvas.clientHeight * dpr).toInt().coerceAtLeast(1)
        canvas.width = width
        canvas.height = height
        renderer.onSurfaceChanged(null, width, height)
    }

    private fun frame(now: Double) {
        val t0 = window.performance.now()
        // Chunk meshes and other "background thread" work, a few milliseconds a frame.
        WebTasks.runFor(6.0) { window.performance.now() }
        val t1 = window.performance.now()
        applyKeys()
        try {
            renderer.onDrawFrame(null)
            val t2 = window.performance.now()
            Stats.record(t1 - t0, t2 - t1, WebTasks.pending)
        } catch (t: Throwable) {
            console.error(t)
            hud.fatal("Keystone hit an error: ${t.message}")
            return
        }
        hud.update(game)
        window.requestAnimationFrame(::frame)
    }

    private fun applyKeys() {
        if (touchMove) return
        var mx = 0f
        var my = 0f
        if ("KeyW" in keys || "ArrowUp" in keys) my -= 1f
        if ("KeyS" in keys || "ArrowDown" in keys) my += 1f
        if ("KeyA" in keys || "ArrowLeft" in keys) mx -= 1f
        if ("KeyD" in keys || "ArrowRight" in keys) mx += 1f
        val len = sqrt(mx * mx + my * my)
        if (len > 1f) {
            mx /= len
            my /= len
        }
        game.moveX = mx
        game.moveY = my
        game.runOn = "ShiftLeft" in keys || "ShiftRight" in keys
    }

    private fun bindInput() {
        window.addEventListener("keydown", { e ->
            e as KeyboardEvent
            if (e.repeat) return@addEventListener
            keys.add(e.code)
            when (e.code) {
                "Space" -> game.jump()
                "KeyE", "KeyF" -> game.interact()
                "KeyV" -> game.thirdPerson = !game.thirdPerson
                "Digit1", "Digit2", "Digit3", "Digit4" -> game.useQuick(e.code.last() - '1')
            }
            if (e.code == "Space" || e.code.startsWith("Arrow")) e.preventDefault()
        })
        window.addEventListener("keyup", { e -> keys.remove((e as KeyboardEvent).code) })
        window.addEventListener("blur", { keys.clear() })

        // Mouse: click to capture the pointer, move to look, left button attacks.
        canvas.addEventListener("mousedown", { e ->
            e as MouseEvent
            if (document.asDynamic().pointerLockElement != canvas) {
                canvas.asDynamic().requestPointerLock()
            } else if (e.button.toInt() == 0) {
                game.attack()
            }
        })
        document.addEventListener("mousemove", { e ->
            e as MouseEvent
            if (document.asDynamic().pointerLockElement == canvas) {
                game.addLook(e.asDynamic().movementX.unsafeCast<Double>().toFloat() * 0.0035f, e.asDynamic().movementY.unsafeCast<Double>().toFloat() * 0.0035f)
            }
        })

        canvas.addEventListener("pointerdown", { e ->
            e as PointerEvent
            if (e.pointerType != "touch") return@addEventListener
            if (e.clientX.toDouble() < canvas.clientWidth / 2.0 && stickId < 0) {
                stickId = e.pointerId
                stickX = e.clientX.toDouble()
                stickY = e.clientY.toDouble()
                touchMove = true
            } else if (lookId < 0) {
                lookId = e.pointerId
                lookX = e.clientX.toDouble()
                lookY = e.clientY.toDouble()
            }
        })
        canvas.addEventListener("pointermove", { e ->
            e as PointerEvent
            if (e.pointerId == stickId) {
                val maxR = 56.0
                var dx = e.clientX.toDouble() - stickX
                var dy = e.clientY.toDouble() - stickY
                val len = sqrt(dx * dx + dy * dy)
                if (len > maxR) {
                    stickX = e.clientX.toDouble() - dx * maxR / len
                    stickY = e.clientY.toDouble() - dy * maxR / len
                    dx = e.clientX.toDouble() - stickX
                    dy = e.clientY.toDouble() - stickY
                }
                if (len > maxR * 0.12) {
                    game.moveX = (dx / maxR).toFloat()
                    game.moveY = (dy / maxR).toFloat()
                } else {
                    game.moveX = 0f
                    game.moveY = 0f
                }
            } else if (e.pointerId == lookId) {
                val dx = e.clientX.toDouble() - lookX
                val dy = e.clientY.toDouble() - lookY
                lookX = e.clientX.toDouble()
                lookY = e.clientY.toDouble()
                game.addLook((dx * 0.006).toFloat(), (dy * 0.006).toFloat())
            }
        })
        val end = { e: org.w3c.dom.events.Event ->
            e as PointerEvent
            if (e.pointerId == stickId) {
                stickId = -1
                touchMove = false
                game.moveX = 0f
                game.moveY = 0f
            }
            if (e.pointerId == lookId) lookId = -1
        }
        canvas.addEventListener("pointerup", end)
        canvas.addEventListener("pointercancel", end)

        hud.onTouchButton("jump") { game.jump() }
        hud.onTouchButton("attack") { game.attack() }
        hud.onTouchButton("use") { game.interact() }
        hud.onTouchButton("run") { game.runOn = !game.runOn }
    }
}

/** The thin HTML layer over the 3D view: loading text, health and stamina, messages. */
private class Hud {
    private val status = document.getElementById("status") as HTMLElement
    private val bars = document.getElementById("bars") as HTMLElement
    private val health = document.getElementById("health") as HTMLElement
    private val stamina = document.getElementById("stamina") as HTMLElement
    private val toast = document.getElementById("toast") as HTMLElement
    private var lastToast = ""
    private var toastUntil = 0.0

    fun status(text: String) {
        status.textContent = text
        status.style.display = "flex"
    }

    fun fatal(text: String) {
        status.textContent = text
        status.style.display = "flex"
        status.classList.add("error")
    }

    fun ready() {
        status.style.display = "none"
        bars.style.display = "flex"
        document.body?.classList?.add("playing")
    }

    fun onTouchButton(id: String, action: () -> Unit) {
        val b = document.getElementById("btn-$id") as? HTMLElement ?: return
        b.addEventListener("pointerdown", { e ->
            e.preventDefault()
            e.stopPropagation()
            action()
        })
    }

    fun update(game: Game) {
        health.style.width = "${(game.health / game.maxHealth * 100f).coerceIn(0f, 100f)}%"
        stamina.style.width = "${(game.stamina * 100f).coerceIn(0f, 100f)}%"
        val now = window.performance.now()
        val msg = game.questToast
        if (msg.isNotEmpty() && msg != lastToast) {
            lastToast = msg
            toast.textContent = msg
            toast.style.opacity = "1"
            toastUntil = now + 4000.0
        }
        if (toastUntil != 0.0 && now > toastUntil) {
            toast.style.opacity = "0"
            toastUntil = 0.0
        }
    }
}

/** Frame timings, readable from the browser console as keystoneStats. */
private object Stats {
    var frames = 0
    var taskMs = 0.0
    var drawMs = 0.0
    var maxDrawMs = 0.0
    var pending = 0

    fun record(task: Double, draw: Double, pendingTasks: Int) {
        frames++
        taskMs += task
        drawMs += draw
        if (draw > maxDrawMs) maxDrawMs = draw
        pending = pendingTasks
        window.asDynamic().keystoneStats = js("({})")
        val o = window.asDynamic().keystoneStats
        o.frames = frames
        o.avgTaskMs = taskMs / frames
        o.avgDrawMs = drawMs / frames
        o.maxDrawMs = maxDrawMs
        o.pendingTasks = pending
    }
}

fun main() {
    val canvas = document.getElementById("game") as HTMLCanvasElement
    WebHost(canvas, Hud()).start()
}
