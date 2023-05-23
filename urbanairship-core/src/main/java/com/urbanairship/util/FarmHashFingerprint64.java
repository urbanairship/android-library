/* Copyright Airship and Contributors */

package com.urbanairship.util;

import java.nio.charset.StandardCharsets;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import static java.lang.Long.rotateRight;

/**
 * Implementation of FarmHash Fingerprint64, an open-source fingerprinting algorithm for strings.
 *
 * Based on https://github.com/google/guava/blob/master/guava/src/com/google/common/hash/FarmHashFingerprint64.java
 *
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class FarmHashFingerprint64 {

    // Some primes between 2^63 and 2^64 for various uses.
    private static final long K0 = 0xc3a5c85c97cb3127L;
    private static final long K1 = 0xb492b66fbe98f273L;
    private static final long K2 = 0x9ae16a3b2f90404fL;

    public static long fingerprint(@NonNull String string) {
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        return fingerprint(bytes, 0, bytes.length);
    }

    static long fingerprint(byte[] bytes, int offset, int length) {
        if (length <= 32) {
            if (length <= 16) {
                return hashLength0to16(bytes, offset, length);
            } else {
                return hashLength17to32(bytes, offset, length);
            }
        } else if (length <= 64) {
            return hashLength33To64(bytes, offset, length);
        } else {
            return hashLength65Plus(bytes, offset, length);
        }
    }

    static long load32(byte[] source, int offset) {
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result |= (source[offset + i] & 0xFFL) << (i * 8);
        }
        return result;
    }

    static long load64(byte[] source, int offset) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result |= (source[offset + i] & 0xFFL) << (i * 8);
        }
        return result;
    }

    private static long shiftMix(long val) {
        return val ^ (val >>> 47);
    }

    private static long hashLength16(long u, long v, long mul) {
        long a = (u ^ v) * mul;
        a ^= (a >>> 47);
        long b = (v ^ a) * mul;
        b ^= (b >>> 47);
        b *= mul;
        return b;
    }

    /**
     * Computes intermediate hash of 32 bytes of byte array from the given offset. Results are
     * returned in the output array because when we last measured, this was 12% faster than allocating
     * new arrays every time.
     */
    private static void weakHashLength32WithSeeds(
            byte[] bytes,
            int offset,
            long seedA,
            long seedB,
            long[] output
    ) {
        long part1 = load64(bytes, offset);
        long part2 = load64(bytes, offset + 8);
        long part3 = load64(bytes, offset + 16);
        long part4 = load64(bytes, offset + 24);

        seedA += part1;
        seedB = rotateRight(seedB + seedA + part4, 21);
        long c = seedA;
        seedA += part2;
        seedA += part3;
        seedB += rotateRight(seedA, 44);
        output[0] = seedA + part4;
        output[1] = seedB + c;
    }

    private static long hashLength0to16(byte[] bytes, int offset, int length) {
        if (length >= 8) {
            long mul = K2 + length * 2L;
            long a = load64(bytes, offset) + K2;
            long b = load64(bytes, offset + length - 8);
            long c = rotateRight(b, 37) * mul + a;
            long d = (rotateRight(a, 25) + b) * mul;
            return hashLength16(c, d, mul);
        }
        if (length >= 4) {
            long mul = K2 + length * 2;
            long a = load32(bytes, offset) & 0xFFFFFFFFL;
            return hashLength16(
                    length + (a << 3),
                    load32(bytes, offset + length - 4) & 0xFFFFFFFFL,
                    mul
            );
        }
        if (length > 0) {
            byte a = bytes[offset];
            byte b = bytes[offset + (length >> 1)];
            byte c = bytes[offset + (length - 1)];
            int y = (a & 0xFF) + ((b & 0xFF) << 8);
            int z = length + ((c & 0xFF) << 2);
            return shiftMix(y * K2 ^ z * K0) * K2;
        }
        return K2;
    }

    private static long hashLength17to32(byte[] bytes, int offset, int length) {
        long mul = K2 + length * 2L;
        long a = load64(bytes, offset) * K1;
        long b = load64(bytes, offset + 8);
        long c = load64(bytes, offset + length - 8) * mul;
        long d = load64(bytes, offset + length - 16) * K2;
        return hashLength16(
                rotateRight(a + b, 43) + rotateRight(c, 30) + d,
                a + rotateRight(b + K2, 18) + c,
                mul
        );
    }

    private static long hashLength33To64(byte[] bytes, int offset, int length) {
        long mul = K2 + length * 2L;
        long a = load64(bytes, offset) * K2;
        long b = load64(bytes, offset + 8);
        long c = load64(bytes, offset + length - 8) * mul;
        long d = load64(bytes, offset + length - 16) * K2;
        long y = rotateRight(a + b, 43) + rotateRight(c, 30) + d;
        long z = hashLength16(y, a + rotateRight(b + K2, 18) + c, mul);
        long e = load64(bytes, offset + 16) * mul;
        long f = load64(bytes, offset + 24);
        long g = (y + load64(bytes, offset + length - 32)) * mul;
        long h = (z + load64(bytes, offset + length - 24)) * mul;
        return hashLength16(
                rotateRight(e + f, 43) + rotateRight(g, 30) + h,
                e + rotateRight(f + a, 18) + g,
                mul
        );
    }

    /*
     * Compute an 8-byte hash of a byte array of length greater than 64 bytes.
     */
    private static long hashLength65Plus(byte[] bytes, int offset, int length) {
        int seed = 81;
        // For strings over 64 bytes we loop. Internal state consists of 56 bytes: v, w, x, y, and z.
        long x = seed;
        @SuppressWarnings("ConstantOverflow")
        long y = seed * K1 + 113;
        long z = shiftMix(y * K2 + 113) * K2;
        long[] v = new long[2];
        long[] w = new long[2];
        x = x * K2 + load64(bytes, offset);

        // Set end so that after the loop we have 1 to 64 bytes left to process.
        int end = offset + ((length - 1) / 64) * 64;
        int last64offset = end + ((length - 1) & 63) - 63;
        do {
            x = rotateRight(x + y + v[0] + load64(bytes, offset + 8), 37) * K1;
            y = rotateRight(y + v[1] + load64(bytes, offset + 48), 42) * K1;
            x ^= w[1];
            y += v[0] + load64(bytes, offset + 40);
            z = rotateRight(z + w[0], 33) * K1;
            weakHashLength32WithSeeds(bytes, offset, v[1] * K1, x + w[0], v);
            weakHashLength32WithSeeds(
                    bytes,
                    offset + 32,
                    z + w[1],
                    y + load64(bytes, offset + 16),
                    w
            );
            long tmp = x;
            x = z;
            z = tmp;
            offset += 64;
        } while (offset != end);
        long mul = K1 + ((z & 0xFF) << 1);
        // Operate on the last 64 bytes of input.
        offset = last64offset;
        w[0] += ((length - 1) & 63);
        v[0] += w[0];
        w[0] += v[0];
        x = rotateRight(x + y + v[0] + load64(bytes, offset + 8), 37) * mul;
        y = rotateRight(y + v[1] + load64(bytes, offset + 48), 42) * mul;
        x ^= w[1] * 9;
        y += v[0] * 9 + load64(bytes, offset + 40);
        z = rotateRight(z + w[0], 33) * mul;
        weakHashLength32WithSeeds(bytes, offset, v[1] * mul, x + w[0], v);
        weakHashLength32WithSeeds(
                bytes,
                offset + 32,
                z + w[1],
                y + load64(bytes, offset + 16),
                w
        );
        return hashLength16(
                hashLength16(v[0], w[0], mul) + shiftMix(y) * K0 + x,
                hashLength16(v[1], w[1], mul) + z,
                mul
        );
    }

}
