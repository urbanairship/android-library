/* Copyright Airship and Contributors */

package com.urbanairship.analytics.template

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.analytics.CustomEvent
import com.urbanairship.analytics.templates.RetailEventTemplate
import com.urbanairship.json.jsonMapOf
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class RetailEventTemplateTest {

    @Test
    public fun testBrowsed() {
        val event = CustomEvent.newBuilder(RetailEventTemplate.Type.Browsed).build()

        assertEquals("browsed", event.eventName)
        assertEquals("retail", event.templateType)

        assertEquals(jsonMapOf("ltv" to false), event.properties)
    }

    @Test
    public fun testAddedToCart() {
        val event = CustomEvent.newBuilder(RetailEventTemplate.Type.AddedToCart).build()

        assertEquals("added_to_cart", event.eventName)
        assertEquals("retail", event.templateType)

        assertEquals(jsonMapOf("ltv" to false), event.properties)
    }

    @Test
    public fun testStarred() {
        val event = CustomEvent.newBuilder(RetailEventTemplate.Type.Starred).build()

        assertEquals("starred_product", event.eventName)
        assertEquals("retail", event.templateType)

        assertEquals(jsonMapOf("ltv" to false), event.properties)
    }

    @Test
    public fun testPurchased() {
        val event = CustomEvent.newBuilder(RetailEventTemplate.Type.Purchased).build()

        assertEquals("purchased", event.eventName)
        assertEquals("retail", event.templateType)

        assertEquals(jsonMapOf("ltv" to false), event.properties)
    }

    @Test
    public fun testShared() {
        val event = CustomEvent.newBuilder(RetailEventTemplate.Type.Shared(
            source = "some source",
            medium = "some medium"
        )).build()

        assertEquals("shared_product", event.eventName)
        assertEquals("retail", event.templateType)

        assertEquals(jsonMapOf(
            "ltv" to false,
            "source" to "some source",
            "medium" to "some medium"
        ), event.properties)
    }

    @Test
    public fun testSharedEmptyDetails() {
        val event = CustomEvent.newBuilder(RetailEventTemplate.Type.Shared()).build()

        assertEquals("shared_product", event.eventName)
        assertEquals("retail", event.templateType)

        assertEquals(jsonMapOf(
            "ltv" to false,
        ), event.properties)
    }

    @Test
    public fun testWishlist() {
        val event = CustomEvent.newBuilder(RetailEventTemplate.Type.Wishlist(
            id = "some id",
            name = "some name"
        )).build()

        assertEquals("wishlist", event.eventName)
        assertEquals("retail", event.templateType)

        assertEquals(jsonMapOf(
            "ltv" to false,
            "wishlist_id" to "some id",
            "wishlist_name" to "some name"
        ), event.properties)
    }

    @Test
    public fun testWishlistEmptyDetails() {
        val event = CustomEvent.newBuilder(RetailEventTemplate.Type.Wishlist()).build()

        assertEquals("wishlist", event.eventName)
        assertEquals("retail", event.templateType)

        assertEquals(jsonMapOf(
            "ltv" to false,
        ), event.properties)
    }

    @Test
    public fun testProperties() {
        val event = CustomEvent.newBuilder(
            type = RetailEventTemplate.Type.Wishlist(),
            properties = RetailEventTemplate.Properties(
                id = "some id",
                category = "some category",
                type = "some type",
                eventDescription = "some description",
                isLTV = true,
                brand = "some brand",
                isNewItem = true,
                currency = "cred"
            )
        ).build()

        assertEquals("wishlist", event.eventName)
        assertEquals("retail", event.templateType)

        assertEquals(jsonMapOf(
            "id" to "some id",
            "category" to "some category",
            "type" to "some type",
            "description" to "some description",
            "ltv" to true,
            "brand" to "some brand",
            "new_item" to true,
            "currency" to "cred",
        ), event.properties)
    }
}
