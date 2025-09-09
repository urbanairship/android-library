/* Copyright Airship and Contributors */
package com.urbanairship.util

import com.urbanairship.BaseTestCase
import com.urbanairship.TestClock
import org.junit.Assert
import org.junit.Test

public class CachedValueTest : BaseTestCase() {

    private val clock = TestClock()
    private val cachedValue = CachedValue<String>(clock)

    @Test
    public fun testSetDate() {
        cachedValue.set("some value", clock.currentTimeMillis() + 100)
        Assert.assertEquals("some value", cachedValue.get())

        clock.currentTimeMillis += 99
        Assert.assertEquals("some value", cachedValue.get())

        clock.currentTimeMillis += 1
        Assert.assertNull(cachedValue.get())
    }

    @Test
    public fun testExpiry() {
        cachedValue.set("some value", clock.currentTimeMillis() + 100)
        Assert.assertEquals("some value", cachedValue.get())

        clock.currentTimeMillis += 99
        Assert.assertEquals("some value", cachedValue.get())
        clock.currentTimeMillis += 1
        Assert.assertNull(cachedValue.get())
    }

    @Test
    public fun testExpireIf() {
        cachedValue.set("some value", clock.currentTimeMillis() + 100)
        Assert.assertEquals("some value", cachedValue.get())

        cachedValue.expireIf { s: String -> s == "some other value" }
        Assert.assertEquals("some value", cachedValue.get())

        cachedValue.expireIf { s: String -> s == "some value" }
        Assert.assertNull(cachedValue.get())
    }

    @Test
    public fun testExpire() {
        cachedValue.set("some value", clock.currentTimeMillis() + 100)
        cachedValue.expire()
        Assert.assertNull(cachedValue.get())
    }
}
