package android.opengl

import java.nio.Buffer
import java.nio.ByteBuffer
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.opengles.GL10
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Int8Array
import org.khronos.webgl.WebGLBuffer
import org.khronos.webgl.WebGLProgram
import org.khronos.webgl.WebGLRenderingContext
import org.khronos.webgl.WebGLShader
import org.khronos.webgl.WebGLUniformLocation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** android.opengl.Matrix, ported from AOSP (column-major 4x4 float matrices). */
object Matrix {
    private val temp = FloatArray(32)
    private val mulScratch = FloatArray(16)
    private val vecScratch = FloatArray(4)

    fun setIdentityM(sm: FloatArray, smOffset: Int) {
        for (i in 0 until 16) sm[smOffset + i] = 0f
        var i = 0
        while (i < 16) {
            sm[smOffset + i] = 1f
            i += 5
        }
    }

    fun multiplyMM(result: FloatArray, resultOffset: Int, lhs: FloatArray, lhsOffset: Int, rhs: FloatArray, rhsOffset: Int) {
        val r = mulScratch
        for (i in 0 until 4) {
            for (j in 0 until 4) {
                var s = 0f
                for (k in 0 until 4) s += lhs[lhsOffset + i + k * 4] * rhs[rhsOffset + k + j * 4]
                r[i + j * 4] = s
            }
        }
        r.copyInto(result, resultOffset)
    }

    fun multiplyMV(resultVec: FloatArray, resultVecOffset: Int, lhsMat: FloatArray, lhsMatOffset: Int, rhsVec: FloatArray, rhsVecOffset: Int) {
        val r = vecScratch
        for (i in 0 until 4) {
            var s = 0f
            for (k in 0 until 4) s += lhsMat[lhsMatOffset + i + k * 4] * rhsVec[rhsVecOffset + k]
            r[i] = s
        }
        r.copyInto(resultVec, resultVecOffset)
    }

    fun translateM(m: FloatArray, mOffset: Int, x: Float, y: Float, z: Float) {
        for (i in 0 until 4) {
            val mi = mOffset + i
            m[12 + mi] += m[mi] * x + m[4 + mi] * y + m[8 + mi] * z
        }
    }

    fun scaleM(m: FloatArray, mOffset: Int, x: Float, y: Float, z: Float) {
        for (i in 0 until 4) {
            val mi = mOffset + i
            m[mi] *= x
            m[4 + mi] *= y
            m[8 + mi] *= z
        }
    }

    fun rotateM(m: FloatArray, mOffset: Int, a: Float, x: Float, y: Float, z: Float) {
        setRotateM(temp, 0, a, x, y, z)
        multiplyMM(temp, 16, m, mOffset, temp, 0)
        temp.copyInto(m, mOffset, 16, 32)
    }

    fun setRotateM(rm: FloatArray, rmOffset: Int, aDeg: Float, x0: Float, y0: Float, z0: Float) {
        var x = x0
        var y = y0
        var z = z0
        rm[rmOffset + 3] = 0f
        rm[rmOffset + 7] = 0f
        rm[rmOffset + 11] = 0f
        rm[rmOffset + 12] = 0f
        rm[rmOffset + 13] = 0f
        rm[rmOffset + 14] = 0f
        rm[rmOffset + 15] = 1f
        val a = aDeg * (PI.toFloat() / 180f)
        val s = sin(a)
        val c = cos(a)
        if (1f == x && 0f == y && 0f == z) {
            rm[rmOffset + 5] = c; rm[rmOffset + 10] = c
            rm[rmOffset + 6] = s; rm[rmOffset + 9] = -s
            rm[rmOffset + 1] = 0f; rm[rmOffset + 2] = 0f
            rm[rmOffset + 4] = 0f; rm[rmOffset + 8] = 0f
            rm[rmOffset + 0] = 1f
        } else if (0f == x && 1f == y && 0f == z) {
            rm[rmOffset + 0] = c; rm[rmOffset + 10] = c
            rm[rmOffset + 8] = s; rm[rmOffset + 2] = -s
            rm[rmOffset + 1] = 0f; rm[rmOffset + 4] = 0f
            rm[rmOffset + 6] = 0f; rm[rmOffset + 9] = 0f
            rm[rmOffset + 5] = 1f
        } else if (0f == x && 0f == y && 1f == z) {
            rm[rmOffset + 0] = c; rm[rmOffset + 5] = c
            rm[rmOffset + 1] = s; rm[rmOffset + 4] = -s
            rm[rmOffset + 2] = 0f; rm[rmOffset + 6] = 0f
            rm[rmOffset + 8] = 0f; rm[rmOffset + 9] = 0f
            rm[rmOffset + 10] = 1f
        } else {
            val len = sqrt(x * x + y * y + z * z)
            if (len != 1f) {
                val recip = 1f / len
                x *= recip
                y *= recip
                z *= recip
            }
            val nc = 1f - c
            val xy = x * y
            val yz = y * z
            val zx = z * x
            val xs = x * s
            val ys = y * s
            val zs = z * s
            rm[rmOffset + 0] = x * x * nc + c
            rm[rmOffset + 4] = xy * nc - zs
            rm[rmOffset + 8] = zx * nc + ys
            rm[rmOffset + 1] = xy * nc + zs
            rm[rmOffset + 5] = y * y * nc + c
            rm[rmOffset + 9] = yz * nc - xs
            rm[rmOffset + 2] = zx * nc - ys
            rm[rmOffset + 6] = yz * nc + xs
            rm[rmOffset + 10] = z * z * nc + c
        }
    }

    fun perspectiveM(m: FloatArray, offset: Int, fovy: Float, aspect: Float, zNear: Float, zFar: Float) {
        val f = 1f / tan(fovy * (PI.toFloat() / 360f))
        val rangeReciprocal = 1f / (zNear - zFar)
        m[offset + 0] = f / aspect
        m[offset + 1] = 0f
        m[offset + 2] = 0f
        m[offset + 3] = 0f
        m[offset + 4] = 0f
        m[offset + 5] = f
        m[offset + 6] = 0f
        m[offset + 7] = 0f
        m[offset + 8] = 0f
        m[offset + 9] = 0f
        m[offset + 10] = (zFar + zNear) * rangeReciprocal
        m[offset + 11] = -1f
        m[offset + 12] = 0f
        m[offset + 13] = 0f
        m[offset + 14] = 2f * zFar * zNear * rangeReciprocal
        m[offset + 15] = 0f
    }

    fun setLookAtM(
        rm: FloatArray, rmOffset: Int,
        eyeX: Float, eyeY: Float, eyeZ: Float,
        centerX: Float, centerY: Float, centerZ: Float,
        upX: Float, upY: Float, upZ: Float,
    ) {
        var fx = centerX - eyeX
        var fy = centerY - eyeY
        var fz = centerZ - eyeZ
        val rlf = 1f / sqrt(fx * fx + fy * fy + fz * fz)
        fx *= rlf
        fy *= rlf
        fz *= rlf
        var sx = fy * upZ - fz * upY
        var sy = fz * upX - fx * upZ
        var sz = fx * upY - fy * upX
        val rls = 1f / sqrt(sx * sx + sy * sy + sz * sz)
        sx *= rls
        sy *= rls
        sz *= rls
        val ux = sy * fz - sz * fy
        val uy = sz * fx - sx * fz
        val uz = sx * fy - sy * fx
        rm[rmOffset + 0] = sx
        rm[rmOffset + 1] = ux
        rm[rmOffset + 2] = -fx
        rm[rmOffset + 3] = 0f
        rm[rmOffset + 4] = sy
        rm[rmOffset + 5] = uy
        rm[rmOffset + 6] = -fy
        rm[rmOffset + 7] = 0f
        rm[rmOffset + 8] = sz
        rm[rmOffset + 9] = uz
        rm[rmOffset + 10] = -fz
        rm[rmOffset + 11] = 0f
        rm[rmOffset + 12] = 0f
        rm[rmOffset + 13] = 0f
        rm[rmOffset + 14] = 0f
        rm[rmOffset + 15] = 1f
        translateM(rm, rmOffset, -eyeX, -eyeY, -eyeZ)
    }
}

/**
 * android.opengl.GLES20 on top of WebGL 1. OpenGL hands out integer ids; WebGL hands out
 * objects, so this keeps a table from one to the other.
 */
object GLES20 {
    const val GL_DEPTH_BUFFER_BIT = 0x0100
    const val GL_COLOR_BUFFER_BIT = 0x4000
    const val GL_POINTS = 0x0000
    const val GL_LINES = 0x0001
    const val GL_TRIANGLES = 0x0004
    const val GL_TRIANGLE_STRIP = 0x0005
    const val GL_ZERO = 0
    const val GL_ONE = 1
    const val GL_SRC_ALPHA = 0x0302
    const val GL_ONE_MINUS_SRC_ALPHA = 0x0303
    const val GL_LESS = 0x0201
    const val GL_LEQUAL = 0x0203
    const val GL_CULL_FACE = 0x0B44
    const val GL_DEPTH_TEST = 0x0B71
    const val GL_BLEND = 0x0BE2
    const val GL_BYTE = 0x1400
    const val GL_UNSIGNED_BYTE = 0x1401
    const val GL_SHORT = 0x1402
    const val GL_UNSIGNED_SHORT = 0x1403
    const val GL_FLOAT = 0x1406
    const val GL_ARRAY_BUFFER = 0x8892
    const val GL_ELEMENT_ARRAY_BUFFER = 0x8893
    const val GL_STREAM_DRAW = 0x88E0
    const val GL_STATIC_DRAW = 0x88E4
    const val GL_DYNAMIC_DRAW = 0x88E8
    const val GL_FRAGMENT_SHADER = 0x8B30
    const val GL_VERTEX_SHADER = 0x8B31
    const val GL_COMPILE_STATUS = 0x8B81
    const val GL_LINK_STATUS = 0x8B82
    const val GL_TRUE = 1
    const val GL_FALSE = 0

    lateinit var gl: WebGLRenderingContext

    private var nextId = 1
    private val buffers = HashMap<Int, WebGLBuffer>()
    private val shaders = HashMap<Int, WebGLShader>()
    private val programs = HashMap<Int, WebGLProgram>()
    private val uniforms = HashMap<Int, WebGLUniformLocation>()

    /** Loses every id, e.g. when the browser drops the GL context. */
    fun reset(context: WebGLRenderingContext) {
        gl = context
        buffers.clear()
        shaders.clear()
        programs.clear()
        uniforms.clear()
        // GLSL in the game uses derivatives for bump detail.
        gl.getExtension("OES_standard_derivatives")
    }

    private fun floats(a: FloatArray, offset: Int, count: Int): Float32Array {
        val f = a.unsafeCast<Float32Array>()
        return f.subarray(offset, offset + count)
    }

    fun glViewport(x: Int, y: Int, w: Int, h: Int) = gl.viewport(x, y, w, h)
    fun glClearColor(r: Float, g: Float, b: Float, a: Float) = gl.clearColor(r, g, b, a)
    fun glClear(mask: Int) = gl.clear(mask)
    fun glEnable(cap: Int) = gl.enable(cap)
    fun glDisable(cap: Int) = gl.disable(cap)
    fun glDepthFunc(f: Int) = gl.depthFunc(f)
    fun glDepthMask(flag: Boolean) = gl.depthMask(flag)
    fun glDepthRangef(n: Float, f: Float) = gl.depthRange(n, f)
    fun glBlendFunc(s: Int, d: Int) = gl.blendFunc(s, d)

    fun glGenBuffers(n: Int, ids: IntArray, offset: Int) {
        for (i in 0 until n) {
            val id = nextId++
            buffers[id] = gl.createBuffer()!!
            ids[offset + i] = id
        }
    }

    fun glDeleteBuffers(n: Int, ids: IntArray, offset: Int) {
        for (i in 0 until n) {
            val b = buffers.remove(ids[offset + i]) ?: continue
            gl.deleteBuffer(b)
        }
    }

    fun glBindBuffer(target: Int, id: Int) = gl.bindBuffer(target, if (id == 0) null else buffers[id])

    fun glBufferData(target: Int, size: Int, data: Buffer?, usage: Int) {
        if (data is ByteBuffer) {
            gl.bufferData(target, Int8Array(data.arrayBuffer, data.position(), size), usage)
        } else {
            gl.bufferData(target, size, usage)
        }
    }

    fun glCreateShader(type: Int): Int {
        val s = gl.createShader(type) ?: return 0
        val id = nextId++
        shaders[id] = s
        return id
    }

    fun glShaderSource(id: Int, source: String) = gl.shaderSource(shaders[id], source)
    fun glCompileShader(id: Int) = gl.compileShader(shaders[id])

    fun glGetShaderiv(id: Int, pname: Int, params: IntArray, offset: Int) {
        val v = gl.getShaderParameter(shaders[id], pname)
        params[offset] = when (v) {
            is Boolean -> if (v) 1 else 0
            is Number -> v.toInt()
            else -> 0
        }
    }

    fun glGetShaderInfoLog(id: Int): String = gl.getShaderInfoLog(shaders[id]) ?: ""

    fun glDeleteShader(id: Int) {
        val s = shaders.remove(id) ?: return
        gl.deleteShader(s)
    }

    fun glCreateProgram(): Int {
        val p = gl.createProgram() ?: return 0
        val id = nextId++
        programs[id] = p
        return id
    }

    fun glAttachShader(program: Int, shader: Int) = gl.attachShader(programs[program], shaders[shader])
    fun glLinkProgram(program: Int) = gl.linkProgram(programs[program])

    fun glGetProgramiv(id: Int, pname: Int, params: IntArray, offset: Int) {
        val v = gl.getProgramParameter(programs[id], pname)
        params[offset] = when (v) {
            is Boolean -> if (v) 1 else 0
            is Number -> v.toInt()
            else -> 0
        }
    }

    fun glGetProgramInfoLog(id: Int): String = gl.getProgramInfoLog(programs[id]) ?: ""

    fun glDeleteProgram(id: Int) {
        val p = programs.remove(id) ?: return
        gl.deleteProgram(p)
    }

    fun glUseProgram(id: Int) = gl.useProgram(if (id == 0) null else programs[id])

    fun glGetAttribLocation(program: Int, name: String): Int = gl.getAttribLocation(programs[program], name)

    fun glGetUniformLocation(program: Int, name: String): Int {
        val loc = gl.getUniformLocation(programs[program], name) ?: return -1
        val id = nextId++
        uniforms[id] = loc
        return id
    }

    fun glEnableVertexAttribArray(i: Int) = gl.enableVertexAttribArray(i)
    fun glDisableVertexAttribArray(i: Int) = gl.disableVertexAttribArray(i)

    fun glVertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int) =
        gl.vertexAttribPointer(index, size, type, normalized, stride, offset)

    fun glUniform1f(loc: Int, x: Float) = gl.uniform1f(uniforms[loc], x)
    fun glUniform2f(loc: Int, x: Float, y: Float) = gl.uniform2f(uniforms[loc], x, y)
    fun glUniform3f(loc: Int, x: Float, y: Float, z: Float) = gl.uniform3f(uniforms[loc], x, y, z)
    fun glUniform4f(loc: Int, x: Float, y: Float, z: Float, w: Float) = gl.uniform4f(uniforms[loc], x, y, z, w)
    fun glUniform1i(loc: Int, x: Int) = gl.uniform1i(uniforms[loc], x)

    fun glUniform4fv(loc: Int, count: Int, v: FloatArray, offset: Int) {
        val u = uniforms[loc] ?: return
        gl.uniform4fv(u, floats(v, offset, count * 4))
    }

    fun glUniform3fv(loc: Int, count: Int, v: FloatArray, offset: Int) {
        val u = uniforms[loc] ?: return
        gl.uniform3fv(u, floats(v, offset, count * 3))
    }

    fun glUniformMatrix4fv(loc: Int, count: Int, transpose: Boolean, v: FloatArray, offset: Int) {
        val u = uniforms[loc] ?: return
        gl.uniformMatrix4fv(u, transpose, floats(v, offset, count * 16))
    }

    fun glDrawArrays(mode: Int, first: Int, count: Int) = gl.drawArrays(mode, first, count)
}

/** Only the parts of GLSurfaceView the renderer's interfaces need; the browser host drives frames. */
open class GLSurfaceView {
    interface Renderer {
        fun onSurfaceCreated(gl: GL10?, config: EGLConfig?)
        fun onSurfaceChanged(gl: GL10?, width: Int, height: Int)
        fun onDrawFrame(gl: GL10?)
    }

    interface EGLConfigChooser {
        fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig
    }

    companion object {
        const val RENDERMODE_WHEN_DIRTY = 0
        const val RENDERMODE_CONTINUOUSLY = 1
    }
}
