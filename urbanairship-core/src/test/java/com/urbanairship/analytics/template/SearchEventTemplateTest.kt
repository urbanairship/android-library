/* Copyright Airship and Contributors */

package com.urbanairship.analytics.template

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.analytics.CustomEvent
import com.urbanairship.analytics.templates.SearchEventTemplate
import com.urbanairship.json.jsonMapOf
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class SearchEventTemplateTest {

    @Test
    public fun testSearch() {
        val event = CustomEvent.newBuilder(SearchEventTemplate.Type.SEARCH).build()

        assertEquals("search", event.eventName)
        assertEquals("search", event.templateType)
        assertEquals(jsonMapOf("ltv" to false), event.properties)
    }

    @Test
    public fun testProperties() {
        val event = CustomEvent.newBuilder(
            type = SearchEventTemplate.Type.SEARCH,
            properties = SearchEventTemplate.Properties(
                id = "some id",
                category = "some category",
                type = "some type",
                isLTV = true,
                query = "some query",
                totalResults = 20
            )
        ).build()

        assertEquals("search", event.eventName)
        assertEquals("search", event.templateType)
        assertEquals(jsonMapOf(
            "id" to "some id",
            "category" to "some category",
            "type" to "some type",
            "ltv" to true,
            "query" to "some query",
            "total_results" to 20
        ), event.properties)
    }
}
