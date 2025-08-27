package com.urbanairship.actions

import android.app.Application
import android.content.Intent
import androidx.core.os.bundleOf
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.Airship
import com.urbanairship.push.PushMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows

/* Copyright Airship and Contributors */
@RunWith(AndroidJUnit4::class)
public class DeepLinkActionTest {

    private val mockShip: Airship = mockk()
    private val action = DeepLinkAction(airshipSupplier = { mockShip })

    @Test
    public fun testPerform() {
        val deepLink = "http://example.com"
        every { mockShip.deepLink(deepLink) } returns true

        val args = ActionTestUtils.createArgs(Action.Situation.WEB_VIEW_INVOCATION, deepLink)
        val result = action.perform(args)

        assertEquals(result.status, ActionResult.Status.COMPLETED)
        assertEquals(deepLink, result.value.getString(""))

        verify { mockShip.deepLink(any()) }
    }

    @Test
    public fun testAcceptsArguments() {
        listOf(
            Action.Situation.MANUAL_INVOCATION,
            Action.Situation.PUSH_OPENED,
            Action.Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON,
            Action.Situation.PUSH_RECEIVED
        ).forEach { situation ->
            val args = ActionTestUtils.createArgs(Action.Situation.MANUAL_INVOCATION, "http://example.com")
            assertTrue("Should accept valid url string for situation $situation", action.acceptsArguments(args))
        }
    }

    @Test
    public fun testPerformFallback() {
        val deepLink = "http://example.com"
        every { mockShip.deepLink(deepLink) } returns false

        val args = ActionTestUtils.createArgs(Action.Situation.WEB_VIEW_INVOCATION, deepLink)
        val result = action.perform(args)

        assertEquals(deepLink, result.value.getString(""))
        validateLastActivity(deepLink, null)
    }

    @Test
    public fun testPerformFallbackAnyString() {
        val deepLink = "adfadfafdsaf adfa dsfadfsa example"
        every { mockShip.deepLink(deepLink) } returns false

        val args = ActionTestUtils.createArgs(Action.Situation.WEB_VIEW_INVOCATION, deepLink)
        val result = action.perform(args)

        assertEquals(deepLink, result.value.getString(""))
        validateLastActivity(deepLink, null)
    }

    @Test
    public fun testFallbackPushMessage() {
        val message = PushMessage(bundleOf("oh" to "hi"))

        val metadata = bundleOf(
            ActionArguments.PUSH_MESSAGE_METADATA to message
        )

        every { mockShip.deepLink(any()) } returns false

        val args =
            ActionTestUtils.createArgs(Action.Situation.PUSH_OPENED, "http://example.com", metadata)
        val result = action.perform(args)

        assertEquals("Value should be the uri", "http://example.com", result.value.getString(""))
        validateLastActivity("http://example.com", message)
    }

    private fun validateLastActivity(expectedUri: String, message: PushMessage?) {
        val application = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
        val intent = application.nextStartedActivity
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(expectedUri, intent.dataString)

        if (message != null) {
            assertEquals(message, PushMessage.fromIntent(intent))
        }
    }
}
