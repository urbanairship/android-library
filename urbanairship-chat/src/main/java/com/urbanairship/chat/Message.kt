package com.urbanairship.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A message. */
@Serializable
data class Message(
    /** ID of the message. */
    @SerialName("message_id") val messageId: String?,
    /** When the message was created. */
    @SerialName("created_on") val createdOn: String,
    /** Whether this message was sent (0) or received (1). */
    val direction: Int,
    /** Message text. */
    val text: String?,
    /** An attachment URL. */
    val attachment: String?,
    /** Request ID. */
    @SerialName("request_id") val requestId: String?
)
