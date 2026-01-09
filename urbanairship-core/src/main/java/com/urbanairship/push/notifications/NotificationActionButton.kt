/* Copyright Airship and Contributors */
package com.urbanairship.push.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.annotation.RestrictTo
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.os.bundleOf
import androidx.core.text.HtmlCompat
import com.urbanairship.push.NotificationProxyActivity
import com.urbanairship.push.NotificationProxyReceiver
import com.urbanairship.push.PushManager
import com.urbanairship.util.PendingIntentCompat
import java.util.UUID

/**
 * Model object encapsulating the data relevant to a notification action button.
 */
public class NotificationActionButton private constructor(
    builder: Builder,
    private val extras: Bundle
) {

    /**
     * The button's ID.
     */
    @JvmField
    public val id: String = builder.buttonId

    private val labelId = builder.labelId
    private val label = builder.label

    /**
     * Flag that indicates if it is a foreground action.
     */
    @JvmField
    public val isForegroundAction: Boolean = builder.isForegroundAction

    /**
     * The button's icon.
     */
    @DrawableRes
    public val icon: Int = builder.iconId

    /**
     * The button's description.
     */
    public val description: String? = builder.description
    private val remoteInputs = builder.remoteInputs

    /**
     * Gets the button's label.
     *
     * @return The button's label.
     */
    public fun getLabel(context: Context): String? {
        if (label != null) {
            return label
        }
        if (labelId != 0) {
            return context.getString(labelId)
        }
        return null
    }

    /**
     * Gets the button's extras.
     *
     * @return The extras as a Bundle.
     */
    public fun getExtras(): Bundle {
        return Bundle(extras)
    }

    /**
     * Gets the remote inputs.
     *
     * @return A list of remote inputs.
     */
    public fun getRemoteInputs(): List<LocalizableRemoteInput> {
        return remoteInputs.toList()
    }

    /**
     * Creates the notification action.
     *
     * @param context The application context.
     * @param actionsPayload The actions payload for the interactive buttons.
     * @param arguments The notification arguments.
     * @return The action as a NotificationCompat.Action
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun createAndroidNotificationAction(
        context: Context,
        actionsPayload: String?,
        arguments: NotificationArguments
    ): NotificationCompat.Action {
        val label = getLabel(context) ?: ""

        val actionDescription = description ?: label

        val actionPendingIntent: PendingIntent

        val intent = Intent(PushManager.ACTION_NOTIFICATION_RESPONSE)
            .addCategory(UUID.randomUUID().toString())
            .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, arguments.message.getPushBundle())
            .putExtra(PushManager.EXTRA_NOTIFICATION_ID, arguments.notificationId)
            .putExtra(PushManager.EXTRA_NOTIFICATION_TAG, arguments.notificationTag)
            .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID, id)
            .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ACTIONS_PAYLOAD, actionsPayload)
            .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, isForegroundAction)
            .putExtra(PushManager.EXTRA_NOTIFICATION_ACTION_BUTTON_DESCRIPTION, actionDescription)

        // If remote inputs are present, create a mutable PendingIntent so that the underlying intent can be modified.
        val flags = if (remoteInputs.isEmpty()) 0 else PendingIntentCompat.FLAG_MUTABLE

        if (isForegroundAction) {
            intent.setClass(context, NotificationProxyActivity::class.java)
            actionPendingIntent = PendingIntentCompat.getActivity(context, 0, intent, flags)
        } else {
            intent.setClass(context, NotificationProxyReceiver::class.java)
            actionPendingIntent = PendingIntentCompat.getBroadcast(context, 0, intent, flags)
        }

        val actionBuilder = NotificationCompat.Action.Builder(
            icon, HtmlCompat.fromHtml(label, HtmlCompat.FROM_HTML_MODE_LEGACY), actionPendingIntent
        ).addExtras(extras)

        remoteInputs.forEach { input ->
            actionBuilder.addRemoteInput(input.createRemoteInput(context))
        }

        return actionBuilder.build()
    }

    /**
     * Builds the NotificationAction.
     */
    public class Builder public constructor(
        internal val buttonId: String
    ) {

        public var labelId: Int = 0
            private set
        public var iconId: Int = 0
            private set
        public var isForegroundAction: Boolean = true
            private set
        public val remoteInputs: MutableList<LocalizableRemoteInput> = mutableListOf()
        private val extenders = mutableListOf<NotificationCompat.Action.Extender>()
        public var description: String? = null
            private set
        public var label: String? = null
            private set

        /**
         * Set the label from a string resource.
         *
         * @param labelId An int value.
         * @return The builder instance.
         */
        public fun setLabel(@StringRes labelId: Int): Builder {
            return this.also {
                it.labelId = labelId
                it.label = null
            }
        }

        /**
         * Set the label.
         *
         * @param label The label.
         * @return The builder instance.
         */
        public fun setLabel(label: String?): Builder {
            return this.also {
                it.labelId = 0
                it.label = label
            }
        }

        /**
         * Sets the description of the action. Used for analytics.
         *
         * @param description The action description.
         * @return The builder with the description set.
         */
        public fun setDescription(description: String?): Builder {
            return this.also { it.description = description }
        }

        /**
         * Set the icon.
         *
         *
         * Note: All notification icons should be entirely white on a transparent background.
         *
         * @param iconId An int value.
         * @return The builder with the iconId value set.
         */
        public fun setIcon(@DrawableRes iconId: Int): Builder {
            return this.also { it.iconId = iconId }
        }

        /**
         * Set the isForegroundAction flag. Defaults to true.
         *
         * @param isForegroundAction A boolean value.
         * @return The builder with the isForegroundAction value set.
         */
        public fun setPerformsInForeground(isForegroundAction: Boolean): Builder {
            return this.also { it.isForegroundAction = isForegroundAction }
        }

        /**
         * Add a remoteInput.
         *
         * @param remoteInput A LocalizableRemoteInput value.
         * @return The builder with the remoteInput value set.
         */
        public fun addRemoteInput(remoteInput: LocalizableRemoteInput): Builder {
            return this.also { remoteInputs.add(remoteInput) }
        }

        /**
         * Extends the notification action.
         *
         * @param extender A NotificationCompat.Action.Extender value.
         * @return The builder with the extender value added.
         */
        public fun extend(extender: NotificationCompat.Action.Extender): Builder {
            return this.also { extenders.add(extender) }
        }

        /**
         * Builds and return the notification action.
         *
         * @return The notification action.
         */
        public fun build(): NotificationActionButton {
            if (extenders.isEmpty()) {
                return NotificationActionButton(this, bundleOf())
            }

            val builder = NotificationCompat.Action.Builder(iconId, null, null)
            extenders.forEach { builder.extend(it) }
            val extras = builder.build().extras

            return NotificationActionButton(this, extras)
        }
    }

    public companion object {

        /**
         * Creates a new Builder from a button ID.
         *
         * @param buttonId The button identifier.
         * @return A new builder.
         */
        @JvmStatic
        public fun newBuilder(buttonId: String): Builder {
            return Builder(buttonId)
        }
    }
}
