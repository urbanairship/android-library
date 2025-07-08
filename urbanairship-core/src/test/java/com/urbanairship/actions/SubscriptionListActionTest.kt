/* Copyright Airship and Contributors */
package com.urbanairship.actions

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.actions.Action.Situation
import com.urbanairship.actions.ActionValue.Companion.wrap
import com.urbanairship.channel.SubscriptionListEditor
import com.urbanairship.channel.SubscriptionListMutation
import com.urbanairship.contacts.Scope
import com.urbanairship.contacts.ScopedSubscriptionListEditor
import com.urbanairship.contacts.ScopedSubscriptionListMutation
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class SubscriptionListActionTest {

    private val contactMutations = mutableListOf<ScopedSubscriptionListMutation>()
    private val clock = TestClock()

    private val channelEditor: SubscriptionListEditor = object : SubscriptionListEditor(clock) {
        override fun onApply(collapsedMutations: List<SubscriptionListMutation>) {
            channelMutations.addAll(collapsedMutations)
        }
    }

    private val channelMutations = mutableListOf<SubscriptionListMutation>()
    private val contactEditor: ScopedSubscriptionListEditor =
        object : ScopedSubscriptionListEditor(clock) {
            override fun onApply(collapsedMutations: List<ScopedSubscriptionListMutation>) {
                contactMutations.addAll(collapsedMutations)
            }
        }

    private val action = SubscriptionListAction({ channelEditor }, { contactEditor })

    @Test
    public fun testAcceptsArguments() {
        listOf(
            Situation.MANUAL_INVOCATION,
            Situation.PUSH_OPENED,
            Situation.WEB_VIEW_INVOCATION,
            Situation.BACKGROUND_NOTIFICATION_ACTION_BUTTON,
            Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON,
            Situation.AUTOMATION
        ).forEach { situation ->
            val args = ActionTestUtils.createArgs(situation, wrap(JsonValue.parseString(VALID_ARG)))
            assertTrue(action.acceptsArguments(args))
        }
    }

    @Test
    public fun testRejectArguments() {
        listOf(
            Situation.PUSH_RECEIVED,
        ).forEach { situation ->
            val args = ActionTestUtils.createArgs(situation, wrap(JsonValue.parseString(VALID_ARG)))
            assertFalse(action.acceptsArguments(args))
        }

        val empty = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, wrap(JsonValue.NULL))
        assertFalse(action.acceptsArguments(empty))
    }

    @Test
    public fun testPerform() {
        val args = ActionTestUtils.createArgs(
            situation = Situation.MANUAL_INVOCATION,
            value = wrap(JsonValue.parseString(VALID_ARG))
        )
        action.perform(args)

        val expectedContactMutation = ScopedSubscriptionListMutation.newSubscribeMutation(
            "mylist", Scope.APP, clock.currentTimeMillis
        )
        assertEquals(listOf(expectedContactMutation), contactMutations)

        val expectedChannelMutation =
            SubscriptionListMutation.newUnsubscribeMutation("thelist", clock.currentTimeMillis)
        assertEquals(listOf(expectedChannelMutation), channelMutations)
    }

    @Test
    public fun testPerformInvalidArg() {
        val invalidArg = """[
     {
        "type": "contact",
        "action": "subscribe",
        "list": "mylist",
        "scope": "app"
     },
     {
        "action": "unsubscribe",
       "list": "thelist"
     }
 ]"""

        val args = ActionTestUtils.createArgs(
            situation = Situation.MANUAL_INVOCATION,
            value = wrap(JsonValue.parseString(invalidArg))
        )
        action.perform(args)

        // Should skip all edits even if one is valid
        assertTrue(contactMutations.isEmpty())
        assertTrue(channelMutations.isEmpty())
    }

    public companion object {

        private const val VALID_ARG = """
            [
              {
                "action": "subscribe",
                "list": "mylist",
                "scope": "app",
                "type": "contact"
              },
              {
                "action": "unsubscribe",
                "list": "thelist",
                "type": "channel"
              }
            ]
        """
    }
}
