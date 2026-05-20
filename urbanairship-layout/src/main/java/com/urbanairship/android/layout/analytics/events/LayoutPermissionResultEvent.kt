/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.analytics.events

import androidx.annotation.RestrictTo
import androidx.core.util.ObjectsCompat
import com.urbanairship.analytics.EventType
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionStatus

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class LayoutPermissionResultEvent(
    permission: Permission,
    startingStatus: PermissionStatus,
    endingStatus: PermissionStatus
) : LayoutEvent {

    private val reportData = PermissionResultData(
        permission = permission.value,
        startingStatus = startingStatus.value,
        endingStatus = endingStatus.value
    )

    override val eventType: EventType = EventType.IN_APP_PERMISSION_RESULT
    override val data: JsonSerializable = reportData

    private data class PermissionResultData(
        val permission: String,
        val startingStatus: String,
        val endingStatus: String
    ) : JsonSerializable {
        companion object {
            private const val PERMISSION = "permission"
            private const val STARTING_STATUS = "starting_permission_status"
            private const val ENDING_STATUS = "ending_permission_status"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            PERMISSION to permission,
            STARTING_STATUS to startingStatus,
            ENDING_STATUS to endingStatus
        ).toJsonValue()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LayoutPermissionResultEvent

        if (reportData != other.reportData) return false
        if (eventType != other.eventType) return false
        if (data != other.data) return false

        return true
    }

    override fun hashCode(): Int = ObjectsCompat.hash(eventType, data)
}
