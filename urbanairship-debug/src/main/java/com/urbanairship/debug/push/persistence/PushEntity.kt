/* Copyright Airship and Contributors */

package com.urbanairship.debug.push.persistence

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import android.support.annotation.RestrictTo
import com.urbanairship.push.PushMessage

/**
 * Entities stored in the event database.
 * @hide
 */
@Entity(tableName = "pushes")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class PushEntity(@PrimaryKey(autoGenerate = true)
                           val id: Int,
                           val pushId: String,
                           val payload: String,
                           val time: Long) {

    constructor(pushMessage: PushMessage) : this(
            0,
            pushMessage.canonicalPushId ?: "MISSING",
            pushMessage.toJsonValue().toString(),
            System.currentTimeMillis())

}
