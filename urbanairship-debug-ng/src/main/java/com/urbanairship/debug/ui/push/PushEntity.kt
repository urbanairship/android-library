package com.urbanairship.debug.ui.push

import androidx.annotation.RestrictTo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.urbanairship.push.PushMessage

/**
 * Entities stored in the push database.
 * @hide
 */
@Entity(tableName = "pushes")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal data class PushEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val pushId: String,
    val payload: String,
    val time: Long
) {

    constructor(pushMessage: PushMessage) : this(
        0,
        pushMessage.canonicalPushId ?: "MISSING",
        pushMessage.toJsonValue().toString(),
        System.currentTimeMillis())
}
