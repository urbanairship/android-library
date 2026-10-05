/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class PlacementCycleBreakerTest {

    private val a = Any()
    private val b = Any()
    private val c = Any()

    @Test
    public fun testRefusesTheMoveThatWouldRepeatABackAndForth() {
        val breaker = PlacementCycleBreaker<Any, String>()
        assertTrue(breaker.allows(a, b, "window"))
        assertTrue(breaker.allows(b, a, "window"))
        assertFalse(breaker.allows(a, b, "window"))
        assertFalse("still refused", breaker.allows(a, b, "window"))
    }

    @Test
    public fun testAllowsMovesOnToAThirdPlacement() {
        val breaker = PlacementCycleBreaker<Any, String>()
        assertTrue(breaker.allows(a, b, "window"))
        assertTrue(breaker.allows(b, c, "window"))
        assertTrue(breaker.allows(c, b, "window"))
        assertTrue(breaker.allows(b, a, "window"))
    }

    @Test
    public fun testStartsOverInANewWindowOrAfterAReset() {
        val breaker = PlacementCycleBreaker<Any, String>()
        breaker.allows(a, b, "window")
        breaker.allows(b, a, "window")
        assertTrue(breaker.allows(a, b, "rotated"))

        breaker.allows(b, a, "rotated")
        breaker.reset()
        assertTrue(breaker.allows(a, b, "rotated"))
    }
}
