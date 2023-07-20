package com.urbanairship.audience

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
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
}
