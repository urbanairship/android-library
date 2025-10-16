/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.actions.Action.Situation
import com.urbanairship.actions.ActionTestUtils.createArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowToast

@RunWith(AndroidJUnit4::class)
public class ToastActionTest {

    private var action = ToastAction { ApplicationProvider.getApplicationContext() }

    // Accepted situations (All - PUSH_RECEIVED)
    private val acceptedSituations: Array<Situation> = arrayOf(
        Situation.PUSH_OPENED,
        Situation.MANUAL_INVOCATION,
        Situation.WEB_VIEW_INVOCATION,
        Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON,
        Situation.BACKGROUND_NOTIFICATION_ACTION_BUTTON,
        Situation.AUTOMATION
    )

    // Rejected situations (All - accepted)
    private val rejectedSituations: Array<Situation> = arrayOf(
        Situation.PUSH_RECEIVED
    )

    /**
     * Test accepts arguments with map action argument value.
     */
    @Test
    public fun testAcceptsArgumentWithMap() {
        val toastMap = mapOf(
            "length" to Toast.LENGTH_LONG,
            "text" to "toast text"
        )

        for (situation in acceptedSituations) {
            val args = createArgs(situation, toastMap)
            assertTrue(
                "Should accept arguments in situation $situation", action.acceptsArguments(args)
            )
        }

        for (situation in rejectedSituations) {
            val args = createArgs(situation, toastMap)
            assertFalse(
                "Should reject arguments in situation $situation", action.acceptsArguments(args)
            )
        }
    }

    /**
     * Test accepts arguments with string action argument value.
     */
    @Test
    public fun testAcceptsArgumentWithString() {
        val toastText = "oh hi"

        for (situation in acceptedSituations) {
            val args = createArgs(situation, toastText)
            assertTrue(
                "Should accept arguments in situation $situation", action.acceptsArguments(args)
            )
        }

        for (situation in rejectedSituations) {
            val args = createArgs(situation, toastText)
            assertFalse(
                "Should reject arguments in situation $situation", action.acceptsArguments(args)
            )
        }
    }

    /**
     * Test perform with map action argument value.
     */
    @Test
    @SuppressLint("NewApi")
    public fun testPerformWithMap() {
        val toastMap = mapOf(
            "length" to Toast.LENGTH_LONG,
            "text" to "totes"
        )

        val args = createArgs(Situation.PUSH_OPENED, toastMap)
        val result = action.perform(args)

        assertEquals(args.value, result.value)

        assertEquals("totes", ShadowToast.getTextOfLatestToast())
        assertEquals(Toast.LENGTH_LONG, ShadowToast.getLatestToast().duration)
    }

    /**
     * Test perform with string action argument value.
     */
    @Test
    @SuppressLint("NewApi")
    public fun testPerformWithString() {
        val args = createArgs(Situation.PUSH_OPENED, "totes")
        val result = action.perform(args)

        assertEquals(args.value, result.value)

        assertEquals("totes", ShadowToast.getTextOfLatestToast())
        assertEquals(Toast.LENGTH_SHORT, ShadowToast.getLatestToast().duration)
    }
}
