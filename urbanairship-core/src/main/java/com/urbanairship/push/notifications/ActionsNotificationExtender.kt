/* Copyright Airship and Contributors */
package com.urbanairship.push.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import com.urbanairship.Airship

/**
 * Notification builder extender to add UA notification action buttons to a
 * notification.
 */
public class ActionsNotificationExtender public constructor(
    context: Context,
    private val arguments: NotificationArguments
) : NotificationCompat.Extender {

    private val context: Context = context.applicationContext

    override fun extend(builder: NotificationCompat.Builder): NotificationCompat.Builder {
        val group = arguments.message.interactiveNotificationType

        val actionGroup = Airship.push.getNotificationActionGroup(group)
            ?: return builder

        actionGroup
            .createAndroidActions(
                context = context,
                arguments = arguments,
                actionsPayload = arguments.message.interactiveActionsPayload)
            .forEach { builder.addAction(it) }

        return builder
    }
}
