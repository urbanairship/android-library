/* Copyright Airship and Contributors */
package com.urbanairship.util

import com.urbanairship.BaseTestCase
import com.urbanairship.TestClock
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert
import org.junit.Test

public class CachedValueTest : BaseTestCase() {

    private val clock = TestClock()
    private val cachedValue = CachedValue<String>(clock)

    @Test
    public fun testSetDate() {
        cachedValue.set("some value", clock.now() + 100.milliseconds)
        Assert.assertEquals("some value", cachedValue.get())

        clock.currentTime += (99).milliseconds
        Assert.assertEquals("some value", cachedValue.get())

        clock.currentTime += (1).milliseconds
        Assert.assertNull(cachedValue.get())
    }

    @Test
    public fun testExpiry() {
        cachedValue.set("some value", clock.now() + 100.milliseconds)
        Assert.assertEquals("some value", cachedValue.get())

        clock.currentTime += (99).milliseconds
        Assert.assertEquals("some value", cachedValue.get())
        clock.currentTime += (1).milliseconds
        Assert.assertNull(cachedValue.get())
    }

    @Test
    public fun testExpireIf() {
        cachedValue.set("some value", clock.now() + 100.milliseconds)
        Assert.assertEquals("some value", cachedValue.get())

        cachedValue.expireIf { s: String -> s == "some other value" }
        Assert.assertEquals("some value", cachedValue.get())

        cachedValue.expireIf { s: String -> s == "some value" }
        Assert.assertNull(cachedValue.get())
    }

    @Test
    public fun testExpire() {
        cachedValue.set("some value", clock.now() + 100.milliseconds)
        cachedValue.expire()
        Assert.assertNull(cachedValue.get())
    }
}
