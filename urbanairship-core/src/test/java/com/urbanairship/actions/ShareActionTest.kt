/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows

@RunWith(AndroidJUnit4::class)
public class ShareActionTest {

    private val application: Application
        get() = ApplicationProvider.getApplicationContext()

    private var action = ShareAction { application }

    /**
     * Test the share action accepts Strings in foreground situations.
     */
    @Test
    public fun testAcceptsArgs() {
        listOf(
            Action.Situation.MANUAL_INVOCATION,
            Action.Situation.PUSH_OPENED,
            Action.Situation.WEB_VIEW_INVOCATION,
            Action.Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON,
            Action.Situation.AUTOMATION
        ).forEach { situation ->
            val args = ActionTestUtils.createArgs(situation, "share text")
            assertTrue(action.acceptsArguments(args))
        }
    }

    /**
     * Test that it rejects Action.SITUATION_PUSH_RECEIVED.
     */
    @Test
    public fun testRejectsPossibleBackgroundSituations() {
        listOf(
            Action.Situation.PUSH_RECEIVED,
            Action.Situation.BACKGROUND_NOTIFICATION_ACTION_BUTTON
        ).forEach { situation ->
            val args = ActionTestUtils.createArgs(situation, "share text")
            assertFalse(action.acceptsArguments(args))
        }
    }

    /**
     * Test perform constructs the correct chooser activity intent.
     */
    @Test
    public fun testPerform() {
        val args = ActionTestUtils.createArgs(Action.Situation.MANUAL_INVOCATION, "Share text")
        action.perform(args)

        // Verify the started intent has the right flags and actions
        val startedIntent = Shadows.shadowOf(application).nextStartedActivity
        assertEquals(startedIntent.action, Intent.ACTION_CHOOSER)
        assertEquals(startedIntent.flags.toLong(), Intent.FLAG_ACTIVITY_NEW_TASK.toLong())
        assertFalse(startedIntent.hasExtra(Intent.EXTRA_INITIAL_INTENTS))

        // Verify the chooser intent is contained in the starter intent
        val chooserIntent = startedIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(chooserIntent!!.getStringExtra(Intent.EXTRA_TEXT), "Share text")
        assertEquals(chooserIntent.action, Intent.ACTION_SEND)
        assertEquals(null, chooserIntent.getPackage())
        assertFalse(chooserIntent.hasExtra(Intent.EXTRA_INITIAL_INTENTS))
    }
}
