/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The vectors are web-thomas's, so every platform shuffles a scene the same way. */
public class SeededShuffleTest {

    private fun order(count: Int, key: ShuffleKey): List<Int> = List(count) { it }.seededShuffled(key)

    private fun hex(value: UInt) = "0x" + value.toString(16).padStart(8, '0')

    @Test
    public fun testFnv1a32HashesUtf8Bytes() {
        listOf(
            "" to "0x811c9dc5",
            "a" to "0xe40c292c",
            "é" to "0x1e9de8c1",
            "日" to "0x80d4e4fd",
            "😀" to "0x33a29608",
            "ui-test:survey_options:5" to "0x5eb9dae6"
        ).forEach { (text, expected) ->
            assertEquals(text, expected, hex(fnv1a32(text)))
        }
    }

    @Test
    public fun testFnv1a32HashesALoneSurrogateAsTheReplacementCharacter() {
        assertEquals(fnv1a32("�"), fnv1a32("\uD800"))
    }

    @Test
    public fun testMulberry32DrawsTheSharedSequence() {
        val next = mulberry32(fnv1a32("ui-test:survey_options:5"))
        assertEquals(
            listOf("0x4d47218c", "0xaaab5a8b", "0xabecdff8", "0xbbd8a688"),
            List(4) { hex(next()) }
        )
    }

    @Test
    public fun testShuffleKeyString() {
        assertEquals("ui-test:survey_options:5", shuffleKeyString(ShuffleKey("ui-test", "survey_options"), 5))
        assertEquals("ui-test:5", shuffleKeyString(ShuffleKey("ui-test"), 5))
        assertEquals("an empty identifier is none", "ui-test:3", shuffleKeyString(ShuffleKey("ui-test", ""), 3))
    }

    @Test
    public fun testSeededShuffleOrders() {
        listOf(
            Triple(ShuffleKey("ui-test", "survey_options"), 5, listOf(0, 4, 3, 2, 1)),
            Triple(ShuffleKey("ui-test", "survey_options"), 6, listOf(5, 0, 3, 4, 1, 2)),
            Triple(ShuffleKey("ui-test", "other_options"), 5, listOf(1, 0, 2, 3, 4)),
            Triple(ShuffleKey("seed-b", "survey_options"), 5, listOf(2, 0, 3, 4, 1)),
            Triple(ShuffleKey("ui-test", "first"), 5, listOf(4, 1, 0, 2, 3)),
            Triple(ShuffleKey("ui-test", "second"), 5, listOf(1, 0, 3, 2, 4)),
            Triple(ShuffleKey("ui-test"), 5, listOf(1, 3, 2, 0, 4)),
            Triple(ShuffleKey("ui-test", "colors"), 10, listOf(6, 0, 4, 8, 3, 1, 2, 7, 5, 9))
        ).forEach { (key, count, expected) ->
            assertEquals("$key with $count items", expected, order(count, key))
        }
    }

    @Test
    public fun testSeededShuffleReturnsANewListAndLeavesTheInputAlone() {
        val items = listOf("a", "b", "c", "d", "e")
        val shuffled = items.seededShuffled(ShuffleKey("ui-test", "survey_options"))
        assertEquals(listOf("a", "e", "d", "c", "b"), shuffled)
        assertEquals(listOf("a", "b", "c", "d", "e"), items)
        assertNotSame(items, shuffled)
    }

    @Test
    public fun testSeededShuffleHandlesShortLists() {
        (0..2).forEach { count ->
            assertEquals(List(count) { it }, order(count, ShuffleKey("ui-test")))
        }
    }

    @Test
    public fun testSeededShuffleAlwaysReturnsAPermutation() {
        (0..40).forEach { count ->
            assertEquals(List(count) { it }, order(count, ShuffleKey("s$count", "options")).sorted())
        }
    }

    @Test
    public fun testSeededShuffleGivesEveryOrderOfThreeAboutEquallyOften() {
        val runs = 60000
        val counts = (0 until runs).groupingBy { order(3, ShuffleKey("s$it")) }.eachCount()
        assertEquals(6, counts.size)
        counts.values.forEach { assertEquals(1.0 / 6, it.toDouble() / runs, 0.005) }
    }

    @Test
    public fun testRandomShuffleSeedsDiffer() {
        assertTrue(List(20) { randomShuffleSeed() }.toSet().size > 1)
    }
}
