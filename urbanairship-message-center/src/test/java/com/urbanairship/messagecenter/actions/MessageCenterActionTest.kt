/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter.actions

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionValue
import com.urbanairship.messagecenter.MessageCenter
import com.urbanairship.push.PushMessage
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class MessageCenterActionTest {

    private val mockMessageCenter = mockk<MessageCenter>(relaxUnitFun = true)
    private val action = MessageCenterAction { mockMessageCenter }

    /** Test accepts arguments */
    @Test
    public fun testAcceptsArguments() {
        val situations = arrayOf(
            Action.Situation.PUSH_OPENED,
            Action.Situation.MANUAL_INVOCATION,
            Action.Situation.WEB_VIEW_INVOCATION,
            Action.Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON,
            Action.Situation.AUTOMATION
        )

        // Should accept null value
        for (situation in situations) {
            val args = createArgs(situation, null)
            assertTrue("Should accept arguments in situation $situation", action.acceptsArguments(args))
        }

        // Should accept message ID as the action value
        for (situation in situations) {
            val args = createArgs(situation, "message_id")
            assertTrue("Should accept arguments in situation $situation", action.acceptsArguments(args))
        }
    }

    /** Test action perform when the message ID is not specified it starts an activity to view the inbox. */
    @Test
    public fun testPerformNoMessageId() {
        action.perform(createArgs(Action.Situation.MANUAL_INVOCATION, null))
        verify { mockMessageCenter.showMessageCenter() }
    }

    /** Test action perform when the message is available it starts an activity to view the message. */
    @Test
    public fun testPerform() {
        action.perform(createArgs(Action.Situation.MANUAL_INVOCATION, "message_id"))
        verify { mockMessageCenter.showMessageCenter("message_id") }
    }

    /** Test "auto" placeholder looks for the message's ID in the push message metadata. */
    @Test
    public fun testPerformMessageIdPlaceHolderPushMetadata() {
        val pushBundle = Bundle()
        pushBundle.putString(PushMessage.EXTRA_RICH_PUSH_ID, "the_message_id")
        val metadata = Bundle()
        metadata.putParcelable(ActionArguments.PUSH_MESSAGE_METADATA, PushMessage(pushBundle))
        action.perform(createArgs(Action.Situation.MANUAL_INVOCATION, "auto", metadata))
        verify { mockMessageCenter.showMessageCenter("the_message_id") }
    }

    /** Test "auto" placeholder looks for the message's ID in the rich push message ID metadata. */
    @Test
    public fun testPerformMessageIdPlaceHolderRichPushMessageMetadata() {
        val metadata = Bundle()
        metadata.putString(ActionArguments.RICH_PUSH_ID_METADATA, "the_message_id")
        action.perform(createArgs(Action.Situation.MANUAL_INVOCATION, "auto", metadata))
        verify { mockMessageCenter.showMessageCenter("the_message_id") }
    }

    /** Test "auto" placeholder will fail to find the message ID if no metadata is available and tries to view the inbox instead */
    @Test
    public fun testPerformMessageIdPlaceHolderNoMetadata() {
        action.perform(createArgs(Action.Situation.MANUAL_INVOCATION, "auto"))
        verify { mockMessageCenter.showMessageCenter() }
    }

    /** Test "" placeholder will load the inbox. */
    @Test
    public fun testPerformEmptyMessageId() {
        action.perform(createArgs(Action.Situation.MANUAL_INVOCATION, ""))
        verify { mockMessageCenter.showMessageCenter() }
    }

    private fun createArgs(
        situation: Action.Situation,
        value: String?,
        metadata: Bundle? = null
    ): ActionArguments = ActionArguments(situation, ActionValue.wrap(value), metadata ?: Bundle())
}
