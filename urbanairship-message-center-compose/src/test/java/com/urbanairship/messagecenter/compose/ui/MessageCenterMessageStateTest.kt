/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter.compose.ui

import com.google.common.truth.Truth.assertThat
import com.urbanairship.messagecenter.compose.createMessage
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.Action
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.State
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.State.MessageContent.Content
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.State.MessageContent.WebViewState
import io.mockk.mockk
import org.junit.Test

public class MessageCenterMessageStateTest {

    private val actions = mutableListOf<Action>()
    private val state = MessageCenterMessageState(
        onAction = { actions.add(it) },
        makeAnalytics = { _, _ -> mockk() },
    )

    @Test
    public fun settingMessageIdLoadsThatMessage() {
        state.viewState = State.MessageContent(createMessage("current"), Content.Html(WebViewState.LOADED))

        state.messageId = "next"

        assertThat(actions).containsExactly(Action.LoadMessage("next"))
    }

    @Test
    public fun settingMessageIdWithNoMessageLoadsIt() {
        state.messageId = "next"

        assertThat(actions).containsExactly(Action.LoadMessage("next"))
    }

    @Test
    public fun settingMessageIdToNullClearsMessage() {
        state.viewState = State.MessageContent(createMessage("current"), Content.Html(WebViewState.LOADED))

        state.messageId = null

        assertThat(actions).containsExactly(Action.ClearMessage)
    }

    @Test
    public fun selectAndClearMessageRouteThroughMessageState() {
        val centerState = MessageCenterState(listState = mockk(), messageState = state)

        centerState.selectMessage("selected")
        centerState.clearMessage()

        assertThat(actions).containsExactly(Action.LoadMessage("selected"), Action.ClearMessage).inOrder()
    }
}
