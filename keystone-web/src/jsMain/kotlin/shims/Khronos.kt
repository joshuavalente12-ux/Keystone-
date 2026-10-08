package javax.microedition.khronos.egl

class EGLConfig
class EGLDisplay

interface EGL10 {
    fun eglChooseConfig(display: EGLDisplay, attribs: IntArray, configs: Array<EGLConfig?>, size: Int, count: IntArray): Boolean

    companion object {
        const val EGL_RED_SIZE = 0x3024
        const val EGL_GREEN_SIZE = 0x3023
        const val EGL_BLUE_SIZE = 0x3022
        const val EGL_ALPHA_SIZE = 0x3021
        const val EGL_DEPTH_SIZE = 0x3025
        const val EGL_RENDERABLE_TYPE = 0x3040
        const val EGL_NONE = 0x3038
    }
}
