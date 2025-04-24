/* Copyright Airship and Contributors */

package com.urbanairship.analytics.template

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.analytics.CustomEvent
import com.urbanairship.analytics.templates.MediaEventTemplate
import com.urbanairship.json.jsonMapOf
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class MediaEventTemplateTest {

    @Test
    public fun testBrowsed() {
        val event = CustomEvent.newBuilder(MediaEventTemplate.Type.Browsed).build()

        assertEquals("browsed_content", event.eventName)
        assertEquals("media", event.templateType)
        assertEquals(jsonMapOf("ltv" to false), event.properties)
    }

    @Test
    public fun testConsumed() {
        val event = CustomEvent.newBuilder(MediaEventTemplate.Type.Consumed).build()

        assertEquals("consumed_content", event.eventName)
        assertEquals("media", event.templateType)
        assertEquals(jsonMapOf("ltv" to false), event.properties)
    }

    @Test
    public fun testShared() {
        val event = CustomEvent.newBuilder(MediaEventTemplate.Type.Shared(
            source = "some source",
            medium = "some medium"
        )).build()

        assertEquals("shared_content", event.eventName)
        assertEquals("media", event.templateType)
        assertEquals(jsonMapOf(
            "ltv" to false,
            "source" to "some source",
            "medium" to "some medium"
        ), event.properties)
    }

    @Test
    public fun testSharedEmptyDetails() {
        val event = CustomEvent.newBuilder(MediaEventTemplate.Type.Shared()).build()

        assertEquals("shared_content", event.eventName)
        assertEquals("media", event.templateType)
        assertEquals(jsonMapOf(
            "ltv" to false,
        ), event.properties)
    }

    @Test
    public fun testStarred() {
        val event = CustomEvent.newBuilder(MediaEventTemplate.Type.Starred).build()

        assertEquals("starred_content", event.eventName)
        assertEquals("media", event.templateType)
        assertEquals(jsonMapOf("ltv" to false), event.properties)
    }

    @Test
    public fun testProperties() {
        val event = CustomEvent.newBuilder(
            type = MediaEventTemplate.Type.Shared(
                source = "some source",
                medium = "some medium"
            ),
            properties = MediaEventTemplate.Properties(
                id = "some id",
                category = "some category",
                type = "some type",
                eventDescription = "some description",
                isLTV = true,
                author = "some author",
                publishedDate = "date-as-string",
                isFeature = true
            )
        ).build()

        assertEquals("shared_content", event.eventName)
        assertEquals("media", event.templateType)
        assertEquals(jsonMapOf(
            "id" to "some id",
            "category" to "some category",
            "type" to "some type",
            "description" to "some description",
            "ltv" to true,
            "author" to "some author",
            "published_date" to "date-as-string",
            "feature" to true,
            "source" to "some source",
            "medium" to "some medium"
        ), event.properties)
    }
}
