package java.util

import kotlin.js.Date

/**
 * java.util.Random with the exact algorithm the JVM uses (48-bit LCG), so seeded worlds,
 * treasure and quests come out identical to the Android build.
 */
open class Random(seed: Long) {
    constructor() : this(uniqueSeed())

    private var state: Long = scramble(seed)

    fun setSeed(seed: Long) {
        state = scramble(seed)
    }

    protected fun next(bits: Int): Int {
        state = (state * MULTIPLIER + ADDEND) and MASK
        return (state ushr (48 - bits)).toInt()
    }

    fun nextInt(): Int = next(32)

    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        var r = next(31)
        val m = bound - 1
        if ((bound and m) == 0) return ((bound.toLong() * r.toLong()) shr 31).toInt()
        var u = r
        while (true) {
            r = u % bound
            if (u - r + m < 0) {
                u = next(31)
                continue
            }
            return r
        }
    }

    fun nextLong(): Long = (next(32).toLong() shl 32) + next(32)

    /** Java 17 RandomGenerator.nextLong(bound). */
    fun nextLong(bound: Long): Long {
        require(bound > 0L) { "bound must be positive" }
        var r = nextLong()
        val m = bound - 1
        if ((bound and m) == 0L) return r and m
        var u = r ushr 1
        while (true) {
            r = u % bound
            if (u + m - r < 0L) {
                u = nextLong() ushr 1
                continue
            }
            return r
        }
    }

    fun nextBoolean(): Boolean = next(1) != 0

    fun nextFloat(): Float = next(24) / (1 shl 24).toFloat()

    fun nextDouble(): Double = ((next(26).toLong() shl 27) + next(27)) * DOUBLE_UNIT

    private companion object {
        const val MULTIPLIER = 0x5DEECE66DL
        const val ADDEND = 0xBL
        const val MASK = (1L shl 48) - 1
        const val DOUBLE_UNIT = 1.1102230246251565E-16 // 1.0 / 2^53
        var uniquifier = 8682522807148012L

        fun scramble(seed: Long) = (seed xor MULTIPLIER) and MASK

        fun uniqueSeed(): Long {
            uniquifier *= 1181783497276652981L
            return uniquifier xor (Date.now() * 1_000_000.0).toLong()
        }
    }
}
