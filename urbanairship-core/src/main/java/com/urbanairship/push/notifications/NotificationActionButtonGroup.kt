/* Copyright Airship and Contributors */
package com.urbanairship.push.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

/**
 * Model object encapsulating the data relevant to a notification action button group.
 */
public class NotificationActionButtonGroup private constructor(
    private val actionButtons: List<NotificationActionButton>
) {

    /**
     * Gets the notification actions.
     */
    public val notificationActionButtons: List<NotificationActionButton>
        get() = actionButtons.toList()

    /**
     * Creates a list of Android notification actions.
     *
     * @param context The application context.
     * @param arguments The notification arguments.
     * @param actionsPayload The actions payload that defines the Airship actions for each
     * interactive notification action.
     * @return List of Android notification actions.
     */
    public fun createAndroidActions(
        context: Context,
        arguments: NotificationArguments,
        actionsPayload: String?
    ): List<NotificationCompat.Action> {

        // Run UA actions for the notification action
        val notificationActionMap = if (actionsPayload.isNullOrEmpty()) {
            null
        } else {
            try {
                JsonValue.parseString(actionsPayload).requireMap()
            } catch (e: JsonException) {
                UALog.e(e, "Failed to parse notification actions payload: $actionsPayload")
                null
            }
        }

        return notificationActionButtons
            .map { action ->
                val actions = notificationActionMap?.opt(action.id)?.toString()
                action.createAndroidNotificationAction(context, actions, arguments)
            }
    }

    /**
     * Builds the NotificationActionButtonGroup.
     */
    public class Builder public constructor() {

        private val actionButtons = mutableListOf<NotificationActionButton>()

        /**
         * Adds a notification action button.
         *
         * @param action The notification action button to add.
         * @return The builder to allow method chaining.
         */
        public fun addNotificationActionButton(action: NotificationActionButton): Builder {
            return this.also { it.actionButtons.add(action) }
        }

        /**
         * Builds and returns the [NotificationActionButtonGroup].
         *
         * @return The [NotificationActionButtonGroup].
         */
        public fun build(): NotificationActionButtonGroup {
            return NotificationActionButtonGroup(actionButtons)
        }
    }

    public companion object {

        /**
         * Builder factory method.
         *
         * @return A new builder instance.
         */
        @JvmStatic
        public fun newBuilder(): Builder {
            return Builder()
        }
    }
}
