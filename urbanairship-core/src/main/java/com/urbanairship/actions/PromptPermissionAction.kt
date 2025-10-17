/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.app.Activity
import android.os.ResultReceiver
import androidx.annotation.Keep
import androidx.core.os.bundleOf
import com.urbanairship.Airship
import com.urbanairship.actions.ActionResult.Companion.newEmptyResult
import com.urbanairship.actions.ActionResult.Companion.newErrorResult
import com.urbanairship.actions.PromptPermissionAction.Companion.DEFAULT_NAMES
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionPromptFallback
import com.urbanairship.permission.PermissionStatus
import com.urbanairship.permission.PermissionsManager
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * An action that prompts for permission.
 *
 *
 * Expected value:
 * - permission: post_notifications, contacts, bluetooth, location, media, mic, or camera
 * - fallback_system_settings: `true` to navigate to app settings if the permission is silently denied.
 * - allow_airship_usage: If the permission is granted, any Airship features that depend on the
 * permission will be enabled as well, e.g., enable user notifications on PushManager and push feature
 * on privacy Manager if notifications are allowed.
 *
 *
 * Accepted situations: [Action.Situation.PUSH_OPENED], [Action.Situation.WEB_VIEW_INVOCATION],
 * [Action.Situation.MANUAL_INVOCATION], [Action.Situation.AUTOMATION],
 * and [Action.Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON].
 *
 *
 * Default Result: empty. The actual permission result can be received using a ResultReceiver in the metadata.
 *
 *
 * Default Registration Name: [DEFAULT_NAMES]
 */
public open class PromptPermissionAction public constructor(
    private val permissionsManagerProvider: () -> PermissionsManager
): Action() {

    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    @Keep
    public constructor() : this({ Airship.permissionsManager })

    override fun acceptsArguments(arguments: ActionArguments): Boolean {
        // Validate situation
        return when (arguments.situation) {
            Situation.PUSH_OPENED,
            Situation.WEB_VIEW_INVOCATION,
            Situation.MANUAL_INVOCATION,
            Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON,
            Situation.AUTOMATION -> {
                true
            }
            else -> false
        }
    }

    override fun perform(arguments: ActionArguments): ActionResult {
        val resultReceiver = arguments.metadata.getParcelable<ResultReceiver>(RECEIVER_METADATA)

        try {
            val args = parseArg(arguments)
            prompt(args, resultReceiver)
            return newEmptyResult()
        } catch (e: Exception) {
            return newErrorResult(e)
        }
    }

    @Throws(JsonException::class, IllegalArgumentException::class)
    protected open fun parseArg(arguments: ActionArguments): Args {
        return Args.fromJson(arguments.value.toJsonValue())
    }

    @Throws(ExecutionException::class, InterruptedException::class)
    protected fun prompt(args: Args, resultReceiver: ResultReceiver?) {
        val permissionsManager = requireNotNull(permissionsManagerProvider())

        scope.launch {
            val before = permissionsManager.checkPermissionStatus(args.permission)
            val current = permissionsManager.requestPermission(
                permission = args.permission,
                enableAirshipUsageOnGrant = args.enableAirshipUsage,
                fallback = if (args.fallbackSystemSettings) PermissionPromptFallback.SystemSettings else PermissionPromptFallback.None
            )

            sendResult(args.permission, before, current.permissionStatus, resultReceiver)
        }
    }

    public fun sendResult(
        permission: Permission,
        before: PermissionStatus,
        after: PermissionStatus,
        resultReceiver: ResultReceiver?
    ) {
        resultReceiver?.send(
            Activity.RESULT_OK,
            bundleOf(
                PERMISSION_RESULT_KEY to permission.toJsonValue().toString(),
                BEFORE_PERMISSION_STATUS_RESULT_KEY to before.toJsonValue().toString(),
                AFTER_PERMISSION_STATUS_RESULT_KEY to after.toJsonValue().toString()
            )
        )
    }

    override fun shouldRunOnMainThread(): Boolean {
        return true
    }

    public open class Args internal constructor(
        @JvmField public val permission: Permission,
        @JvmField public val enableAirshipUsage: Boolean,
        @JvmField public val fallbackSystemSettings: Boolean
    ) {

        public companion object {

            @Throws(JsonException::class)
            public fun fromJson(value: JsonValue): Args {
                val permission = Permission.fromJson(value.requireMap().opt(PERMISSION_ARG_KEY))

                val enableAirshipUsage = value.requireMap()
                    .opt(ENABLE_AIRSHIP_USAGE_ARG_KEY)
                    .getBoolean(false)

                val fallbackSystemSettings = value.requireMap()
                    .opt(FALLBACK_SYSTEM_SETTINGS_ARG_KEY)
                    .getBoolean(false)

                return Args(permission, enableAirshipUsage, fallbackSystemSettings)
            }
        }
    }

    public companion object {

        /**
         * Metadata key for a result receiver. Use [PermissionResultReceiver] to simplify parsing the result.
         */
        public const val RECEIVER_METADATA: String =
            "com.urbanairship.actions.PromptPermissionActionReceiver"


        /**
         * Default action names.
         */
        public val DEFAULT_NAMES: Set<String> = setOf("prompt_permission_action", "^pp")

        /**
         * Permission argument key.
         */
        public const val PERMISSION_ARG_KEY: String = "permission"

        /**
         * Enable airship usage argument key.
         */
        public const val ENABLE_AIRSHIP_USAGE_ARG_KEY: String = "enable_airship_usage"

        /**
         * Fallback system settings argument key.
         */
        public const val FALLBACK_SYSTEM_SETTINGS_ARG_KEY: String = "fallback_system_settings"

        /**
         * Permissions result key when using a result receiver.
         */
        public const val PERMISSION_RESULT_KEY: String = "permission"

        /**
         * The starting permission status key when using a result receiver.
         */
        public const val BEFORE_PERMISSION_STATUS_RESULT_KEY: String = "before"

        /**
         * Resulting permission status key when using a result receiver.
         */
        public const val AFTER_PERMISSION_STATUS_RESULT_KEY: String = "after"
    }
}
