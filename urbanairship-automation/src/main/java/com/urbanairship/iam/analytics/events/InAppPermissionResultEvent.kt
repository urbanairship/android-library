/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics.events

import androidx.core.util.ObjectsCompat
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionStatus

internal class InAppPermissionResultEvent(
    permission: Permission,
    startingStatus: PermissionStatus,
    endingStatus: PermissionStatus
) : InAppEvent {

    private val reportData = PermissionResultData(
        permission = permission.value,
        startingStatus = startingStatus.value,
        endingStatus = endingStatus.value
    )

    override val name: String = "in_app_permission_result"
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

        other as InAppPermissionResultEvent

        if (reportData != other.reportData) return false
        if (name != other.name) return false
        if (data != other.data) return false

        return true
    }

    override fun hashCode(): Int = ObjectsCompat.hash(name, data)
}
