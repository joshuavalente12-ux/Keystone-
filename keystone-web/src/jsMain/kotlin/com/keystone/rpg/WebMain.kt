package com.keystone.rpg

import android.content.Context
import android.content.res.Configuration
import android.opengl.GLES20
import android.opengl.GlHost
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.WebImages
import androidx.compose.ui.window.ComposeViewport
import java.util.concurrent.WebTasks
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Image
import org.jetbrains.skiko.wasm.onWasmReady
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.WebGLRenderingContext
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.MouseEvent
import org.w3c.fetch.Response
import kotlin.js.Promise
import kotlin.math.min
import kotlin.math.sqrt

/*
 * Runs Keystone in a browser. The game's own Compose screens (KeystoneApp, from
 * MainActivity.kt) are drawn by Compose Multiplatform on a transparent layer; the 3D world
 * is drawn by the game's WorldRenderer on a WebGL canvas underneath. Keyboard and mouse
 * are added for computers; touch goes through the game's own touch controls.
 */

private val webContext = Context()

/** Keyboard and mouse for computers, on top of the game's touch controls. */
private object DesktopInput {
    var game: Game? = null
    private val keys = HashSet<String>()
    private var keyMoving = false

    fun install(lockTarget: HTMLElement) {
        window.addEventListener("keydown", { e ->
            e as KeyboardEvent
            if (isTyping()) return@addEventListener
            val g = game ?: return@addEventListener
            if (!e.repeat) {
                keys.add(e.code)
                when (e.code) {
                    "Space" -> g.jump()
                    "KeyE", "KeyF" -> g.interact()
                    "KeyV" -> g.thirdPerson = !g.thirdPerson
                    "Digit1", "Digit2", "Digit3", "Digit4" -> g.useQuick(e.code.last() - '1')
                }
            }
            if (e.code == "Space" || e.code.startsWith("Arrow")) e.preventDefault()
        })
        window.addEventListener("keyup", { e -> keys.remove((e as KeyboardEvent).code) })
        window.addEventListener("blur", { keys.clear() })

        // Right click captures the mouse for looking around (Esc lets go); while captured,
        // the left button attacks.
        document.addEventListener("contextmenu", { e ->
            if (game == null) return@addEventListener
            e.preventDefault()
            if (document.asDynamic().pointerLockElement == lockTarget) document.asDynamic().exitPointerLock()
            else lockTarget.asDynamic().requestPointerLock()
        })
        document.addEventListener("mousemove", { e ->
            e as MouseEvent
            if (document.asDynamic().pointerLockElement != lockTarget) return@addEventListener
            val dx = (e.asDynamic().movementX as Number).toFloat()
            val dy = (e.asDynamic().movementY as Number).toFloat()
            game?.addLook(dx * 0.0035f, dy * 0.0035f)
        })
        document.addEventListener("mousedown", { e ->
            e as MouseEvent
            if (document.asDynamic().pointerLockElement == lockTarget && e.button.toInt() == 0) game?.attack()
        })
    }

    private fun isTyping(): Boolean {
        val a = document.activeElement ?: return false
        return a.tagName == "INPUT" || a.tagName == "TEXTAREA"
    }

    /** Called every frame: WASD walks (the touch stick still works when no key is down). */
    fun apply() {
        val g = game ?: return
        var mx = 0f
        var my = 0f
        if ("KeyW" in keys || "ArrowUp" in keys) my -= 1f
        if ("KeyS" in keys || "ArrowDown" in keys) my += 1f
        if ("KeyA" in keys || "ArrowLeft" in keys) mx -= 1f
        if ("KeyD" in keys || "ArrowRight" in keys) mx += 1f
        if (mx != 0f || my != 0f) {
            val len = sqrt(mx * mx + my * my)
            g.moveX = mx / len
            g.moveY = my / len
            keyMoving = true
        } else if (keyMoving) {
            g.moveX = 0f
            g.moveY = 0f
            keyMoving = false
        }
        if ("ShiftLeft" in keys || "ShiftRight" in keys) g.runOn = true
    }
}

private fun status(text: String, error: Boolean = false) {
    val s = document.getElementById("status") as HTMLElement
    s.textContent = text
    s.style.display = if (text.isEmpty()) "none" else "flex"
    if (error) s.classList.add("error")
}

/** Finds the Game a WorldRenderer draws (its private field), for the keyboard controls. */
private fun gameOf(renderer: Any): Game? {
    val d = renderer.asDynamic()
    val keys = js("Object").keys(d).unsafeCast<Array<String>>()
    for (k in keys) {
        val v: Any? = d[k]
        if (v is Game) return v
    }
    return null
}

private fun loadImages(): Promise<Unit> {
    val loads = WebResources.drawables.map { id ->
        val url = WebResources.files.getValue(id)
        window.fetch(url)
            .then { r: Response -> r.arrayBuffer() }
            .then { buf: ArrayBuffer ->
                val bytes = Int8Array(buf).unsafeCast<ByteArray>()
                WebImages.byId[id] = Image.makeFromEncoded(bytes).toComposeImageBitmap()
            }
    }
    return Promise.all(loads.toTypedArray()).then { }
}

private fun screenConfig() = Configuration(window.innerWidth, window.innerHeight)

private fun frame(@Suppress("UNUSED_PARAMETER") now: Double) {
    try {
        WebTasks.runFor(6.0) { window.performance.now() }
        android.graphics.Bitmap.flushAll()
        DesktopInput.apply()
        GlHost.frame()
    } catch (t: Throwable) {
        console.error(t)
        status("Keystone hit an error: ${t.message}", error = true)
        return
    }
    window.requestAnimationFrame(::frame)
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() = onWasmReady {
    // Skia's web renderer paints the whole canvas white each frame; make that transparent
    // so the 3D canvas underneath shows through wherever the game draws nothing.
    js("var clear0 = globalThis.org_jetbrains_skia_Canvas__1nClear; globalThis.org_jetbrains_skia_Canvas__1nClear = function(p, c) { return clear0(p, c === -1 ? 0 : c); }")

    val glCanvas = document.getElementById("gl") as HTMLCanvasElement
    val gl = (glCanvas.getContext("webgl", js("({antialias: true, depth: true, powerPreference: 'high-performance'})"))
        ?: glCanvas.getContext("experimental-webgl")) as? WebGLRenderingContext
    if (gl == null) {
        status("Your browser doesn't support WebGL, which Keystone needs for 3D graphics.", error = true)
        return@onWasmReady
    }
    GLES20.reset(gl)
    GlHost.canvas = glCanvas
    val resizeGl = {
        val dpr = min(window.devicePixelRatio, 1.5)
        val w = (glCanvas.clientWidth * dpr).toInt().coerceAtLeast(1)
        val h = (glCanvas.clientHeight * dpr).toInt().coerceAtLeast(1)
        glCanvas.width = w
        glCanvas.height = h
        GlHost.resize(w, h)
    }
    resizeGl()
    window.addEventListener("resize", { resizeGl() })

    GlHost.onRenderer = { r -> gameOf(r)?.let { DesktopInput.game = it } }
    val ui = document.getElementById("ui") as HTMLElement
    DesktopInput.install(ui)

    WebFonts.regular = FontMgr.default.matchFamilyStyle(null, FontStyle.NORMAL)
    WebFonts.bold = FontMgr.default.matchFamilyStyle(null, FontStyle.BOLD)

    loadImages().then {
        status("")
        ComposeViewport(ui) {
            var config by remember { mutableStateOf(screenConfig()) }
            DisposableEffect(Unit) {
                val onResize: (org.w3c.dom.events.Event) -> Unit = { config = screenConfig() }
                window.addEventListener("resize", onResize)
                onDispose { window.removeEventListener("resize", onResize) }
            }
            CompositionLocalProvider(
                LocalContext provides webContext,
                LocalView provides View(webContext),
                LocalConfiguration provides config,
            ) {
                KeystoneApp()
            }
        }
        window.requestAnimationFrame(::frame)
    }.catch { e ->
        console.error(e)
        status("Keystone couldn't load its images.", error = true)
    }
}
