package com.urbanairship.audience

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class VariantAudienceTest {

    // Same fixture as AudienceHashSelectorTest.testHash: this prefix/contactID
    // combination resolves to bucket 9908 of 16384 via farm hash.
    private fun variantAudience(
        audienceSubset: Pair<Int, Int>,
        holdoutSubset: Pair<Int, Int>? = null
    ): VariantAudience {
        val holdoutJson = holdoutSubset?.let {
            """, "holdout_subset": { "min_hash_bucket": ${it.first}, "max_hash_bucket": ${it.second} }"""
        } ?: ""

        val json = """
            {
                "audience_hash": {
                    "hash_prefix": "686f2c15-cf8c-47a6-ae9f-e749fc792a9d:",
                    "num_hash_buckets": 16384,
                    "hash_identifier": "contact",
                    "hash_algorithm": "farm_hash"
                },
                "audience_subset": { "min_hash_bucket": ${audienceSubset.first}, "max_hash_bucket": ${audienceSubset.second} }
                $holdoutJson
            }
        """.trimIndent()

        return requireNotNull(VariantAudience.fromJson(JsonValue.parseString(json).requireMap()))
    }

    @Test
    public fun testResolveMatched() {
        val variantAudience = variantAudience(audienceSubset = 9908 to 9908)
        assertEquals(VariantAudience.Outcome.MATCHED, variantAudience.resolve("", "contactId"))
    }

    @Test
    public fun testResolveMatchedTakesPrecedenceOverHoldout() {
        // Overlapping subsets shouldn't happen in practice, but a bucket that lands in both
        // must resolve to MATCHED, never HOLDOUT.
        val variantAudience = variantAudience(
            audienceSubset = 9908 to 9908,
            holdoutSubset = 9908 to 9908
        )
        assertEquals(VariantAudience.Outcome.MATCHED, variantAudience.resolve("", "contactId"))
    }

    @Test
    public fun testResolveHoldout() {
        val variantAudience = variantAudience(
            audienceSubset = 0 to 0,
            holdoutSubset = 9908 to 9908
        )
        assertEquals(VariantAudience.Outcome.HOLDOUT, variantAudience.resolve("", "contactId"))
    }

    @Test
    public fun testResolveVariantMissWithHoldoutArm() {
        val variantAudience = variantAudience(
            audienceSubset = 0 to 0,
            holdoutSubset = 1 to 1
        )
        assertEquals(VariantAudience.Outcome.VARIANT_MISS, variantAudience.resolve("", "contactId"))
    }

    @Test
    public fun testResolveVariantMissWithoutHoldoutArm() {
        val variantAudience = variantAudience(audienceSubset = 0 to 0)
        assertEquals(VariantAudience.Outcome.VARIANT_MISS, variantAudience.resolve("", "contactId"))
    }

    @Test
    public fun testIsDisplaySkipped() {
        assertFalse(VariantAudience.Outcome.MATCHED.isDisplaySkipped)
        assertTrue(VariantAudience.Outcome.HOLDOUT.isDisplaySkipped)
        assertTrue(VariantAudience.Outcome.VARIANT_MISS.isDisplaySkipped)
    }
}
