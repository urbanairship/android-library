/* Copyright Airship and Contributors */

package com.urbanairship.chat.data

import androidx.annotation.RestrictTo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.urbanairship.chat.ChatDirection

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Entity(tableName = "messages")
internal data class MessageEntity(
    @PrimaryKey
    val messageId: String,
    val text: String?,
    val attachment: String?,
    val createdOn: Long,
    val direction: ChatDirection,
    val isPending: Boolean
)
