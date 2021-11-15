package com.urbanairship.chat

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestApplication
import com.urbanairship.actions.Action
import com.urbanairship.actions.Action.Situation
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionValue
import com.urbanairship.job.JobDispatcher
import com.urbanairship.json.JsonMap
import com.urbanairship.push.PushManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class SendChatActionTest {
    private var mockConversation: Conversation = mock()
    private var mockPush: PushManager = mock()
    private var mockJobDispatcher: JobDispatcher = mock()

    private lateinit var action: SendChatAction
    private lateinit var chat: Chat
    private lateinit var dataStore: PreferenceDataStore
    private lateinit var privacyManager: PrivacyManager

    @Before
    fun setUp() {
        dataStore = PreferenceDataStore.inMemoryStore(TestApplication.getApplication())
        privacyManager = PrivacyManager(dataStore, PrivacyManager.FEATURE_ALL)
        chat = Chat(TestApplication.getApplication(), dataStore,
                privacyManager, mockPush, mockConversation, mockJobDispatcher)
        action = SendChatAction {
            chat
        }
    }

    @Test
    fun testAcceptsArguments() {
        @Situation val acceptSituations = intArrayOf(Action.SITUATION_PUSH_OPENED, Action.SITUATION_MANUAL_INVOCATION,
                Action.SITUATION_WEB_VIEW_INVOCATION, Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON,
                Action.SITUATION_AUTOMATION)

        @Situation val rejectSituations = intArrayOf(Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON, Action.SITUATION_PUSH_RECEIVED)

        acceptSituations.forEach {
            val messageArgs: ActionArguments = ActionArguments(it, ActionValue.wrap(JsonMap.newBuilder().put("message", "neat").build()), Bundle.EMPTY)
            val routingArgs: ActionArguments = ActionArguments(it, ActionValue.wrap(JsonMap.newBuilder().put("chat_routing", ChatRouting("fakeagent")).build()), Bundle.EMPTY)
            assertTrue("Should accept arguments in situation $it", action.acceptsArguments(messageArgs))
            assertTrue("Should accept arguments in situation $it", action.acceptsArguments(routingArgs))
        }

        rejectSituations.forEach {
            val args: ActionArguments = ActionArguments(it, null, Bundle.EMPTY)
            assertFalse("Should reject arguments in situation $it", action.acceptsArguments(args))
        }
    }

    @Test
    fun testPerformWithMessage() {
        val args = ActionArguments(Action.SITUATION_AUTOMATION, ActionValue.wrap(JsonMap.newBuilder().put("message", "neat").build()), Bundle.EMPTY)
        val result = action.perform(args)

        assertTrue(result.value.isNull)
    }

    @Test
    fun testPerformWithRouting() {
        val args = ActionArguments(Action.SITUATION_AUTOMATION, ActionValue.wrap(JsonMap.newBuilder().put("chat_routing", ChatRouting("fakeagent")).build()), Bundle.EMPTY)
        val result = action.perform(args)

        assertTrue(result.value.isNull)
    }
}
