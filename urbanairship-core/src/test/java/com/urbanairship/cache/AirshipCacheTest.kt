package com.urbanairship.cache

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.json.JsonValue
import com.urbanairship.util.Clock
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AirshipCacheTest {
    private val clock: Clock = mockk()
    private val config: AirshipRuntimeConfig = mockk()
    private val appVersion = "1.2.3"
    private val sdkVersion = "3.2.1"
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var cache: AirshipCache

    @Before
    public fun setup() {
        every { clock.currentTimeMillis() } returns 0L

        cache = AirshipCache(
            context = ApplicationProvider.getApplicationContext(),
            runtimeConfig = config,
            isPersistent = false,
            appVersion = appVersion,
            sdkVersion = sdkVersion,
            clock = clock,
            dispatcher = testDispatcher
        )
    }

    @Test
    public fun testStoreSavesItems(): TestResult = runTest {
        val content = JsonValue.wrap("item")
        val storeKey = "key-to-store"

        var stored = cache.getCached(storeKey) { it }
        assertNull(stored)

        cache.store(content, storeKey, 1u)
        stored = cache.getCached(storeKey) { it }

        assertEquals(content, stored)
    }

    @Test
    public fun testDeleteExpired(): TestResult = runTest {
        val storedItems = mapOf(
            "key-1" to JsonValue.wrap(1),
            "key-2" to JsonValue.wrap(2),
            "key-3" to JsonValue.wrap(3),
            "key-4" to JsonValue.wrap(4),
        )

        var ttl: ULong = 1u

        storedItems.forEach { key, json ->
            runBlocking { cache.store(json, key, ttl) }
            ttl += 1u
        }

        val savedItems = {
            var result = 0
            storedItems.mapKeys {
                val stored = runBlocking { cache.getCached(it.key) { it } }
                if (stored != null) {
                    result += 1
                }
            }
            result
        }

        assertEquals(4, savedItems())

        cache.deleteExpired(timestamp = 2)
        assertEquals(3, savedItems())

        cache.deleteExpired(appVersion = "1.1.1")
        assertEquals(0, savedItems())

        ttl = 1u
        storedItems.forEach { key, json ->
            runBlocking { cache.store(json, key, ttl) }
            ttl += 1u
        }

        cache.deleteExpired(sdkVersion = "1.1.1")
        assertEquals(0, savedItems())
    }

    @Test
    public fun getCachedDoesntReturnExpired(): TestResult = runTest {
        val key = "key"
        cache.store(JsonValue.wrap(2), key, 1u)

        assertNotNull(cache.getCached(key) { it })
        every { clock.currentTimeMillis() } returns 2

        assertNull(cache.getCached(key) { it })
    }
}
