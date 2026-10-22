/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter.compose.ui

import com.google.common.truth.Truth.assertThat
import com.urbanairship.messagecenter.compose.createMessage
import com.urbanairship.messagecenter.compose.ui.MessageCenterListViewModel.Action
import com.urbanairship.messagecenter.compose.ui.MessageCenterListViewModel.State
import org.junit.Test

public class MessageCenterListStateTest {

    private val actions = mutableListOf<Action>()
    private val state = MessageCenterListState(onAction = { actions.add(it) })

    private val messages = listOf(createMessage("one"), createMessage("two"))

    @Test
    public fun loadingStateExposesNoContent() {
        assertThat(state.isLoading).isTrue()
        assertThat(state.isError).isFalse()
        assertThat(state.isRefreshing).isFalse()
        assertThat(state.isEditing).isFalse()
        assertThat(state.messages).isEmpty()
        assertThat(state.selectedMessageIds).isEmpty()
        assertThat(state.areAllMessagesSelected).isFalse()
        assertThat(state.highlightedMessageId).isNull()
    }

    @Test
    public fun errorStateExposesNoContent() {
        state.viewState = State.Error

        assertThat(state.isLoading).isFalse()
        assertThat(state.isError).isTrue()
        assertThat(state.messages).isEmpty()
    }

    @Test
    public fun contentStateExposesMessages() {
        state.viewState = State.Content(messages = messages, isRefreshing = true, highlightedMessageId = "two")

        assertThat(state.isLoading).isFalse()
        assertThat(state.isError).isFalse()
        assertThat(state.isRefreshing).isTrue()
        assertThat(state.messages).containsExactlyElementsIn(messages).inOrder()
        assertThat(state.highlightedMessageId).isEqualTo("two")
    }

    @Test
    public fun contentStateExposesSelection() {
        state.viewState = State.Content(
            messages = messages, isRefreshing = false, isEditing = true, selectedMessageIds = setOf("one")
        )

        assertThat(state.isEditing).isTrue()
        assertThat(state.selectedMessageIds).containsExactly("one")
        assertThat(state.areAllMessagesSelected).isFalse()

        state.viewState = (state.viewState as State.Content).copy(selectedMessageIds = setOf("one", "two"))

        assertThat(state.areAllMessagesSelected).isTrue()
    }

    @Test
    public fun methodsDispatchActions() {
        val onRefreshed = {}

        state.refresh(onRefreshed)
        state.markRead(setOf("one"))
        state.delete(setOf("two"))
        state.markSelectedRead()
        state.deleteSelected()
        state.setEditing(true)
        state.setSelected("one", true)
        state.selectAll()
        state.selectNone()
        state.setHighlighted("two")
        state.setHighlighted(null)

        assertThat(actions).containsExactly(
            Action.Refresh(onRefreshed),
            Action.MarkMessagesRead(setOf("one")),
            Action.DeleteMessages(setOf("two")),
            Action.MarkSelectedMessagesRead,
            Action.DeleteSelectedMessages,
            Action.SetEditing(true),
            Action.SetSelected("one", true),
            Action.SelectAll,
            Action.SelectNone,
            Action.SetHighlighted("two"),
            Action.SetHighlighted(null as String?),
        ).inOrder()
    }
}
