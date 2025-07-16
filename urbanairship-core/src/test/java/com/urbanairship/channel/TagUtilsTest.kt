/* Copyright Airship and Contributors */
package com.urbanairship.channel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.channel.TagUtils.convertToTagsMap
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class TagUtilsTest {

    /**
     * Test converting a JsonValue to a tags map
     */
    @Test
    public fun testConvertToTagsMap() {
        val tags = setOf("tag1", "tag2", "tag3")
        val tagGroups = mapOf(
            "tagGroup" to tags
        )

        val jsonValue = JsonValue.wrap(tagGroups)

        val map = convertToTagsMap(jsonValue)
        assertEquals("Map size mismatch", map?.size, tagGroups.size)
        assertTrue("Value mismatch", map?.containsValue(tags) == true)
    }

    /**
     * Test converting a null and non-JsonMap returns null.
     */
    @Test
    public fun testConvertNullToTagsMap() {
        assertNull(convertToTagsMap(null))

        val jsonString = JsonValue.parseString("non-JsonMap")
        assertNull(convertToTagsMap(jsonString))
    }
}
