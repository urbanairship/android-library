/* Copyright Airship and Contributors */
package com.urbanairship.push

import android.app.Activity
import android.content.Context
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.core.util.Consumer
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PreferenceDataStore
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.SimpleActivityListener
import com.urbanairship.permission.PermissionDelegate
import com.urbanairship.permission.PermissionRequestResult
import com.urbanairship.permission.PermissionStatus
import com.urbanairship.permission.PermissionsActivity
import com.urbanairship.push.notifications.NotificationChannelRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The default permission delegate for notifications.
 */
internal class NotificationsPermissionDelegate @VisibleForTesting constructor(
    private val defaultChannelId: String,
    private val dataStore: PreferenceDataStore,
    private val notificationManager: AirshipNotificationManager,
    private val channelRegistry: NotificationChannelRegistry,
    private val activityMonitor: ActivityMonitor,
    private val permissionRequestDelegate: PermissionRequestDelegate
) : PermissionDelegate {

    internal fun interface PermissionRequestDelegate {
        fun requestPermissions(
            context: Context,
            permission: String,
            consumer: Consumer<PermissionRequestResult>
        )
    }

    constructor(
        defaultChannelId: String,
        dataStore: PreferenceDataStore,
        notificationManager: AirshipNotificationManager,
        channelRegistry: NotificationChannelRegistry,
        activityMonitor: ActivityMonitor
    ) : this(
        defaultChannelId,
        dataStore,
        notificationManager,
        channelRegistry,
        activityMonitor,
        PermissionRequestDelegate { context: Context, permission: String, consumer: Consumer<PermissionRequestResult> ->
            PermissionsActivity.requestPermission(context, permission, consumer)
        })

    override fun checkPermissionStatus(context: Context, callback: Consumer<PermissionStatus>) {
        val status = if (notificationManager.areNotificationsEnabled()) {
            PermissionStatus.GRANTED
        } else {
            when (notificationManager.promptSupport) {
                AirshipNotificationManager.PromptSupport.COMPAT,
                AirshipNotificationManager.PromptSupport.SUPPORTED -> {
                    if (dataStore.getBoolean(PROMPTED_KEY, false)) {
                        PermissionStatus.DENIED
                    } else {
                        PermissionStatus.NOT_DETERMINED
                    }
                }
                AirshipNotificationManager.PromptSupport.NOT_SUPPORTED -> PermissionStatus.DENIED
            }
        }

        callback.accept(status)
    }

    @MainThread
    override fun requestPermission(context: Context, callback: Consumer<PermissionRequestResult>) {
        if (notificationManager.areNotificationsEnabled()) {
            callback.accept(PermissionRequestResult.granted())
            return
        }

        when (notificationManager.promptSupport) {
            AirshipNotificationManager.PromptSupport.NOT_SUPPORTED -> {
                callback.accept(PermissionRequestResult.denied(true))
                return
            }
            AirshipNotificationManager.PromptSupport.COMPAT -> {
                dataStore.put(PROMPTED_KEY, true)
                if (notificationManager.areChannelsCreated()) {
                    callback.accept(PermissionRequestResult.denied(true))
                    return
                }

                val scope = CoroutineScope(AirshipDispatchers.IO)
                scope.launch {
                    channelRegistry.getNotificationChannel(defaultChannelId)
                }

                activityMonitor.addActivityListener(object : SimpleActivityListener() {
                    override fun onActivityResumed(activity: Activity) {
                        if (notificationManager.areNotificationsEnabled()) {
                            callback.accept(PermissionRequestResult.granted())
                        } else {
                            callback.accept(PermissionRequestResult.denied(false))
                        }
                        activityMonitor.removeActivityListener(this)
                    }
                })
                return
            }

            AirshipNotificationManager.PromptSupport.SUPPORTED -> {
                dataStore.put(PROMPTED_KEY, true)
                permissionRequestDelegate.requestPermissions(
                    context = context,
                    permission = POST_NOTIFICATION_PERMISSION,
                    consumer = callback)
            }
        }
    }

    companion object {
        private const val PROMPTED_KEY = "NotificationsPermissionDelegate.prompted"
        private const val POST_NOTIFICATION_PERMISSION = "android.permission.POST_NOTIFICATIONS"
    }
}
