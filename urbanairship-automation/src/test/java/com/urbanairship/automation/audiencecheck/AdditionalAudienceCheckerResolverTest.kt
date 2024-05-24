package com.urbanairship.automation.audiencecheck

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestClock
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.automation.AudienceCheckOverrides
import com.urbanairship.cache.AirshipCache
import com.urbanairship.contacts.StableContactInfo
import com.urbanairship.http.RequestResult
import com.urbanairship.json.JsonValue
import com.urbanairship.remoteconfig.AdditionalAudienceCheckConfig
import com.urbanairship.remoteconfig.IAAConfig
import com.urbanairship.remoteconfig.RemoteConfig
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AdditionalAudienceCheckerResolverTest {
    private val clock = TestClock()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var cache: AirshipCache
    private val apiClient: AdditionalAudienceCheckApiClient = mockk()
    private val defaultConfig = AdditionalAudienceCheckConfig(
        isEnabled = true,
        context = JsonValue.wrap("remote config context"),
        url = "https://test.config"
    )

    private var contactId = ""
    private var namedUserId: String? = null

    private lateinit var resolver: AdditionalAudienceCheckerResolver
    private val deviceInfoProvider: DeviceInfoProvider = mockk()

    @Before
    public fun setup() {
        clock.currentTimeMillis = 1

        cache = AirshipCache(
            context = context,
            runtimeConfig = TestAirshipRuntimeConfig(),
            isPersistent = false,
            appVersion = "app version",
            sdkVersion = "sdk version",
            clock = clock
        )
    }

    @Test
    public fun testHappyPath(): TestResult = runTest {
        makeResolver()

        contactId = "existing-contact-id"
        namedUserId = "some user id"

        val channelID = "channel-id"

        val expected = AdditionalAudienceCheckApiClient.Result(
            isMatched = true,
            cacheTtl = 10.seconds
        )

        coEvery { apiClient.resolve(any()) } answers {
            val request: AdditionalAudienceCheckApiClient.Info = firstArg()

            assertEquals("channel-id", request.channelId)
            assertEquals("existing-contact-id", request.contactId)
            assertEquals("some user id", request.namedUserId)
            assertEquals(JsonValue.wrap("default context"), request.context)
            assertEquals("https://test.config", request.url)

            return@answers RequestResult(
                status = 200,
                value = expected,
                body = expected.toJsonValue().toString(),
                headers = mapOf()
            )
        }

        val cacheKey = "https://test.config:\"default context\":existing-contact-id:channel-id"

        var cached = getCached(cacheKey)
        assertNull(cached)

        mockDeviceInfo(channelID, contactId, namedUserId)

        val result = resolver.resolve(
            deviceInfoProvider,
            audienceCheckOverrides = AudienceCheckOverrides(
                bypass = false,
                context = JsonValue.wrap("default context"),
                url = null
            )
        )

        cached = getCached(cacheKey)
        assertEquals(expected, cached)
        assertEquals(true, cached?.isMatched)
        assertEquals(10.seconds, cached?.cacheTtl)
        assertTrue(result.getOrThrow())

        coVerify { apiClient.resolve(any()) }
    }

    @Test
    public fun testResolverReturnsTrueOnNoConfigOrDisabled(): TestResult = runTest {
        makeResolver(config = null)

        mockDeviceInfo()

        var result = resolver.resolve(
            deviceInfoProvider = deviceInfoProvider,
            audienceCheckOverrides = AudienceCheckOverrides(
                bypass = false,
                context = JsonValue.wrap("default context"),
                url = null
            )
        )

        assertTrue(result.getOrThrow())

        makeResolver(
            config = AdditionalAudienceCheckConfig(
                isEnabled = false,
                context = null,
                url = "https://test.url"
            )
        )

        result = resolver.resolve(
            deviceInfoProvider = deviceInfoProvider,
            audienceCheckOverrides = AudienceCheckOverrides(
                bypass = false,
                context = JsonValue.wrap("default context"),
                url = null
            )
        )

        assertTrue(result.getOrThrow())
    }

    @Test
    public fun testResolverThrowsOnNoUrlProvided(): TestResult = runTest {
        clock.currentTimeMillis = 0
        makeResolver()

        val response = RequestResult(
            status = 200,
            value = AdditionalAudienceCheckApiClient.Result(true, 1.seconds),
            body = "{\"is_matched\": true, \"cache_ttl\": 1}",
            headers = mapOf()
        )

        coEvery { apiClient.resolve(any()) } answers { response }

        mockDeviceInfo()

        var result = resolver.resolve(
            deviceInfoProvider = deviceInfoProvider,
            audienceCheckOverrides = AudienceCheckOverrides(
                bypass = false,
                context = JsonValue.wrap("default context"),
                url = null
            )
        )

        assertTrue(result.getOrThrow())

        clock.currentTimeMillis = 1001
        makeResolver(AdditionalAudienceCheckConfig(true, JsonValue.NULL, null))
        result = resolver.resolve(
            deviceInfoProvider = deviceInfoProvider,
            audienceCheckOverrides = AudienceCheckOverrides(
                bypass = false,
                context = JsonValue.wrap("default context"),
                url = "https://test.url"
            )
        )
        assertTrue(result.getOrThrow())

        clock.currentTimeMillis += 2
        result = resolver.resolve(
            deviceInfoProvider = deviceInfoProvider,
            audienceCheckOverrides = AudienceCheckOverrides(
                bypass = false,
                context = JsonValue.wrap("default context"),
                url = null
            )
        )
        assertFalse(result.isSuccess)
    }

    @Test
    public fun testOverridesBypass(): TestResult = runTest {
        makeResolver()

        coEvery { apiClient.resolve(any()) } answers {
            RequestResult(400, null, null, mapOf())
        }

        mockDeviceInfo()

        val result = resolver.resolve(
            deviceInfoProvider = deviceInfoProvider,
            audienceCheckOverrides = AudienceCheckOverrides(
                bypass = true,
                context = JsonValue.NULL,
                url = null
            )
        )
        assertTrue(result.getOrThrow())
    }

    @Test
    public fun testContextDefaultsToConfig(): TestResult = runTest {
        makeResolver()

        coEvery { apiClient.resolve(any()) } answers {
            val request: AdditionalAudienceCheckApiClient.Info = firstArg()
            assertEquals(JsonValue.wrap("remote config context"), request.context)

            RequestResult(
                status = 200,
                value = AdditionalAudienceCheckApiClient.Result(true, 1.seconds),
                body = "{\"is_matched\": true, \"cache_ttl\": 1}",
                headers = mapOf()
            )
        }

        mockDeviceInfo()

        val result = resolver.resolve(
            deviceInfoProvider = deviceInfoProvider,
            audienceCheckOverrides = AudienceCheckOverrides(
                bypass = false,
                context = null,
                url = null
            )
        )
        assertTrue(result.getOrThrow())
        coVerify { apiClient.resolve(any()) }
    }

    @Test
    public fun testReturnsCachedIfAvailable(): TestResult = runTest {
        makeResolver()

        contactId = "existing-contact-id"
        namedUserId = "some user id"

        val channelID = "channel-id"

        coEvery { apiClient.resolve(any()) } answers {
            RequestResult(
                status = 400,
                value = null,
                body = null,
                headers = null
            )
        }

        val cacheKey = "https://test.config:\"default context\":existing-contact-id:channel-id"
        cache.store(
            value = AdditionalAudienceCheckApiClient.Result(isMatched = true, cacheTtl = 10.milliseconds),
            key = cacheKey,
            ttl = 10U
        )

        mockDeviceInfo(channelID, contactId, namedUserId)

        val result = resolver.resolve(
            deviceInfoProvider = deviceInfoProvider,
            audienceCheckOverrides = AudienceCheckOverrides(
                bypass = false,
                context = JsonValue.wrap("default context"),
                url = null
            )
        )
        assertTrue(result.getOrThrow())

        coVerify(exactly = 0) { apiClient.resolve(any()) }
    }

    @Test
    public fun testIsNotCachedOnError(): TestResult = runTest {
        makeResolver()

        contactId = "existing-contact-id"
        namedUserId = "some user id"

        val channelID = "channel-id"

        coEvery { apiClient.resolve(any()) } answers {
            RequestResult(
                status = 400,
                value = null,
                body = null,
                headers = null
            )
        }

        val cacheKey = "https://test.config:\"default context\":existing-contact-id:channel-id"
        assertNull(cache.getCached(cacheKey) { it })

        mockDeviceInfo()

        val result = resolver.resolve(
            deviceInfoProvider = deviceInfoProvider,
            audienceCheckOverrides = AudienceCheckOverrides(
                bypass = false,
                context = JsonValue.wrap("default context"),
                url = null
            )
        )
        assertFalse(result.getOrThrow())
        coVerify { apiClient.resolve(any()) }

        assertNull(cache.getCached(cacheKey) { it })
    }

    @Test
    public fun testThrowsOnServerError(): TestResult = runTest {
        makeResolver()

        coEvery { apiClient.resolve(any()) } answers {
            RequestResult(
                status = 500,
                value = null,
                body = null,
                headers = null
            )
        }

        mockDeviceInfo()

        val result = resolver.resolve(
            deviceInfoProvider = deviceInfoProvider,
            audienceCheckOverrides = AudienceCheckOverrides(
                bypass = false,
                context = JsonValue.wrap("default context"),
                url = null
            )
        )
        assertFalse(result.isSuccess)
    }

    private fun mockDeviceInfo(
        channelId: String = "channelID",
        contactId: String = "contactId",
        namedUserId: String? = null) {
        coEvery { deviceInfoProvider.getChannelId() } returns channelId
        coEvery { deviceInfoProvider.getStableContactInfo() } returns StableContactInfo(
            contactId = contactId,
            namedUserId = namedUserId
        )
    }

    private suspend fun getCached(key: String): AdditionalAudienceCheckApiClient.Result? {
        return cache.getCached(key, converter = AdditionalAudienceCheckApiClient.Result::fromJson)
    }

    private fun makeResolver(config: AdditionalAudienceCheckConfig? = defaultConfig) {
        resolver = AdditionalAudienceCheckerResolver(
            cache = cache,
            apiClient = apiClient,
            config = mockk {
                coEvery { remoteConfig } returns RemoteConfig(
                    iaaConfig = IAAConfig(
                        additionalAudienceCheck = config
                    )
                )
            }
        )
    }
}
