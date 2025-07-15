/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.os.Bundle
import android.os.Handler
import android.os.ResultReceiver
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionStatus

/**
 * Result receiver to receive permission results from the [PromptPermissionAction].
 */
public abstract class PermissionResultReceiver
/**
 * Default constructor.
 *
 * @param handler The handler to receive the result callback.
 */
public constructor(handler: Handler) : ResultReceiver(handler) {

    /**
     * Called when a new permission result is received.
     *
     * @param permission The permission.
     * @param before The status before requesting permission.
     * @param after The resulting status.
     */
    public abstract fun onResult(
        permission: Permission, before: PermissionStatus, after: PermissionStatus
    )

    override fun onReceiveResult(resultCode: Int, resultData: Bundle) {
        super.onReceiveResult(resultCode, resultData)
        try {
            val permission = parsePermission(resultData, PromptPermissionAction.PERMISSION_RESULT_KEY)
            val before = parseStatus(resultData, PromptPermissionAction.BEFORE_PERMISSION_STATUS_RESULT_KEY)
            val after = parseStatus(resultData, PromptPermissionAction.AFTER_PERMISSION_STATUS_RESULT_KEY)
            onResult(permission, before, after)
        } catch (e: JsonException) {
            UALog.e(e, "Failed to parse result")
        }
    }

    public companion object {

        @Throws(JsonException::class)
        public fun parsePermission(bundle: Bundle, key: String?): Permission {
            val value = JsonValue.parseString(bundle.getString(key))
            return Permission.fromJson(value)
        }

        @Throws(JsonException::class)
        public fun parseStatus(bundle: Bundle, key: String?): PermissionStatus {
            val value = JsonValue.parseString(bundle.getString(key))
            return PermissionStatus.fromJson(value)
        }
    }
}
