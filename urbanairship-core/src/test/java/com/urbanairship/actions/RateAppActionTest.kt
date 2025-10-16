/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.actions.Action.Situation
import com.urbanairship.actions.ActionValue.Companion.wrap
import com.urbanairship.json.JsonMap
import com.urbanairship.json.jsonMapOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows

@RunWith(AndroidJUnit4::class)
public class RateAppActionTest {

    private var appStoreIntent = Intent("test")

    private var action = RateAppAction(
        appStoreIntentProvider = { appStoreIntent },
        contextProvider = { ApplicationProvider.getApplicationContext() }
    )

    private val checkSituations = arrayOf(
        Situation.PUSH_OPENED,
        Situation.MANUAL_INVOCATION,
        Situation.WEB_VIEW_INVOCATION,
        Situation.AUTOMATION,
        Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON
    )


    /**
     * Test accepted arguments
     */
    @Test
    public fun testAcceptsArguments() {
        // Test payload without prompt
        val linkPayload = jsonMapOf(RateAppAction.SHOW_LINK_PROMPT_KEY to false)
        verifyAcceptsArgumentValue(linkPayload, true)

        // Test payload with prompt
        val linkPromptPayload = jsonMapOf(RateAppAction.SHOW_LINK_PROMPT_KEY to true)
        verifyAcceptsArgumentValue(linkPromptPayload, true)

        // Test customized prompt
        val customizedMessagePayload = jsonMapOf(
            RateAppAction.SHOW_LINK_PROMPT_KEY to true,
            RateAppAction.TITLE_KEY to "some title",
            RateAppAction.BODY_KEY to "some body"
        )
        verifyAcceptsArgumentValue(customizedMessagePayload, true)

        // Test empty arguments
        verifyAcceptsArgumentValue(null, true)
    }

    /**
     * Test perform with link.
     */
    @Test
    public fun testPerformLink() {
        // Test payload without prompt
        val linkPayload = jsonMapOf(RateAppAction.SHOW_LINK_PROMPT_KEY to false)

        verifyPerform(linkPayload) {
            assertEquals(appStoreIntent, it)
        }

        // Test empty payload
        verifyPerform(null) {
            assertEquals(appStoreIntent, it)
        }
    }

    /**
     * Test perform with prompt.
     */
    @Test
    public fun testPerformPrompt() {
        // Test payload with only prompt
        val promptPayload = jsonMapOf(RateAppAction.SHOW_LINK_PROMPT_KEY to true)

        verifyPerform(promptPayload) {
            assertEquals(RateAppAction.SHOW_RATE_APP_INTENT_ACTION, it.action)
            assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP, it.flags)
        }

        // Test customized prompt
        val customizedMessagePayload = jsonMapOf(
            RateAppAction.SHOW_LINK_PROMPT_KEY to true,
            RateAppAction.TITLE_KEY to "some title",
            RateAppAction.BODY_KEY to "some body"
        )

        // Test empty payload
        verifyPerform(customizedMessagePayload) {
            assertEquals(RateAppAction.SHOW_RATE_APP_INTENT_ACTION, it.action)
            assertEquals(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP, it.flags
            )

            assertEquals("some title", it.getStringExtra(RateAppAction.TITLE_KEY))
            assertEquals("some body", it.getStringExtra(RateAppAction.BODY_KEY))
        }
    }

    private fun verifyPerform(value: JsonMap?, verifyIntent: (Intent) -> Unit) {
        val application = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())

        checkSituations.forEach { situation ->
            val args = ActionTestUtils.createArgs(
                situation = situation,
                value = if (value == null) ActionValue() else wrap(value)
            )

            val result = action.perform(args)
            assertEquals(ActionResult.Status.COMPLETED, result.status)

            verifyIntent.invoke(application.nextStartedActivity)
        }
    }

    private fun verifyAcceptsArgumentValue(jsonMap: JsonMap?, shouldAccept: Boolean) {
        checkSituations.forEach { situation ->
            val args = ActionTestUtils.createArgs(
                situation = situation,
                value = if (jsonMap == null) ActionValue() else wrap(jsonMap)
            )

            assertTrue(action.acceptsArguments(args) == shouldAccept)
        }
    }
}
