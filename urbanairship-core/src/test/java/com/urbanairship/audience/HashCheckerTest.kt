/* Copyright Airship and Contributors */

package com.urbanairship.audience

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.cache.AirshipCache
import com.urbanairship.contacts.StableContactInfo
import com.urbanairship.json.JsonValue
import kotlin.time.Duration.Companion.milliseconds
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class HashCheckerTest {

    private val cache: AirshipCache = mockk()
    private val infoProvider: DeviceInfoProvider = mockk()

    private val checker = HashChecker(cache)

    private var channelId = "some channel"
    private var contactInfo = StableContactInfo(contactId = "match", namedUserId = null)
    private val cachedResults: MutableMap<String, AirshipDeviceAudienceResult> = mutableMapOf()

    @Before
    public fun setup() {
        coEvery { infoProvider.getChannelId() } answers { channelId }
        coEvery { infoProvider.getStableContactInfo() } answers { contactInfo }

        coEvery { cache.getCached<AirshipDeviceAudienceResult>(any(), any()) } answers {
            cachedResults[firstArg()]
        }

        coJustRun { cache.store(any(), any(), any()) }
    }

    @Test
    public fun testStickyCacheMatch(): TestResult = runTest {

        val expected = AirshipDeviceAudienceResult(isMatch = true, reportingMetadata = listOf(JsonValue.wrap("sticky reporting")))

        val result = checker.evaluate(defaultStickyHash(), infoProvider)
        assertEquals(expected, result)

        coVerify { cache.store(expected, "StickyHash:match:some channel:sticky ID", 100.milliseconds) }
    }

    @Test
    public fun testStickyHashFromCacheStillCaches(): TestResult = runTest {
        val expected = AirshipDeviceAudienceResult(isMatch = true, reportingMetadata = listOf(JsonValue.wrap("sticky reporting")))

        var result = checker.evaluate(defaultStickyHash(), infoProvider)
        assertEquals(expected, result)

        coVerify { cache.store(expected, "StickyHash:match:some channel:sticky ID", 100.milliseconds) }
        cachedResults["StickyHash:match:some channel:sticky ID"] = result

        val updatedSticky = defaultStickyHash(sticky = AudienceSticky(
            id = "sticky ID",
            reportingMetadata = JsonValue.wrap("updated sticky reporting"),
            lastAccessTtl = 50.milliseconds
        ))

        result = checker.evaluate(updatedSticky, infoProvider)
        assertEquals(expected, result)

        coVerify { cache.store(expected, "StickyHash:match:some channel:sticky ID", 50.milliseconds) }
    }

    @Test
    public fun testStickyCacheMiss(): TestResult = runTest {
        contactInfo = StableContactInfo("not a match", null)

        cachedResults["StickyHash:match:some channel:sticky ID"] = AirshipDeviceAudienceResult.match

        val expected = AirshipDeviceAudienceResult(isMatch = false, reportingMetadata = listOf(JsonValue.wrap("sticky reporting")))
        assertEquals(expected, checker.evaluate(defaultStickyHash(), infoProvider))

        coVerify(exactly = 0) { cache.getCached<AirshipDeviceAudienceResult>("StickyHash:match:some channel:sticky ID", any()) }
        coVerify { cache.getCached<AirshipDeviceAudienceResult>("StickyHash:not a match:some channel:sticky ID", any()) }
        coVerify { cache.store(expected, "StickyHash:not a match:some channel:sticky ID", 100.milliseconds) }

        assertEquals(
            AirshipDeviceAudienceResult.match,
            cache.getCached("StickyHash:match:some channel:sticky ID", AirshipDeviceAudienceResult::fromJson)
        )
    }

    private fun defaultStickyHash(sticky: AudienceSticky? = null, hasNoSticky: Boolean = false): AudienceHashSelector {
        val resultSticky = if (hasNoSticky) {
            null
        } else {
            sticky ?: AudienceSticky(
                id = "sticky ID",
                reportingMetadata = JsonValue.wrap("sticky reporting"),
                lastAccessTtl = 100.milliseconds
            )
        }

        return AudienceHashSelector(
            hash = AudienceHash(
                prefix = "e66a2371-fecf-41de-9238-cb6c28a86cec:",
                property = HashIdentifiers.CONTACT,
                algorithm = HashAlgorithm.FARM,
                seed = 100,
                numberOfHashBuckets = 16384,
                overrides = null
            ),
            bucket = BucketSubset(min = 11600U, max = 13000U),
            sticky = resultSticky
        )
    }

}
