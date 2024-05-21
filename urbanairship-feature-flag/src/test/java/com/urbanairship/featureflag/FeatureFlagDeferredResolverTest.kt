package com.urbanairship.featureflag

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.cache.AirshipCache
import com.urbanairship.deferred.DeferredRequest
import com.urbanairship.deferred.DeferredResolver
import com.urbanairship.deferred.DeferredResult
import com.urbanairship.deferred.DeferredTriggerContext
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.Clock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class FeatureFlagDeferredResolverTest {
    private lateinit var cache: AirshipCache
    private val clock: Clock = mockk()
    private val coreResolver: DeferredResolver = mockk()

    private lateinit var subject: FlagDeferredResolver
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        cache = spyk(AirshipCache(
            context = ApplicationProvider.getApplicationContext(),
            runtimeConfig = mockk(),
            isPersistent = false,
            appVersion = "1.2.3",
            sdkVersion = "3.2.1",
            clock = clock)
        )

        every { clock.currentTimeMillis() } returns 1

        subject = FlagDeferredResolver(cache, coreResolver, clock, testDispatcher)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun resolverReturnsValidCachedValue(): TestResult = runTest {
        val expected = DeferredFlag.Found(
            DeferredFlagInfo(
                isEligible = true,
                variables = FeatureFlagVariables.Fixed(jsonMapOf("variable" to "value")),
                reportingMetadata = jsonMapOf("reporting" to "context")
            )
        )

        val request = makeDeferredRequest()
        val flagName = "flag-name"
        val info = makeFeatureFlagInfo(flagName, FeatureFlagPayload.DeferredPayload(request.uri))
        val cacheKey = calculateItemId(info, request)

        cache.store(expected, cacheKey, 2U)

        val actual = subject.resolve(request, info)

        assertEquals(expected, actual.getOrNull())
    }

    @Test
    fun testSuccessPath(): TestResult = runTest {
        val expected = DeferredFlag.Found(
            DeferredFlagInfo(
                isEligible = true,
                variables = FeatureFlagVariables.Fixed(
                    jsonMapOf("variable" to "value")
                ),
                reportingMetadata = jsonMapOf("reporting" to "context")
            )
        )

        val request = makeDeferredRequest()
        val flagName = "flag-name"
        val info = makeFeatureFlagInfo(flagName, FeatureFlagPayload.DeferredPayload(request.uri))

        val callbackSlot = slot<(JsonValue) -> Any>()
        coEvery { coreResolver.resolve(request, capture(callbackSlot)) } answers {
            val deferred = callbackSlot.captured.invoke(jsonMapOf(
                "is_eligible" to true,
                "variables" to jsonMapOf(
                    "type" to "fixed",
                    "data" to jsonMapOf(
                        "variable" to "value"
                    )
                ),
                "reporting_metadata" to mapOf(
                    "reporting" to "context"
                )
            ).toJsonValue())
            DeferredResult.Success(deferred)
        }

        val actual = subject.resolve(request, info)
        assertEquals(expected, actual.getOrNull())

        val cacheKey = calculateItemId(info, request)
        coVerify { cache.store(expected, cacheKey, 60000U) }
    }

    @Test
    fun testCacheTtlIsCorrectlyParsed(): TestResult = runTest {
        val request = makeDeferredRequest()
        val flagName = "flag-name"
        val info = makeFeatureFlagInfo(
            flagName,
            FeatureFlagPayload.DeferredPayload(request.uri),
            EvaluationOptions(disallowStaleValues = null, ttl = 123U)
        )

        val callbackSlot = slot<(JsonValue) -> Any>()
        coEvery { coreResolver.resolve(request, capture(callbackSlot)) } answers {
            val deferred = callbackSlot.captured.invoke(jsonMapOf(
                "is_eligible" to true,
                "variables" to jsonMapOf(
                    "type" to "fixed",
                    "data" to jsonMapOf(
                        "variable" to "value"
                    )
                ),
                "reporting_metadata" to mapOf(
                    "reporting" to "context"
                )
            ).toJsonValue())
            DeferredResult.Success(deferred)
        }

        subject.resolve(request, info)

        val cacheKey = calculateItemId(info, request)
        coVerify { cache.store(any(), cacheKey, 60000U) }

        val otherInfo = makeFeatureFlagInfo(
            "another-flag",
            FeatureFlagPayload.DeferredPayload(request.uri),
            EvaluationOptions(disallowStaleValues = null, ttl = 70000U)
        )

        subject.resolve(request, otherInfo)
        coVerify { cache.store(any(), calculateItemId(otherInfo, request), 70000U) }
    }

    @Test
    fun testNotFound(): TestResult = runTest {
        val request = makeDeferredRequest()
        val info = makeFeatureFlagInfo(
            "flag-name",
            FeatureFlagPayload.DeferredPayload(request.uri)
        )

        val callbackSlot = slot<(JsonValue) -> Any>()
        coEvery { coreResolver.resolve(request, capture(callbackSlot)) } answers {
            DeferredResult.NotFound()
        }

        val expected = Result.success(DeferredFlag.NotFound)
        val actual = subject.resolve(request, info)
        assertEquals(expected, actual)
    }

    @Test
    fun testOutOfDateThrows(): TestResult = runTest {
        val request = makeDeferredRequest()
        val info = makeFeatureFlagInfo(
            "flag-name",
            FeatureFlagPayload.DeferredPayload(request.uri)
        )

        val callbackSlot = slot<(JsonValue) -> Any>()
        coEvery { coreResolver.resolve(request, capture(callbackSlot)) } answers {
            DeferredResult.OutOfDate()
        }

        val result = subject.resolve(request, info)
        assertTrue(result.exceptionOrNull() is FeatureFlagEvaluationException.OutOfDate)
    }

    @Test
    fun testRetryError(): TestResult = runTest {
        val expected = DeferredFlag.Found(
            DeferredFlagInfo(
                isEligible = true,
                variables = FeatureFlagVariables.Fixed(
                    jsonMapOf("variable" to "value")
                ),
                reportingMetadata = jsonMapOf("reporting" to "context")
            )
        )

        val request = makeDeferredRequest()
        val info = makeFeatureFlagInfo(
            "flag-name",
            FeatureFlagPayload.DeferredPayload(request.uri)
        )

        val callbackSlot = slot<(JsonValue) -> Any>()
        var returnError = true

        coEvery { coreResolver.resolve(request, capture(callbackSlot)) } answers {
            if (returnError) {
                returnError = false
                DeferredResult.RetriableError<Any>(2L)
            } else {
                val deferred = callbackSlot.captured.invoke(jsonMapOf(
                    "is_eligible" to true,
                    "variables" to jsonMapOf(
                        "type" to "fixed",
                        "data" to jsonMapOf(
                            "variable" to "value"
                        )
                    ),
                    "reporting_metadata" to mapOf(
                        "reporting" to "context"
                    )
                ).toJsonValue())
                DeferredResult.Success(deferred)
            }
        }

        val actual = subject.resolve(request, info)
        assertEquals(expected, actual.getOrNull())

        val cacheKey = calculateItemId(info, request)
        coVerify { cache.store(expected, cacheKey, 60000U) }
    }

    @Test
    fun testRetryOnlyOnce(): TestResult = runTest {
        val expected = DeferredFlag.Found(
            DeferredFlagInfo(
                isEligible = true,
                variables = FeatureFlagVariables.Fixed(
                    jsonMapOf("variable" to "value")
                ),
                reportingMetadata = jsonMapOf("reporting" to "context")
            )
        )

        val request = makeDeferredRequest()
        val info = makeFeatureFlagInfo(
            "flag-name",
            FeatureFlagPayload.DeferredPayload(request.uri)
        )

        val callbackSlot = slot<(JsonValue) -> Any>()
        var counter = 0
        coEvery { coreResolver.resolve(request, capture(callbackSlot)) } answers {
            if (counter < 2) {
                counter += 1
                DeferredResult.RetriableError<Any>(2L)
            } else {
                val deferred = callbackSlot.captured.invoke(jsonMapOf(
                    "is_eligible" to true,
                    "variables" to jsonMapOf(
                        "type" to "fixed",
                        "data" to jsonMapOf(
                            "variable" to "value"
                        )
                    ),
                    "reporting_metadata" to mapOf(
                        "reporting" to "context"
                    )
                ).toJsonValue())
                DeferredResult.Success(deferred)
            }
        }

        val result = subject.resolve(request, info)
        assertTrue(result.exceptionOrNull() is FeatureFlagEvaluationException.ConnectionError)
    }

    @Test
    fun waitForResolving(): TestResult = runTest {
        val expected = DeferredFlag.Found(
            DeferredFlagInfo(
                isEligible = true,
                variables = FeatureFlagVariables.Fixed(
                    jsonMapOf("variable" to "value")
                ),
                reportingMetadata = jsonMapOf("reporting" to "context")
            )
        )

        val request = makeDeferredRequest()
        val info = makeFeatureFlagInfo(
            "flag-name",
            FeatureFlagPayload.DeferredPayload(request.uri)
        )

        val callbackSlot = slot<(JsonValue) -> Any>()
        val response: MutableStateFlow<JsonValue?> = MutableStateFlow(null)
        var resolveCount = 0

        coEvery { coreResolver.resolve(request, capture(callbackSlot)) } coAnswers {
            val result = response.filterNotNull().first()
            resolveCount += 1
            DeferredResult.Success(callbackSlot.captured.invoke(result))
        }

        val first = async {
            subject.resolve(request, info)
        }
        val second = async {
            subject.resolve(request, info)
        }

        var valueFirst = withTimeoutOrNull(100, { first.await() })
        var valueSecond = withTimeoutOrNull(100, { second.await() })

        assertNull(valueFirst)
        assertNull(valueSecond)

        response.value =
            jsonMapOf(
                "is_eligible" to true,
                "variables" to jsonMapOf(
                    "type" to "fixed",
                    "data" to jsonMapOf(
                        "variable" to "value"
                    )
                ),
                "reporting_metadata" to mapOf(
                    "reporting" to "context"
                )
            ).toJsonValue()


        subject.resolve(request, info)

        valueFirst = withTimeoutOrNull(100, { first.await() })
        valueSecond = withTimeoutOrNull(100, { second.await() })

        assertEquals(expected, valueFirst?.getOrNull())
        assertEquals(expected, valueSecond?.getOrNull())

        assertEquals(1, resolveCount)
    }

    @Test
    fun reResolveOnFinished(): TestResult = runTest(UnconfinedTestDispatcher()) {
        val expected = DeferredFlag.Found(
            DeferredFlagInfo(
                isEligible = true,
                variables = FeatureFlagVariables.Fixed(
                    jsonMapOf("variable" to "value")
                ),
                reportingMetadata = jsonMapOf("reporting" to "context")
            )
        )

        val request = makeDeferredRequest()
        val info = makeFeatureFlagInfo(
            "flag-name",
            FeatureFlagPayload.DeferredPayload(request.uri)
        )

        val callbackSlot = slot<(JsonValue) -> Any>()
        val response: MutableStateFlow<JsonValue?> = MutableStateFlow(null)
        var resolveCount = 0

        coEvery { coreResolver.resolve(request, capture(callbackSlot)) } coAnswers {
            val result = response.filterNotNull().first()
            resolveCount += 1
            DeferredResult.Success(callbackSlot.captured.invoke(result))
        }

        val first = async { subject.resolve(request, info) }

        var valueFirst = withTimeoutOrNull(100, { first.await() })

        assertNull(valueFirst)

        response.value = jsonMapOf(
            "is_eligible" to true,
            "variables" to jsonMapOf(
                "type" to "fixed",
                "data" to jsonMapOf(
                    "variable" to "value"
                )
            ),
            "reporting_metadata" to mapOf(
                "reporting" to "context"
            )
        ).toJsonValue()



        subject.resolve(request, info)

        valueFirst = withTimeoutOrNull(100, { first.await() })

        assertEquals(expected, valueFirst?.getOrNull())
        assertEquals(1, resolveCount)

        every { clock.currentTimeMillis() } returns 70000

        val next = subject.resolve(request, info)
        assertEquals(expected, next.getOrNull())
        assertEquals(2, resolveCount)
    }

    private fun makeDeferredRequest(): DeferredRequest {
        return DeferredRequest(
            uri = Uri.parse("https://example.co"),
            channelId = "channel-id",
            contactId = "contact-id",
            triggerContext = DeferredTriggerContext("type", 1.0, JsonValue.wrap(1)),
            locale = Locale.ENGLISH,
            notificationOptIn = false,
            appVersionName = "1.2.3",
            sdkVersion = "3.2.1"
        )
    }

    private fun makeFeatureFlagInfo(
        name: String,
        payload: FeatureFlagPayload,
        evaluationOptions: EvaluationOptions? = null
    ): FeatureFlagInfo {
        return FeatureFlagInfo(
            id = "test-id",
            created = 1L,
            lastUpdated = 2L,
            name = name,
            reportingContext = jsonMapOf(),
            audience = null,
            timeCriteria = null,
            payload = payload,
            evaluationOptions = evaluationOptions
        )
    }

    private fun calculateItemId(info: FeatureFlagInfo, request: DeferredRequest): String {
        return listOf(info.name, info.id, info.lastUpdated, request.contactId ?: "", request.uri.toString())
            .joinToString(":")
    }
}
