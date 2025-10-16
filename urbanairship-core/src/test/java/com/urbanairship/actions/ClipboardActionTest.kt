/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.actions.Action.Situation
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ClipboardActionTest {

    private val clipboardManager = ApplicationProvider.getApplicationContext<Context>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var action = ClipboardAction {
        ApplicationProvider.getApplicationContext()
    }

    private val acceptedSituations = arrayOf(
        Situation.PUSH_OPENED,
        Situation.MANUAL_INVOCATION,
        Situation.WEB_VIEW_INVOCATION,
        Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON,
        Situation.BACKGROUND_NOTIFICATION_ACTION_BUTTON,
        Situation.AUTOMATION
    )

    private val rejectedSituations = arrayOf(
        Situation.PUSH_RECEIVED
    )

    /**
     * Test accepts arguments with map action argument value.
     */
    @Test
    public fun testAcceptsArgumentWithMap() {

        val insertMap = mapOf(
            "label" to "clipboard label",
            "text" to "clipboard text"
        )

        acceptedSituations.forEach { situation ->
            val args = ActionTestUtils.createArgs(situation, insertMap)
            assertTrue(
                "Should accept arguments in situation $situation", action.acceptsArguments(args)
            )
        }

        rejectedSituations.forEach { situation ->
            val args = ActionTestUtils.createArgs(situation, insertMap)
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
        val clipboardString = "oh hi"

        acceptedSituations.forEach {
            val args = ActionTestUtils.createArgs(it, clipboardString)
            assertTrue(
                "Should accept arguments in situation $it", action.acceptsArguments(args)
            )

        }

        rejectedSituations.forEach {
            val args = ActionTestUtils.createArgs(it, clipboardString)
            assertFalse(
                "Should reject arguments in situation $it", action.acceptsArguments(args)
            )
        }
    }

    /**
     * Test perform with map action argument value.
     */
    @Test
    @SuppressLint("NewApi")
    public fun testPerformWithMap() {
        val map = mapOf(
            "label" to "clipboard label",
            "text" to "clipboard text"
        )

        val args = ActionTestUtils.createArgs(Situation.PUSH_OPENED, map)
        val result = action.perform(args)

        assertEquals(args.value, result.value)

        val clipData = clipboardManager.primaryClip
        assertEquals(clipData?.description?.label, "clipboard label")
        assertEquals(clipData?.getItemAt(0)?.text, "clipboard text")
    }

    /**
     * Test perform with string action argument value.
     */
    @Test
    @SuppressLint("NewApi")
    public fun testPerformWithString() {
        val args = ActionTestUtils.createArgs(Situation.PUSH_OPENED, "clipboard text")
        val result = action.perform(args)

        assertEquals(args.value, result.value)

        val clipData = clipboardManager.primaryClip
        assert(clipData?.description?.label == null)
        assertEquals(clipData?.getItemAt(0)?.text, "clipboard text")
    }
}
