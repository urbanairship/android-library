/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import kotlin.random.Random

/**
 * What a `randomize_children` order is derived from. Layouts that give the same key get the same
 * order.
 */
internal data class ShuffleKey(
    /** The layout's seed, drawn once per display unless a UI test fixes it. */
    val seed: String,
    /** The layout's identifier, if it has one. */
    val identifier: String? = null
)

/** The seed UI tests pass, so screenshot baselines hold from run to run and can be compared across platforms. */
internal const val UI_TEST_RANDOM_SEED: String = "ui-test"

/**
 * Draws a seed for a layout the host doesn't seed.
 *
 * @return a random seed string.
 */
internal fun randomShuffleSeed(): String = Random.nextInt().toUInt().toString(16)

/**
 * Returns the items in the shuffled order [key] gives, the same on every call and every platform.
 *
 * A Fisher–Yates shuffle drawing from [mulberry32], seeded with the [fnv1a32] hash of
 * [shuffleKeyString]. Each index is `(next() × (i + 1)) >> 32`. For example, seed `ui-test` and
 * identifier `survey_options` order five items `0, 4, 3, 2, 1`.
 *
 * @param key the seed and identifier the order is derived from.
 * @return a new list of the same items, shuffled.
 */
internal fun <T> List<T>.seededShuffled(key: ShuffleKey): List<T> {
    val shuffled = toMutableList()
    val next = mulberry32(fnv1a32(shuffleKeyString(key, size)))
    for (i in shuffled.lastIndex downTo 1) {
        val j = ((next().toULong() * (i + 1).toULong()) shr 32).toInt()
        shuffled[i] = shuffled[j].also { shuffled[j] = shuffled[i] }
    }
    return shuffled
}

/**
 * Returns the items in [key]'s shuffled order when [randomize] is set, else as they are.
 *
 * Derived from the key rather than drawn, so async content built again keeps the order already
 * shown, and a button describes its children in the order they're shown.
 */
internal fun <T> List<T>.shuffledIf(randomize: Boolean, key: ShuffleKey): List<T> =
    if (randomize) seededShuffled(key) else this

/**
 * Builds the string a shuffle is hashed from: `<seed>:<identifier>:<count>`, or `<seed>:<count>`
 * for a layout without an identifier.
 *
 * For example, seed `ui-test`, identifier `survey_options` and 5 items give
 * `ui-test:survey_options:5`.
 *
 * @param key the seed and identifier; an empty identifier counts as none.
 * @param count how many items are shuffled, as written, before any are hidden.
 * @return the string to hash.
 */
internal fun shuffleKeyString(key: ShuffleKey, count: Int): String =
    listOfNotNull(key.seed, key.identifier, count.toString())
        .filter { it.isNotEmpty() }
        .joinToString(":")

/**
 * Returns the FNV-1a 32-bit hash of a string's UTF-8 bytes.
 *
 * @param text the string to hash; a lone surrogate encodes as U+FFFD, as web's `TextEncoder` does,
 * rather than the `?` Java's encoder writes.
 * @return the hash.
 */
internal fun fnv1a32(text: String): UInt {
    var hash = 0x811c9dc5u
    for (byte in utf8Bytes(text)) {
        hash = (hash xor byte.toUByte().toUInt()) * 0x01000193u
    }
    return hash
}

private fun utf8Bytes(text: String): ByteArray {
    val wellFormed = StringBuilder(text.length)
    text.codePoints().forEach { code ->
        wellFormed.appendCodePoint(if (code in 0xD800..0xDFFF) 0xFFFD else code)
    }
    return wellFormed.toString().toByteArray(Charsets.UTF_8)
}

/**
 * Creates a mulberry32 generator.
 *
 * Every step is 32-bit integer arithmetic, so any platform implementing the same steps draws the
 * same sequence from the same state.
 *
 * @param state the starting state.
 * @return a function giving the next draw on each call.
 */
internal fun mulberry32(state: UInt): () -> UInt {
    var a = state
    return {
        a += 0x6d2b79f5u
        var t = (a xor (a shr 15)) * (1u or a)
        t = (t + (t xor (t shr 7)) * (61u or t)) xor t
        t xor (t shr 14)
    }
}
