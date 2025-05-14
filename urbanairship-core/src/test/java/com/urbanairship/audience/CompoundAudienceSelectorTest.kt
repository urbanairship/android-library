/* Copyright Airship and Contributors */

package com.urbanairship.audience

import androidx.core.os.persistableBundleOf
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.cache.AirshipCache
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class CompoundAudienceSelectorTest {

    private val infoProvider: DeviceInfoProvider = mockk(relaxed = true)
    private val hashChecker = HashChecker(
        cache = mockk(relaxed = true)
    )

    @Before
    public fun setUp() {
        every { infoProvider.installDateMilliseconds } returns 0
    }

    @Test
    public fun testParsing() {
        listOf(
            Pair(
                defaultAudience(),
                "{\"type\":\"atomic\", \"audience\":{\"new_user\":true}}"
            ),
            Pair(
                CompoundAudienceSelector.Not(defaultAudience()),
                "{\"type\":\"not\", \"selector\": {\"type\":\"atomic\", \"audience\":{\"new_user\":true}}}"
            ),
            Pair(
                CompoundAudienceSelector.And(
                    listOf(
                        defaultAudience(),
                        defaultAudience(false),
                    )
                ),
                "{\"type\":\"and\", \"selectors\": [{\"type\":\"atomic\", \"audience\":{\"new_user\":true}},{\"type\":\"atomic\", \"audience\":{\"new_user\":false}}]}"
            ),
            Pair(
                CompoundAudienceSelector.And(listOf()),
                "{\"type\":\"and\", \"selectors\": []}"
            ),
            Pair(
                CompoundAudienceSelector.Or(
                    listOf(
                        defaultAudience(),
                        defaultAudience(false),
                    )
                ),
                "{\"type\":\"or\", \"selectors\": [{\"type\":\"atomic\", \"audience\":{\"new_user\":true}},{\"type\":\"atomic\", \"audience\":{\"new_user\":false}}]}"
            ),
            Pair(
                CompoundAudienceSelector.Or(listOf()),
                "{\"type\":\"or\", \"selectors\": []}"
            ),
        ).forEach { verify(it.second, it.first) }
    }

    @Test
    public fun testEvaluation(): TestResult = runTest {
        listOf(
            Pair(defaultAudience(), true),
            Pair(defaultAudience(false), false),
            Pair(CompoundAudienceSelector.Not(defaultAudience()), false),
            Pair(CompoundAudienceSelector.And(listOf(
                defaultAudience(true),
                defaultAudience(false)
            )), false),
            Pair(CompoundAudienceSelector.And(listOf(
                defaultAudience(true),
                defaultAudience(true)
            )), true),
            Pair(CompoundAudienceSelector.Or(listOf(
                defaultAudience(true),
                defaultAudience(false)
            )), true),
            Pair(CompoundAudienceSelector.Or(listOf(
                defaultAudience(true),
                defaultAudience(true)
            )), true),
            Pair(CompoundAudienceSelector.Or(listOf(
                defaultAudience(false),
                defaultAudience(false)
            )), false),
        ).forEach { (audience, expected) ->
            assertEquals(audience.evaluate(0, infoProvider, hashChecker), AirshipDeviceAudienceResult(expected))
        }
    }

    @Test
    public fun testOrMissFirstSelector(): TestResult = runTest {
        val requiresAnalytics = AudienceSelector.newBuilder().setRequiresAnalytics(true).build()
        val doesNotRequireAnalytics = AudienceSelector.newBuilder().setRequiresAnalytics(false).build()

        every { infoProvider.analyticsEnabled } returns false

        val result = CompoundAudienceSelector.Or(
            listOf(
                CompoundAudienceSelector.Atomic(requiresAnalytics),
                CompoundAudienceSelector.Atomic(doesNotRequireAnalytics)
            )
        ).evaluate(0, infoProvider, hashChecker)

        assertTrue(result.isMatch)
    }

    @Test
    public fun testEmptyOr(): TestResult = runTest {
        val selector = CompoundAudienceSelector.Or(emptyList())
        val result = selector.evaluate(0, infoProvider, hashChecker)
        assertFalse(result.isMatch)
    }

    @Test
    public fun testEmptyAnd(): TestResult = runTest {
        val selector = CompoundAudienceSelector.And(emptyList())
        val result = selector.evaluate(0, infoProvider, hashChecker)
        assertTrue(result.isMatch)
    }

    @Test
    public fun testCombine() {
        val deviceSelector = AudienceSelector
            .newBuilder()
            .setNewUser(true)
            .build()

        val compoundAudienceSelector = defaultAudience(false)
        val combined = CompoundAudienceSelector.combine(compoundAudienceSelector, deviceSelector)

        val expected = CompoundAudienceSelector.And(
            listOf(
                defaultAudience(true),
                defaultAudience(false)
            )
        )

        assertEquals(expected, combined)
    }

    private fun defaultAudience(newUser: Boolean = true) = CompoundAudienceSelector.Atomic(
        AudienceSelector
            .newBuilder()
            .setNewUser(newUser)
            .build()
    )

    private fun verify(json: String, expected: CompoundAudienceSelector) {
        val fromJson = CompoundAudienceSelector.fromJson(JsonValue.parseString(json))
        assertEquals(fromJson, expected)

        val roundTrip = CompoundAudienceSelector.fromJson(fromJson.toJsonValue())
        assertEquals(roundTrip, expected)
    }
}
