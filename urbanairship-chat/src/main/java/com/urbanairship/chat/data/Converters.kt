/* Copyright Airship and Contributors */

package com.urbanairship.chat.data

import androidx.annotation.RestrictTo
import androidx.room.TypeConverter
import com.urbanairship.chat.ChatDirection

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class Converters {

    @TypeConverter
    fun fromChatDirection(value: ChatDirection): Int {
        return when (value) {
            ChatDirection.OUTGOING -> 0
            ChatDirection.INCOMING -> 1
        }
    }

    @TypeConverter
    fun toChatDirection(value: Int): ChatDirection {
        return when (value) {
            0 -> ChatDirection.OUTGOING
            1 -> ChatDirection.INCOMING
            else -> ChatDirection.INCOMING
        }
    }
}
