/* Copyright Airship and Contributors */
package com.urbanairship.util

import java.nio.charset.StandardCharsets

/**
 * Implementation of FarmHash Fingerprint64, an open-source fingerprinting algorithm for strings.
 *
 * Based on https://github.com/google/guava/blob/master/guava/src/com/google/common/hash/FarmHashFingerprint64.java
 *
 * @hide
 */
internal object FarmHashFingerprint64 {

    // Some primes between 2^63 and 2^64 for various uses.
    private const val K0 = -0x3c5a37a36834ced9L
    private const val K1 = -0x4b6d499041670d8dL
    private const val K2 = -0x651e95c4d06fbfb1L

    fun fingerprint(string: String): Long {
        val bytes = string.toByteArray(StandardCharsets.UTF_8)
        return fingerprint(bytes, 0, bytes.size)
    }

    fun fingerprint(bytes: ByteArray, offset: Int, length: Int): Long {
        return if (length <= 32) {
            if (length <= 16) {
                hashLength0to16(bytes, offset, length)
            } else {
                hashLength17to32(bytes, offset, length)
            }
        } else if (length <= 64) {
            hashLength33To64(bytes, offset, length)
        } else {
            hashLength65Plus(bytes, offset, length)
        }
    }

    fun load32(source: ByteArray, offset: Int): Long {
        var result: Long = 0
        for (i in 0..3) {
            result = result or ((source[offset + i].toLong() and 0xFFL) shl (i * 8))
        }
        return result
    }

    fun load64(source: ByteArray, offset: Int): Long {
        var result: Long = 0
        for (i in 0..7) {
            result = result or ((source[offset + i].toLong() and 0xFFL) shl (i * 8))
        }
        return result
    }

    private fun shiftMix(`val`: Long): Long {
        return `val` xor (`val` ushr 47)
    }

    private fun hashLength16(u: Long, v: Long, mul: Long): Long {
        var a = (u xor v) * mul
        a = a xor (a ushr 47)
        var b = (v xor a) * mul
        b = b xor (b ushr 47)
        b *= mul
        return b
    }

    /**
     * Computes intermediate hash of 32 bytes of byte array from the given offset. Results are
     * returned in the output array because when we last measured, this was 12% faster than allocating
     * new arrays every time.
     */
    private fun weakHashLength32WithSeeds(
        bytes: ByteArray, offset: Int, seedA: Long, seedB: Long, output: LongArray
    ) {
        var seedA = seedA
        var seedB = seedB
        val part1 = load64(bytes, offset)
        val part2 = load64(bytes, offset + 8)
        val part3 = load64(bytes, offset + 16)
        val part4 = load64(bytes, offset + 24)

        seedA += part1
        seedB = java.lang.Long.rotateRight(seedB + seedA + part4, 21)
        val c = seedA
        seedA += part2
        seedA += part3
        seedB += java.lang.Long.rotateRight(seedA, 44)
        output[0] = seedA + part4
        output[1] = seedB + c
    }

    private fun hashLength0to16(bytes: ByteArray, offset: Int, length: Int): Long {
        if (length >= 8) {
            val mul = K2 + length * 2L
            val a = load64(bytes, offset) + K2
            val b = load64(bytes, offset + length - 8)
            val c = java.lang.Long.rotateRight(b, 37) * mul + a
            val d = (java.lang.Long.rotateRight(a, 25) + b) * mul
            return hashLength16(c, d, mul)
        }
        if (length >= 4) {
            val mul = K2 + length * 2
            val a = load32(bytes, offset) and 0xFFFFFFFFL
            return hashLength16(
                length + (a shl 3), load32(bytes, offset + length - 4) and 0xFFFFFFFFL, mul
            )
        }
        if (length > 0) {
            val a = bytes[offset]
            val b = bytes[offset + (length shr 1)]
            val c = bytes[offset + (length - 1)]
            val y = (a.toInt() and 0xFF) + ((b.toInt() and 0xFF) shl 8)
            val z = length + ((c.toInt() and 0xFF) shl 2)
            return shiftMix(y * K2 xor z * K0) * K2
        }
        return K2
    }

    private fun hashLength17to32(bytes: ByteArray, offset: Int, length: Int): Long {
        val mul = K2 + length * 2L
        val a = load64(bytes, offset) * K1
        val b = load64(bytes, offset + 8)
        val c = load64(bytes, offset + length - 8) * mul
        val d = load64(bytes, offset + length - 16) * K2
        return hashLength16(
            java.lang.Long.rotateRight(a + b, 43) + java.lang.Long.rotateRight(c, 30) + d,
            a + java.lang.Long.rotateRight(b + K2, 18) + c,
            mul
        )
    }

    private fun hashLength33To64(bytes: ByteArray, offset: Int, length: Int): Long {
        val mul = K2 + length * 2L
        val a = load64(bytes, offset) * K2
        val b = load64(bytes, offset + 8)
        val c = load64(bytes, offset + length - 8) * mul
        val d = load64(bytes, offset + length - 16) * K2
        val y = java.lang.Long.rotateRight(a + b, 43) + java.lang.Long.rotateRight(c, 30) + d
        val z = hashLength16(y, a + java.lang.Long.rotateRight(b + K2, 18) + c, mul)
        val e = load64(bytes, offset + 16) * mul
        val f = load64(bytes, offset + 24)
        val g = (y + load64(bytes, offset + length - 32)) * mul
        val h = (z + load64(bytes, offset + length - 24)) * mul
        return hashLength16(
            java.lang.Long.rotateRight(e + f, 43) + java.lang.Long.rotateRight(g, 30) + h,
            e + java.lang.Long.rotateRight(f + a, 18) + g,
            mul
        )
    }

    /*
     * Compute an 8-byte hash of a byte array of length greater than 64 bytes.
     */
    private fun hashLength65Plus(bytes: ByteArray, offset: Int, length: Int): Long {
        var offset = offset
        val seed = 81
        // For strings over 64 bytes we loop. Internal state consists of 56 bytes: v, w, x, y, and z.
        var x = seed.toLong()
        var y = seed * K1 + 113
        var z = shiftMix(y * K2 + 113) * K2
        val v = LongArray(2)
        val w = LongArray(2)
        x = x * K2 + load64(bytes, offset)

        // Set end so that after the loop we have 1 to 64 bytes left to process.
        val end = offset + ((length - 1) / 64) * 64
        val last64offset = end + ((length - 1) and 63) - 63
        do {
            x = java.lang.Long.rotateRight(x + y + v[0] + load64(bytes, offset + 8), 37) * K1
            y = java.lang.Long.rotateRight(y + v[1] + load64(bytes, offset + 48), 42) * K1
            x = x xor w[1]
            y += v[0] + load64(bytes, offset + 40)
            z = java.lang.Long.rotateRight(z + w[0], 33) * K1
            weakHashLength32WithSeeds(bytes, offset, v[1] * K1, x + w[0], v)
            weakHashLength32WithSeeds(
                bytes, offset + 32, z + w[1], y + load64(bytes, offset + 16), w
            )
            val tmp = x
            x = z
            z = tmp
            offset += 64
        } while (offset != end)
        val mul = K1 + ((z and 0xFFL) shl 1)
        // Operate on the last 64 bytes of input.
        offset = last64offset
        w[0] += ((length - 1) and 63).toLong()
        v[0] += w[0]
        w[0] += v[0]
        x = java.lang.Long.rotateRight(x + y + v[0] + load64(bytes, offset + 8), 37) * mul
        y = java.lang.Long.rotateRight(y + v[1] + load64(bytes, offset + 48), 42) * mul
        x = x xor w[1] * 9
        y += v[0] * 9 + load64(bytes, offset + 40)
        z = java.lang.Long.rotateRight(z + w[0], 33) * mul
        weakHashLength32WithSeeds(bytes, offset, v[1] * mul, x + w[0], v)
        weakHashLength32WithSeeds(
            bytes, offset + 32, z + w[1], y + load64(bytes, offset + 16), w
        )
        return hashLength16(
            hashLength16(v[0], w[0], mul) + shiftMix(y) * K0 + x,
            hashLength16(v[1], w[1], mul) + z,
            mul
        )
    }
}
