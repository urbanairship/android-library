/* Copyright Airship and Contributors */

package com.urbanairship.analytics.template

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.analytics.CustomEvent
import com.urbanairship.analytics.templates.AccountEventTemplate
import com.urbanairship.json.jsonMapOf
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AccountEventTemplateTest {

    @Test
    public fun testRegistered() {
        val event = CustomEvent.newBuilder(AccountEventTemplate.Type.REGISTERED).build()

        assertEquals("registered_account", event.eventName)
        assertEquals("account", event.templateType)
        assertEquals(jsonMapOf("ltv" to false), event.properties)
    }

    @Test
    public fun testLoggedIn() {
        val event = CustomEvent.newBuilder(AccountEventTemplate.Type.LOGGED_IN).build()

        assertEquals("logged_in", event.eventName)
        assertEquals("account", event.templateType)
        assertEquals(jsonMapOf("ltv" to false), event.properties)
    }

    @Test
    public fun testLoggedOut() {
        val event = CustomEvent.newBuilder(AccountEventTemplate.Type.LOGGED_OUT).build()

        assertEquals("logged_out", event.eventName)
        assertEquals("account", event.templateType)
        assertEquals(jsonMapOf("ltv" to false), event.properties)
    }

    @Test
    public fun testProperties() {
        val event = CustomEvent.newBuilder(
            type = AccountEventTemplate.Type.LOGGED_OUT,
            properties = AccountEventTemplate.Properties(
                category = "some category",
                type = "some type",
                isLTV = true,
                userId = "some user"
            )
        ).build()

        assertEquals(jsonMapOf(
            "user_id" to "some user",
            "category" to "some category",
            "type" to "some type",
            "ltv" to true,
        ), event.properties)
    }
}
