package com.urbanairship.chat

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.actions.Action
import com.urbanairship.actions.Action.Situation
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionValue
import com.urbanairship.json.JsonMap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class OpenChatActionTest {

    private lateinit var mockChat: Chat
    private lateinit var action: OpenChatAction

    @Before
    fun setUp() {
        mockChat = mock()
        action = OpenChatAction {
            mockChat
        }
    }

    @Test
    fun testAcceptsArguments() {
        @Situation val acceptSituations = intArrayOf(Action.SITUATION_PUSH_OPENED, Action.SITUATION_MANUAL_INVOCATION,
                Action.SITUATION_WEB_VIEW_INVOCATION, Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON,
                Action.SITUATION_AUTOMATION)

        @Situation val rejectSituations = intArrayOf(Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON, Action.SITUATION_PUSH_RECEIVED)

        acceptSituations.forEach {
            val messageArgs: ActionArguments = ActionArguments(it, ActionValue.wrap(JsonMap.newBuilder().put("chat_input", "neat").build()), Bundle.EMPTY)
            val nullArgs: ActionArguments = ActionArguments(it, null, Bundle.EMPTY)
            assertTrue("Should accept arguments in situation $it", action.acceptsArguments(nullArgs))
            assertTrue("Should accept arguments in situation $it", action.acceptsArguments(messageArgs))
        }

        rejectSituations.forEach {
            val args: ActionArguments = ActionArguments(it, null, Bundle.EMPTY)
            assertFalse("Should reject arguments in situation $it", action.acceptsArguments(args))
        }
    }

    @Test
    fun testPerform() {
        val args = ActionArguments(Action.SITUATION_AUTOMATION, null, Bundle.EMPTY)
        action.perform(args)
        verify(mockChat).openChat(null)
    }

    @Test
    fun testPerformWithMessage() {
        val args = ActionArguments(Action.SITUATION_AUTOMATION, ActionValue.wrap(JsonMap.newBuilder().put("chat_input", "neat").build()), Bundle.EMPTY)
        action.perform(args)
        verify(mockChat).openChat("neat")
    }
}
