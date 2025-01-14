package com.urbanairship.audience

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
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
}
