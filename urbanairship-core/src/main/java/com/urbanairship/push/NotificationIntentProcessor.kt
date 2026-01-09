/* Copyright Airship and Contributors */
package com.urbanairship.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.MainThread
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.bundleOf
import com.urbanairship.Airship
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PendingResult
import com.urbanairship.UALog
import com.urbanairship.actions.Action.Situation
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionRunRequest
import com.urbanairship.actions.ActionValue
import com.urbanairship.analytics.Analytics
import com.urbanairship.analytics.InteractiveNotificationEvent
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Processes notification intents.
 */
internal class NotificationIntentProcessor(
    private val context: Context,
    private val intent: Intent,
    private val analytics: Analytics = Airship.analytics,
    private val pushManager: PushManager = Airship.push,
    private val autoLaunchApplication: Boolean = Airship.airshipConfigOptions.autoLaunchApplication,
) {
    private val actionButtonInfo = NotificationActionButtonInfo.fromIntent(intent)
    private val notificationInfo = NotificationInfo.fromIntent(intent)

    /**
     * Processes the intent synchronously, returning a pending result that completes immediately.
     *
     * @return A pending result. The result will be `true` if the intent was processed, otherwise
     * `false`.
     */
    @MainThread
    fun process(): PendingResult<Boolean> {
        val pendingResult = PendingResult<Boolean>()

        pendingResult.setResult(processInternal())

        return pendingResult
    }

    /**
     * Processes the intent.
     *
     * @return `true` if the intent was processed, otherwise `false`.
     */
    @MainThread
    fun processInternal(): Boolean {
        if (intent.action == null || notificationInfo == null) {
            UALog.e("NotificationIntentProcessor - invalid intent %s", intent)
            return false
        }

        val result: Boolean
        UALog.v("Processing intent: %s", intent.action)
        when (intent.action) {
            PushManager.ACTION_NOTIFICATION_RESPONSE -> {
                onNotificationResponse()
                result = true
            }

            PushManager.ACTION_NOTIFICATION_DISMISSED -> {
                onNotificationDismissed()
                result = true
            }

            else -> {
                UALog.e("NotificationIntentProcessor - Invalid intent action: %s", intent.action)
                result = false
            }
        }

        return result
    }

    /** Handles the opened notification without an action. */
    private fun onNotificationResponse() {
        UALog.i("Notification response: $notificationInfo, $actionButtonInfo")
        if (notificationInfo == null) { return }

        if (actionButtonInfo == null || actionButtonInfo.isForeground) {
            // Set the conversion push id and metadata
            analytics.conversionSendId = notificationInfo.message.sendId
            analytics.conversionMetadata = notificationInfo.message.metadata
        }

        val listener = pushManager.notificationListener

        if (actionButtonInfo != null) {
            // Add the interactive notification event
            val event = InteractiveNotificationEvent(notificationInfo, actionButtonInfo)
            analytics.addEvent(event)

            // Dismiss the notification
            NotificationManagerCompat.from(context)
                .cancel(notificationInfo.notificationTag, notificationInfo.notificationId)

            if (actionButtonInfo.isForeground) {
                if (listener?.onNotificationForegroundAction(notificationInfo, actionButtonInfo) != true) {
                    launchApplication()
                }
            } else {
                listener?.onNotificationBackgroundAction(notificationInfo, actionButtonInfo)
            }
        } else {
            if (listener?.onNotificationOpened(notificationInfo) != true) {
                launchApplication()
            }
        }

        for (internalNotificationListener in pushManager.getInternalNotificationListeners()) {
            internalNotificationListener.onNotificationResponse(notificationInfo, actionButtonInfo)
        }

        runNotificationResponseActions()
    }
    /**
     * Handles notification dismissed intent.
     */
    private fun onNotificationDismissed() {
        UALog.i("Notification dismissed: $notificationInfo")

        (intent.extras?.get(PushManager.EXTRA_NOTIFICATION_DELETE_INTENT) as PendingIntent?)?.let {
            try {
                it.send()
            } catch (e: PendingIntent.CanceledException) {
                UALog.d("Failed to send notification's deleteIntent, already canceled.")
            }
        }

        if (notificationInfo != null) {
            pushManager.notificationListener?.onNotificationDismissed(notificationInfo)
        }
    }

    /**
     * Helper method that attempts to launch the application's launch intent.
     */
    private fun launchApplication() {
        (intent.extras?.get(PushManager.EXTRA_NOTIFICATION_CONTENT_INTENT) as PendingIntent?)?.let {
            try {
                it.send()
            } catch (e: PendingIntent.CanceledException) {
                UALog.d("Failed to send notification's contentIntent, already canceled.")
            }

            return
        }

        if (!autoLaunchApplication) {
            return
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launchIntent != null) {
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            launchIntent.putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, notificationInfo?.message?.getPushBundle())
            launchIntent.setPackage(null)
            UALog.i("Starting application's launch intent.")
            context.startActivity(launchIntent)
        } else {
            UALog.i("Unable to launch application. Launch intent is unavailable.")
        }
    }

    /** Helper method to run the actions. */
    private fun runNotificationResponseActions() {
        var actions: Map<String, ActionValue>? = null
        var situation = Situation.MANUAL_INVOCATION
        val metadata = bundleOf(ActionArguments.PUSH_MESSAGE_METADATA to notificationInfo?.message)

        if (actionButtonInfo != null) {
            val actionPayload = intent.getStringExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ACTIONS_PAYLOAD)
            if (!actionPayload.isNullOrEmpty()) {
                actions = parseActionValues(actionPayload)

                actionButtonInfo.remoteInput?.let {
                    metadata.putBundle(ActionArguments.REMOTE_INPUT_METADATA, it)
                }

                situation = when(actionButtonInfo.isForeground) {
                    true -> Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON
                    false -> Situation.BACKGROUND_NOTIFICATION_ACTION_BUTTON
                }
            }
        } else {
            situation = Situation.PUSH_OPENED
            actions = notificationInfo?.message?.actions
        }

        if (actions.isNullOrEmpty()) {
            return
        }

        runActions(actions, situation, metadata)
    }

    /**
     * Helper method to run actions.
     *
     * @param actions The actions payload.
     * @param situation The situation.
     * @param metadata The metadata.
     */
    private fun runActions(
        actions: Map<String, ActionValue>,
        situation: Situation,
        metadata: Bundle
    ) {
        try {
            actions.forEach { (key, value) ->
                ActionRunRequest.createRequest(key)
                    .setMetadata(metadata)
                    .setSituation(situation)
                    .setValue(value)
                    .run()
            }
        } catch (ex: Exception) {
            UALog.e(ex, "Failed to run actions")
        }
    }

    /**
     * Parses an action payload.
     *
     * @param payload The payload.
     * @return The parsed actions.
     */
    private fun parseActionValues(payload: String): Map<String, ActionValue> {
        return try {
            JsonValue.parseString(payload).map
                ?.associate { it.key to ActionValue(it.value) }
                ?: emptyMap()
        } catch (e: JsonException) {
            UALog.e(e, "Failed to parse actions for push.")
            emptyMap()
        }
    }
}
