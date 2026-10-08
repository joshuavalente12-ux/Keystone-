package java.nio

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.DataView
import org.khronos.webgl.Int8Array

class ByteOrder private constructor(val little: Boolean) {
    companion object {
        val LITTLE_ENDIAN = ByteOrder(true)
        val BIG_ENDIAN = ByteOrder(false)
        fun nativeOrder() = LITTLE_ENDIAN
    }
}

open class Buffer

/** A java.nio.ByteBuffer over a JS ArrayBuffer, so mesh data can go straight to WebGL. */
class ByteBuffer private constructor(val arrayBuffer: ArrayBuffer) : Buffer() {
    private val view = DataView(arrayBuffer)
    private val capacity = arrayBuffer.byteLength
    private var position = 0
    private var limit = capacity
    private var little = false

    fun order(o: ByteOrder): ByteBuffer {
        little = o.little
        return this
    }

    fun capacity() = capacity
    fun position() = position
    fun position(p: Int): ByteBuffer {
        position = p
        return this
    }
    fun limit() = limit
    fun limit(l: Int): ByteBuffer {
        limit = l
        if (position > l) position = l
        return this
    }
    fun remaining() = limit - position
    fun hasRemaining() = position < limit

    fun clear(): ByteBuffer {
        position = 0
        limit = capacity
        return this
    }

    fun flip(): ByteBuffer {
        limit = position
        position = 0
        return this
    }

    fun rewind(): ByteBuffer {
        position = 0
        return this
    }

    private fun claim(n: Int): Int {
        if (position + n > limit) throw IndexOutOfBoundsException("ByteBuffer overflow")
        val at = position
        position += n
        return at
    }

    private fun check(index: Int, n: Int): Int {
        if (index < 0 || index + n > limit) throw IndexOutOfBoundsException("ByteBuffer index $index")
        return index
    }

    fun put(b: Byte): ByteBuffer {
        view.setInt8(claim(1), b)
        return this
    }

    fun put(index: Int, b: Byte): ByteBuffer {
        view.setInt8(check(index, 1), b)
        return this
    }

    /** Copies the rest of [src] (position..limit) in, like the JVM. */
    fun put(src: ByteBuffer): ByteBuffer {
        val n = src.remaining()
        val at = claim(n)
        Int8Array(arrayBuffer).set(Int8Array(src.arrayBuffer, src.position, n), at)
        src.position += n
        return this
    }

    fun put(src: ByteArray): ByteBuffer {
        for (b in src) put(b)
        return this
    }

    fun get(): Byte = view.getInt8(claim(1))
    fun get(index: Int): Byte = view.getInt8(check(index, 1))

    fun putFloat(v: Float): ByteBuffer {
        view.setFloat32(claim(4), v, little)
        return this
    }

    fun putFloat(index: Int, v: Float): ByteBuffer {
        view.setFloat32(check(index, 4), v, little)
        return this
    }

    fun getFloat(): Float = view.getFloat32(claim(4), little)
    fun getFloat(index: Int): Float = view.getFloat32(check(index, 4), little)

    fun putInt(v: Int): ByteBuffer {
        view.setInt32(claim(4), v, little)
        return this
    }

    fun putInt(index: Int, v: Int): ByteBuffer {
        view.setInt32(check(index, 4), v, little)
        return this
    }

    fun getInt(): Int = view.getInt32(claim(4), little)
    fun getInt(index: Int): Int = view.getInt32(check(index, 4), little)

    fun putShort(v: Short): ByteBuffer {
        view.setInt16(claim(2), v, little)
        return this
    }

    fun getShort(): Short = view.getInt16(claim(2), little)

    companion object {
        fun allocateDirect(n: Int) = ByteBuffer(ArrayBuffer(n))
        fun allocate(n: Int) = ByteBuffer(ArrayBuffer(n))
    }
}
