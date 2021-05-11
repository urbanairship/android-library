/* Copyright Airship and Contributors */

package com.urbanairship.chat

/**
 * Chat direction.
 */
enum class ChatDirection {

    /**
     * Outgoing message.
     */
    OUTGOING,

    /**
     * Incoming message.
     */
    INCOMING
}

/**
 * Chat message.
 */
data class ChatMessage(
    val messageId: String,
    val text: String?,
    val createdOn: Long,
    val direction: ChatDirection,
    val attachmentUrl: String?,
    val pending: Boolean
)
