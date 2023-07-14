package com.urbanairship.audience

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonException
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class DeviceTagSelectorTest {

    @Test
    @Throws(JsonException::class)
    public fun testJson() {
        val original = DeviceTagSelector.or(
            DeviceTagSelector.and(
                DeviceTagSelector.tag("some-tag"), DeviceTagSelector.not(DeviceTagSelector.tag("not-tag"))
            ), DeviceTagSelector.tag("some-other-tag")
        )
        val fromJson = DeviceTagSelector.fromJson(original.toJsonValue())
        assertEquals(original, fromJson)
        assertEquals(original.hashCode(), fromJson.hashCode())
    }

    @Test
    public fun testSelector() {
        val selector = DeviceTagSelector.or(
            DeviceTagSelector.and(
                DeviceTagSelector.tag("some-tag"), DeviceTagSelector.not(DeviceTagSelector.tag("not-tag"))
            ), DeviceTagSelector.tag("some-other-tag")
        )
        val tags: MutableList<String> = ArrayList()

        // Empty list
        assertFalse(selector.apply(tags))
        tags.add("some-tag")
        // Contains "some-tag" and not "not-tag"
        assertTrue(selector.apply(tags))
        tags.add("not-tag")
        // Contains "some-tag" and "not-tag"
        assertFalse(selector.apply(tags))
        tags.add("some-other-tag")
        // Contains "some-other-tag"
        assertTrue(selector.apply(tags))
    }
}
