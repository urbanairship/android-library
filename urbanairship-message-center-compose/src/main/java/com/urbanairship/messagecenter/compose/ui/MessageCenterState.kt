package com.urbanairship.messagecenter.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.urbanairship.Predicate
import com.urbanairship.messagecenter.Message

/**
 * State holder that combines both list and message functionality for the Message Center.
 *
 * @param listState The state for the message list.
 * @param messageState The state for the message display.
 */
@Stable
public class MessageCenterState internal constructor(
    public val listState: MessageCenterListState,
    public val messageState: MessageCenterMessageState,
) {
    /**
     * Selects and displays a message by its ID.
     * This is a convenience method that updates the [messageState].
     */
    public fun selectMessage(messageId: String) {
        messageState.messageId = messageId
    }

    /**
     * Clears the currently displayed message.
     */
    public fun clearMessage() {
        messageState.messageId = null
    }
}

/**
 * Remembers a [MessageCenterState] that combines both list and message functionality.
 *
 * @param predicate Optional predicate to filter messages in the list
 * @param messageId Optional message ID to display initially
 */
@Composable
public fun rememberMessageCenterState(
    predicate: Predicate<Message>? = null,
    messageId: String? = null,
): MessageCenterState {
    val listState = rememberMessageCenterListState(
        predicate = predicate,
        highlightedMessageId = messageId
    )
    val messageState = rememberMessageCenterMessageState(messageId = messageId)

    return remember(listState, messageState) {
        MessageCenterState(
            listState = listState,
            messageState = messageState
        )
    }
}
