/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.UrlAllowList
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows

@RunWith(AndroidJUnit4::class)
public class OpenExternalUrlActionTest {

    private val urlAllowList: UrlAllowList = mockk()
    private val action = OpenExternalUrlAction { urlAllowList }

    /**
     * Test accepts arguments
     */
    @Test
    public fun testAcceptsArguments() {
        every { urlAllowList.isAllowed(any(), UrlAllowList.SCOPE_OPEN_URL) } returns true

        listOf(
            Action.Situation.MANUAL_INVOCATION,
            Action.Situation.PUSH_OPENED,
            Action.Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON
        ).forEach {
            val args = ActionTestUtils.createArgs(it, "http://example.com")
            assertTrue("Should accept arguments in situation $it", action.acceptsArguments(args))
        }

        val args = ActionTestUtils.createArgs(Action.Situation.PUSH_RECEIVED, "http://example.com")
        assertFalse(
            "Should not accept Action.SITUATION_PUSH_RECEIVED", action.acceptsArguments(args)
        )
    }

    /**
     * Test accepts arguments for URLs that are allowed.
     */
    @Test
    public fun testUrlAllowList() {
        every { urlAllowList.isAllowed(any(), UrlAllowList.SCOPE_OPEN_URL) } answers {
            "https://yep.example.com" == firstArg()
        }

        assertTrue(
            action.acceptsArguments(
                ActionTestUtils.createArgs(
                    Action.Situation.MANUAL_INVOCATION, "https://yep.example.com"
                )
            )
        )

        assertFalse(
            action.acceptsArguments(
                ActionTestUtils.createArgs(
                    Action.Situation.MANUAL_INVOCATION, "https://nope.example.com"
                )
            )
        )
    }

    /**
     * Test perform tries to start an activity with the URL
     */
    @Test
    public fun testPerform() {
        every { urlAllowList.isAllowed(any(), UrlAllowList.SCOPE_OPEN_URL) } returns true

        var args =
            ActionTestUtils.createArgs(Action.Situation.WEB_VIEW_INVOCATION, "http://example.com")
        var result = action.perform(args)

        assertEquals("Value should be the uri", "http://example.com", result.value?.getString(""))
        validateLastActivity("http://example.com")

        args = ActionTestUtils.createArgs(
            Action.Situation.WEB_VIEW_INVOCATION, "adfadfafdsaf adfa dsfadfsa example"
        )
        result = action.perform(args)
        assertEquals(
            "Value should be the uri",
            "adfadfafdsaf adfa dsfadfsa example",
            result.value?.getString("")
        )
        validateLastActivity("adfadfafdsaf adfa dsfadfsa example")
    }

    /**
     * Helper method to validate the activity is launched correctly from
     * the open url action
     */
    private fun validateLastActivity(expectedUri: String) {
        val application = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
        val intent = application.nextStartedActivity
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(expectedUri, intent.dataString)
    }
}
