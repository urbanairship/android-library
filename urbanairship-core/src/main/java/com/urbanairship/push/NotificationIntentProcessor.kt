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
import com.urbanairship.UALog
import com.urbanairship.actions.Action.Situation
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionRunRequest
import com.urbanairship.actions.ActionValue
import com.urbanairship.analytics.Analytics
import com.urbanairship.analytics.InteractiveNotificationEvent
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.push.PushManager.Companion.EXTRA_NOTIFICATION_CONTENT_INTENT
import com.urbanairship.push.PushManager.Companion.EXTRA_NOTIFICATION_DELETE_INTENT
import com.urbanairship.util.getParcelableCompat
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Processes notification intents.
 *
 * The SDK is reached through providers rather than read during construction. Every `Airship`
 * accessor blocks the calling thread until takeOff completes, and both callers construct this on
 * the main thread, so reading them eagerly would block there. The providers are invoked only after
 * [waitForReady] has returned.
 */
internal class NotificationIntentProcessor(
    private val context: Context,
    private val intent: Intent,
    private val waitForReady: suspend (Duration?) -> Boolean = { Airship.waitForReady(it) },
    private val analyticsProvider: () -> Analytics = { Airship.analytics },
    private val pushProvider: () -> PushManager = { Airship.push },
    private val autoLaunchApplicationProvider: () -> Boolean = {
        Airship.airshipConfigOptions.autoLaunchApplication
    },
) {
    private val actionButtonInfo = NotificationActionButtonInfo.fromIntent(intent)
    private val notificationInfo = NotificationInfo.fromIntent(intent)

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    /**
     * Processes the notification intent.
     *
     * This method handles both notification responses (opening the notification or clicking an action)
     * and notification dismissals.
     *
     * Processing happens on the main thread using [Dispatchers.Main.immediate] to ensure
     * that activity launches are not blocked by background restrictions.
     *
     * @param readyTimeout Optional bound on the wait for the SDK to become ready. Reaching it
     * invokes the callback with a failure result. It leaves the processing that follows unbounded,
     * so notification actions are not cut short by a slow takeOff.
     * @param overallTimeout Optional bound on the whole operation, for callers that answer to a
     * hard platform deadline such as a broadcast. Reaching it cancels processing in place,
     * wherever it has got to, and invokes the callback with a failure result.
     * @param callback Callback to be invoked when processing is complete or fails.
     */
    @MainThread
    fun process(
        readyTimeout: Duration? = null,
        overallTimeout: Duration? = null,
        callback: (Result<Unit>) -> Unit
    ) {
        // Immediate main to avoid run loop hop so we can start activities without any background restrictions
        scope.launch {
            try {
                val result = if (overallTimeout != null) {
                    // withTimeout will throw if it reaches the timeout
                    withTimeout(overallTimeout) {
                        processInternal(readyTimeout)
                    }
                } else {
                    processInternal(readyTimeout)
                }
                callback(result)
            } catch(e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    private suspend fun processInternal(readyTimeout: Duration?): Result<Unit> {
        if (intent.action == null || notificationInfo == null) {
            return Result.failure(IllegalArgumentException("Invalid intent $intent"))
        }

        // Rides on the intent and needs nothing from the SDK, so a slow takeOff must not cost the
        // app the delete intent it attached to its own notification.
        if (intent.action == PushManager.ACTION_NOTIFICATION_DISMISSED) {
            sendDeleteIntent()
        }

        // Suspends instead of blocking.
        if (!waitForReady(readyTimeout)) {
            return Result.failure(IllegalStateException("Airship is not ready, unable to process $intent"))
        }

        UALog.v("Processing intent: %s", intent.action)
        return when (intent.action) {
            PushManager.ACTION_NOTIFICATION_RESPONSE -> {
                onNotificationResponse()
                Result.success(Unit)
            }

            PushManager.ACTION_NOTIFICATION_DISMISSED -> {
                onNotificationDismissed()
                Result.success(Unit)
            }

            else -> {
                return Result.failure(IllegalArgumentException("Invalid intent action ${intent.action}"))
            }
        }
    }

    /** Handles the opened notification without an action. */
    private suspend fun onNotificationResponse() {
        UALog.i("Notification response: $notificationInfo, $actionButtonInfo")
        if (notificationInfo == null) { return }

        val analytics = analyticsProvider()
        val pushManager = pushProvider()

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

        if (notificationInfo != null) {
            pushProvider().notificationListener?.onNotificationDismissed(notificationInfo)
        }
    }

    /** Forwards the delete intent the app attached to its own notification, if there is one. */
    private fun sendDeleteIntent() {
        intent.extras?.getParcelableCompat<PendingIntent>(EXTRA_NOTIFICATION_DELETE_INTENT)?.let {
            try {
                it.send()
            } catch (_: PendingIntent.CanceledException) {
                UALog.d("Failed to send notification's deleteIntent, already canceled.")
            }
        }
    }

    /**
     * Helper method that attempts to launch the application's launch intent.
     */
    private fun launchApplication() {
        intent.extras?.getParcelableCompat<PendingIntent>(EXTRA_NOTIFICATION_CONTENT_INTENT)?.let {
            try {
                it.send()
            } catch (_: PendingIntent.CanceledException) {
                UALog.d("Failed to send notification's contentIntent, already canceled.")
            }

            return
        }

        if (!autoLaunchApplicationProvider()) {
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
    private suspend fun runNotificationResponseActions() {
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
    private suspend fun runActions(
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
                    .runSuspending()
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
