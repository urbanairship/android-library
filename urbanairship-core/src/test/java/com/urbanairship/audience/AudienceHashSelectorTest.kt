package com.urbanairship.audience

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AudienceHashSelectorTest {

    @Test
    public fun testHash() {
        val selectorGenerator: (Int, Int) -> AudienceHashSelector = { min: Int, max: Int ->
            val hashDefinition = """
            {
                "audience_hash":{
                   "hash_prefix":"686f2c15-cf8c-47a6-ae9f-e749fc792a9d:",
                   "num_hash_buckets":16384,
                   "hash_identifier":"contact",
                   "hash_algorithm":"farm_hash"
                },
                "audience_subset":{
                   "min_hash_bucket":$min,
                   "max_hash_bucket":$max
                }
             }
            """

            requireNotNull(AudienceHashSelector.fromJson(JsonValue.parseString(hashDefinition).requireMap()))
        }

        // contactId = 9908

        assertTrue(
            selectorGenerator(9908, 9908).evaluate("", "contactId")
        )

        assertTrue(
            selectorGenerator(9907, 9908).evaluate("", "contactId")
        )

        assertTrue(
            selectorGenerator(9908, 9909).evaluate("", "contactId")
        )

        assertFalse(
            selectorGenerator(9907, 9907).evaluate("", "contactId")
        )

        assertFalse(
            selectorGenerator(9909, 9909).evaluate("", "contactId")
        )
    }

    @Test
    public fun testHashSticky() {
        val selectorGenerator: (Int, Int) -> AudienceHashSelector = { min: Int, max: Int ->
            val hashDefinition = """
            {
                "audience_hash":{
                   "hash_prefix":"686f2c15-cf8c-47a6-ae9f-e749fc792a9d:",
                   "num_hash_buckets":16384,
                   "hash_identifier":"contact",
                   "hash_algorithm":"farm_hash",
                   "hash_seed": 100,
                   "hash_identifier_overrides": {
                       "foo": "bar"
                     }
                },
                "audience_subset":{
                   "min_hash_bucket":$min,
                   "max_hash_bucket":$max
                },
                "sticky": {
                    "id": "test-id",
                    "reporting_metadata": "test",
                    "last_access_ttl": 123
                }
             }
            """

            requireNotNull(AudienceHashSelector.fromJson(JsonValue.parseString(hashDefinition).requireMap()))
        }

        // contactId = 9908

        assertTrue(
            selectorGenerator(9908, 9908).evaluate("", "contactId")
        )

        assertTrue(
            selectorGenerator(9907, 9908).evaluate("", "contactId")
        )

        assertTrue(
            selectorGenerator(9908, 9909).evaluate("", "contactId")
        )

        assertFalse(
            selectorGenerator(9907, 9907).evaluate("", "contactId")
        )

        assertFalse(
            selectorGenerator(9909, 9909).evaluate("", "contactId")
        )

        val selector = selectorGenerator(1, 100)
        assertEquals(
            selector,
            AudienceHashSelector(
                hash = AudienceHash(
                    prefix = "686f2c15-cf8c-47a6-ae9f-e749fc792a9d:",
                    property = HashIdentifiers.CONTACT,
                    algorithm = HashAlgorithm.FARM,
                    seed = 100L,
                    numberOfHashBuckets = 16384,
                    overrides = jsonMapOf("foo" to "bar")
                ),
                bucket = BucketSubset(1U, 100U),
                sticky = AudienceSticky(
                    id = "test-id",
                    reportingMetadata = JsonValue.wrap("test"),
                    lastAccessTtl = 123.milliseconds
                )
            )
        )
    }

    // contactId "contactId" hashes into bucket 9908 (see testHash).
    private val hashJson = """
        "audience_hash":{
           "hash_prefix":"686f2c15-cf8c-47a6-ae9f-e749fc792a9d:",
           "num_hash_buckets":16384,
           "hash_identifier":"contact",
           "hash_algorithm":"farm_hash"
        }
    """.trimIndent()

    private fun selector(json: String): AudienceHashSelector =
        requireNotNull(AudienceHashSelector.fromJson(JsonValue.parseString(json).requireMap()))

    private fun schedule(start: Instant?, end: Instant?): TimeSpan =
        TimeSpan(startTimestamp = start, endTimestamp = end)

    @Test
    public fun testStaticOverrideActive() {
        // Base excludes 9908, static override includes it while active.
        val selector = selector(
            """
            {
                $hashJson,
                "audience_subset": { "min_hash_bucket": 0, "max_hash_bucket": 0 },
                "audience_subset_overrides": [
                    {
                        "type": "static",
                        "start_timestamp": 1000,
                        "end_timestamp": 2000,
                        "audience_subset": { "min_hash_bucket": 9908, "max_hash_bucket": 9908 }
                    }
                ]
            }
            """
        )

        assertTrue(selector.evaluate("", "contactId", now = Instant.ofEpochMilli(1500)))
        // After the window (end is exclusive) -> falls back to base (0..0), no match.
        assertFalse(selector.evaluate("", "contactId", now = Instant.ofEpochMilli(3000)))
        // Before the window as well.
        assertFalse(selector.evaluate("", "contactId", now = Instant.ofEpochMilli(500)))
    }

    @Test
    public fun testLinearRampInterpolation() {
        val override = AudienceSubsetOverride.LinearRamp(
            schedule = schedule(start = Instant.ofEpochMilli(1000), end = Instant.ofEpochMilli(2000)),
            subsetStart = BucketSubset(min = 100U, max = 10000U),
            subsetEnd = BucketSubset(min = 200U, max = 20000U)
        )

        // Start
        assertEquals(BucketSubset(100U, 10000U), override.resolveBucket(Instant.ofEpochMilli(1000)))
        // Midpoint
        assertEquals(BucketSubset(150U, 15000U), override.resolveBucket(Instant.ofEpochMilli(1500)))
        // End (interpolation clamps to end subset)
        assertEquals(BucketSubset(200U, 20000U), override.resolveBucket(Instant.ofEpochMilli(2000)))
        // Clamped before start
        assertEquals(BucketSubset(100U, 10000U), override.resolveBucket(Instant.ofEpochMilli(0)))
        // Clamped after end
        assertEquals(BucketSubset(200U, 20000U), override.resolveBucket(Instant.ofEpochMilli(9999)))
    }

    @Test
    public fun testLinearRampInvertedSubsetDoesNotUnderflow() {
        // subsetStart > subsetEnd. Casting to Double before subtraction avoids unsigned underflow.
        val override = AudienceSubsetOverride.LinearRamp(
            schedule = schedule(start = Instant.ofEpochMilli(1000), end = Instant.ofEpochMilli(2000)),
            subsetStart = BucketSubset(min = 0U, max = 20000U),
            subsetEnd = BucketSubset(min = 0U, max = 10000U)
        )

        assertEquals(BucketSubset(0U, 20000U), override.resolveBucket(Instant.ofEpochMilli(1000)))
        assertEquals(BucketSubset(0U, 15000U), override.resolveBucket(Instant.ofEpochMilli(1500)))
        assertEquals(BucketSubset(0U, 10000U), override.resolveBucket(Instant.ofEpochMilli(2000)))
    }

    @Test
    public fun testLinearRampActivationViaEvaluate() {
        // At start, max=9900 excludes 9908; ramps up to include it by midpoint.
        val selector = selector(
            """
            {
                $hashJson,
                "audience_subset": { "min_hash_bucket": 0, "max_hash_bucket": 0 },
                "audience_subset_overrides": [
                    {
                        "type": "linear_ramp",
                        "start_timestamp": 1000,
                        "end_timestamp": 2000,
                        "audience_subset_start": { "min_hash_bucket": 0, "max_hash_bucket": 9900 },
                        "audience_subset_end": { "min_hash_bucket": 0, "max_hash_bucket": 9910 }
                    }
                ]
            }
            """
        )

        // t = 0 -> max 9900 -> 9908 excluded.
        assertFalse(selector.evaluate("", "contactId", now = Instant.ofEpochMilli(1000)))
        // t = 0.5 -> max 9905 -> 9908 excluded.
        assertFalse(selector.evaluate("", "contactId", now = Instant.ofEpochMilli(1500)))
        // t = 0.9 -> max 9909 -> 9908 included.
        assertTrue(selector.evaluate("", "contactId", now = Instant.ofEpochMilli(1900)))
    }

    @Test
    public fun testFirstMatchingOverrideWins() {
        // Two overlapping active overrides. The first (non-matching) one should win.
        val selector = selector(
            """
            {
                $hashJson,
                "audience_subset": { "min_hash_bucket": 9908, "max_hash_bucket": 9908 },
                "audience_subset_overrides": [
                    {
                        "type": "static",
                        "start_timestamp": 1000,
                        "end_timestamp": 2000,
                        "audience_subset": { "min_hash_bucket": 0, "max_hash_bucket": 0 }
                    },
                    {
                        "type": "static",
                        "start_timestamp": 1000,
                        "end_timestamp": 2000,
                        "audience_subset": { "min_hash_bucket": 9908, "max_hash_bucket": 9908 }
                    }
                ]
            }
            """
        )

        // First override subset (0..0) is used -> 9908 excluded -> no match.
        assertFalse(selector.evaluate("", "contactId", now = Instant.ofEpochMilli(1500)))
    }

    @Test
    public fun testFirstActiveOverrideWinsSkippingInactive() {
        // First override is inactive at the eval date; the second (active) one is used.
        val selector = selector(
            """
            {
                $hashJson,
                "audience_subset": { "min_hash_bucket": 0, "max_hash_bucket": 0 },
                "audience_subset_overrides": [
                    {
                        "type": "static",
                        "start_timestamp": 0,
                        "end_timestamp": 1000,
                        "audience_subset": { "min_hash_bucket": 0, "max_hash_bucket": 0 }
                    },
                    {
                        "type": "static",
                        "start_timestamp": 1000,
                        "end_timestamp": 2000,
                        "audience_subset": { "min_hash_bucket": 9908, "max_hash_bucket": 9908 }
                    }
                ]
            }
            """
        )

        // Second override subset (9908..9908) contains 9908 -> match.
        assertTrue(selector.evaluate("", "contactId", now = Instant.ofEpochMilli(1500)))
    }

    @Test
    public fun testFallbackToBaseWhenNoOverrideActive() {
        val selector = selector(
            """
            {
                $hashJson,
                "audience_subset": { "min_hash_bucket": 9908, "max_hash_bucket": 9908 },
                "audience_subset_overrides": [
                    {
                        "type": "static",
                        "start_timestamp": 1000,
                        "end_timestamp": 2000,
                        "audience_subset": { "min_hash_bucket": 0, "max_hash_bucket": 0 }
                    }
                ]
            }
            """
        )

        // Evaluated outside any override window -> base bucket (9908..9908) contains 9908.
        assertTrue(selector.evaluate("", "contactId", now = Instant.ofEpochMilli(5000)))
    }

    @Test
    public fun testMissingOverridesFieldUnchanged() {
        val selector = selector(
            """
            {
                $hashJson,
                "audience_subset": { "min_hash_bucket": 9908, "max_hash_bucket": 9908 }
            }
            """
        )

        assertEquals(null, selector.overrides)
        assertTrue(selector.evaluate("", "contactId", now = Instant.ofEpochMilli(1500)))
        assertTrue(selector.evaluate("", "contactId"))
    }

    @Test
    public fun testJsonWithOverrides() {
        val json = """
            {
                $hashJson,
                "audience_subset": { "min_hash_bucket": 10, "max_hash_bucket": 100 },
                "audience_subset_overrides": [
                    {
                        "type": "linear_ramp",
                        "start_timestamp": 1000000,
                        "end_timestamp": 2000000,
                        "audience_subset_start": { "min_hash_bucket": 0, "max_hash_bucket": 100 },
                        "audience_subset_end": { "min_hash_bucket": 0, "max_hash_bucket": 1000 }
                    },
                    {
                        "type": "static",
                        "start_timestamp": 2000000,
                        "audience_subset": { "min_hash_bucket": 0, "max_hash_bucket": 1000 }
                    }
                ]
            }
        """

        val decoded = selector(json)

        assertEquals(
            listOf(
                AudienceSubsetOverride.LinearRamp(
                    schedule = schedule(start = Instant.ofEpochMilli(1000000), end = Instant.ofEpochMilli(2000000)),
                    subsetStart = BucketSubset(0U, 100U),
                    subsetEnd = BucketSubset(0U, 1000U)
                ),
                AudienceSubsetOverride.Static(
                    schedule = schedule(start = Instant.ofEpochMilli(2000000), end = null),
                    subset = BucketSubset(0U, 1000U)
                )
            ),
            decoded.overrides
        )

        // Overrides round-trip through JSON.
        assertEquals(decoded.overrides, selector(decoded.toJsonValue().toString()).overrides)
    }

    @Test
    public fun testLinearRampRequiresBothTimestamps() {
        // A linear_ramp missing end_timestamp is dropped during parsing.
        val selector = selector(
            """
            {
                $hashJson,
                "audience_subset": { "min_hash_bucket": 10, "max_hash_bucket": 100 },
                "audience_subset_overrides": [
                    {
                        "type": "linear_ramp",
                        "start_timestamp": 1000000,
                        "audience_subset_start": { "min_hash_bucket": 0, "max_hash_bucket": 100 },
                        "audience_subset_end": { "min_hash_bucket": 0, "max_hash_bucket": 1000 }
                    }
                ]
            }
            """
        )

        assertEquals(emptyList<AudienceSubsetOverride>(), selector.overrides)
    }
}
