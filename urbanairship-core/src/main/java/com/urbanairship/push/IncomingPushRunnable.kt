package com.urbanairship.push

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.bundleOf
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionRunRequest
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.json.jsonMapOf
import com.urbanairship.push.PushManager
import com.urbanairship.push.notifications.NotificationArguments
import com.urbanairship.push.notifications.NotificationChannelCompat
import com.urbanairship.push.notifications.NotificationChannelUtils
import com.urbanairship.push.notifications.NotificationProvider
import com.urbanairship.push.notifications.NotificationResult
import com.urbanairship.util.PendingIntentCompat
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * Runnable that processes an incoming push.
 */
internal class IncomingPushRunnable private constructor(
    private val message: PushMessage,
    private val providerClass: String,
    builder: Builder
) : Runnable {

    private val context = builder.context
    private val isLongRunning = builder.isLongRunning
    private val isProcessed = builder.isProcessed

    private val notificationManager = builder.notificationManager
        ?: NotificationManagerCompat.from(context)
    private val jobDispatcher = builder.jobDispatcher
        ?: JobDispatcher.shared(context)
    private val activityMonitor = builder.activityMonitor
        ?: GlobalActivityMonitor.shared(context)

    override fun run() {
        Autopilot.automaticTakeOff(context)

        val airshipWaitTime = if (isLongRunning) LONG_AIRSHIP_WAIT_TIME else AIRSHIP_WAIT_TIME
        val airship = UAirship.waitForTakeOff(airshipWaitTime.inWholeMilliseconds)

        if (airship == null) {
            UALog.e("Unable to process push, Airship is not ready. Make sure takeOff " +
                    "is called by either using autopilot or by calling takeOff in the application's onCreate method.")
            return
        }

        if (!message.isAccengagePush && !message.isAirshipPush) {
            UALog.d("Ignoring push: %s", message)
            return
        }

        if (checkProvider(airship, providerClass)) {
            // If we've already processed the push, proceed to notification display
            if (isProcessed) {
                postProcessPush(airship)
            } else {
                processPush(airship)
            }
        }
    }

    /**
     * Starts processing the push.
     *
     * @param airship The airship instance.
     */
    private fun processPush(airship: UAirship) {
        UALog.i("Processing push: $message")

        if (!airship.pushManager.isPushEnabled) {
            UALog.d("Push disabled, ignoring message")
            return
        }

        if (!airship.pushManager.isUniqueCanonicalId(message.canonicalPushId)) {
            UALog.d("Received a duplicate push with canonical ID: %s", message.canonicalPushId)
            return
        }

        if (message.isExpired) {
            UALog.d("Received expired push message, ignoring.")
            return
        }

        if (message.isPing || message.isRemoteDataUpdate) {
            UALog.v("Received internal push.")
            airship.pushManager.onPushReceived(message, false)
            return
        }

        // Run the push actions
        runActions()

        // Set last received metadata
        airship.pushManager.lastReceivedMetadata = message.metadata

        // Finish processing the push
        postProcessPush(airship)
    }

    /**
     * Finishes processing the push. This step builds the notification if applicable and
     * notifies the airship receiver if the notification was posted or cancelled.
     *
     * @param airship The airship instance.
     */
    @Throws(IllegalArgumentException::class)
    private fun postProcessPush(airship: UAirship) {
        if (!airship.pushManager.isOptIn) {
            UALog.i(
                "User notifications opted out. Unable to display notification for message: $message",
            )
            postProcessPushFinished(airship, message, false)
            return
        }

        if (activityMonitor.isAppForegrounded) {
            if (!message.isForegroundDisplayable) {
                UALog.i(
                    "Push message flagged as not able to be displayed in the foreground: $message",
                )
                postProcessPushFinished(airship, message, false)
                return
            }

            val displayForegroundPredicate = airship.pushManager.foregroundNotificationDisplayPredicate
            if (displayForegroundPredicate?.apply(message) == false) {
                UALog.i(
                    "Foreground notification display predicate prevented the display of message: $message",
                )
                postProcessPushFinished(airship, message, false)
                return
            }
        }

        val provider = getNotificationProvider(airship) ?: run {
            UALog.e(
                "Notification provider is null. Unable to display notification for message: $message",
            )
            postProcessPushFinished(airship, message, false)
            return
        }

        val arguments = try {
            provider.onCreateNotificationArguments(context, message)
        } catch (e: Exception) {
            UALog.e(e, "Failed to generate notification arguments for message. Skipping.")
            postProcessPushFinished(airship, message, false)
            return
        }

        if (!isLongRunning && arguments.requiresLongRunningTask) {
            UALog.d("Push requires a long running task. Scheduled for a later time: $message")
            reschedulePush(message)
            return
        }

        val result = try {
            provider.onCreateNotification(context, arguments)
        } catch (e: Exception) {
            UALog.e(e, "Cancelling notification display to create and display notification.")
            NotificationResult.cancel()
        }

        UALog.d("Received result status ${result.status} for push message: $message")

        when (result.status) {
            NotificationResult.Status.OK -> {
                val notification = result.notification
                    ?: throw IllegalArgumentException("Invalid notification result. Missing notification.")

                val notificationChannel = getNotificationChannel(airship, notification, arguments)

                // Apply legacy settings
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    if (notificationChannel != null) {
                        NotificationChannelUtils.applyLegacySettings(notification, notificationChannel)
                    } else {
                        applyDeprecatedSettings(airship, notification)
                    }
                } else if (notificationChannel == null) {
                    UALog.e("Missing required notification channel. Notification will most likely not display.")
                }

                // Notify the provider the notification was created
                provider.onNotificationCreated(context, notification, arguments)

                // Post the notification
                val posted = postNotification(notification, arguments)

                postProcessPushFinished(airship, message, posted)

                if (posted) {
                    airship.pushManager.onNotificationPosted(
                        message, arguments.notificationId, arguments.notificationTag
                    )
                }
            }

            NotificationResult.Status.CANCEL -> postProcessPushFinished(airship, message, false)

            NotificationResult.Status.RETRY -> {
                UALog.d("Scheduling notification to be retried for a later time: %s", message)
                reschedulePush(message)
            }
        }
    }

    private fun postProcessPushFinished(
        airship: UAirship,
        message: PushMessage,
        notificationPosted: Boolean
    ) = airship.pushManager.onPushReceived(message, notificationPosted)

    private fun getNotificationProvider(airship: UAirship): NotificationProvider? {
        if (message.isAirshipPush) {
            return airship.pushManager.notificationProvider
        }

        return null
    }

    /**
     * Applies deprecated sound, vibration, and quiet time settings to the notification.
     *
     * @param airship The airship instance.
     * @param notification The notification.
     */
    @Suppress("deprecation")
    private fun applyDeprecatedSettings(airship: UAirship, notification: Notification) {
        if (!airship.pushManager.isVibrateEnabled || airship.pushManager.isInQuietTime) {
            // Remove both the vibrate and the DEFAULT_VIBRATE flag
            notification.vibrate = null
            notification.defaults = notification.defaults and Notification.DEFAULT_VIBRATE.inv()
        }

        if (!airship.pushManager.isSoundEnabled || airship.pushManager.isInQuietTime) {
            // Remove both the sound and the DEFAULT_SOUND flag
            notification.sound = null
            notification.defaults = notification.defaults and Notification.DEFAULT_SOUND.inv()
        }
    }

    private fun getNotificationChannel(
        airship: UAirship,
        notification: Notification,
        arguments: NotificationArguments
    ): NotificationChannelCompat? {
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.getChannelId(notification)
        } else {
            arguments.notificationChannelId
        }

        return channelId?.let {
            airship.pushManager.notificationChannelRegistry.getNotificationChannelSync(it)
        }
    }

    /**
     * Posts the notification
     *
     * @param notification The notification.
     * @param arguments The notification arguments.
     */
    private fun postNotification(
        notification: Notification,
        arguments: NotificationArguments
    ): Boolean {
        val tag = arguments.notificationTag
        val id = arguments.notificationId

        val contentIntent = Intent(context, NotificationProxyActivity::class.java)
            .setAction(PushManager.ACTION_NOTIFICATION_RESPONSE)
            .addCategory(UUID.randomUUID().toString())
            .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, arguments.message.getPushBundle())
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            .putExtra(PushManager.EXTRA_NOTIFICATION_ID, arguments.notificationId)
            .putExtra(PushManager.EXTRA_NOTIFICATION_TAG, arguments.notificationTag)

        // If the notification already has an intent, add it to the extras to be sent later
        notification.contentIntent?.let {
            contentIntent.putExtra(PushManager.EXTRA_NOTIFICATION_CONTENT_INTENT, it)
        }

        val deleteIntent = Intent(context, NotificationProxyReceiver::class.java)
            .setAction(PushManager.ACTION_NOTIFICATION_DISMISSED)
            .addCategory(UUID.randomUUID().toString())
            .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, arguments.message.getPushBundle())
            .putExtra(PushManager.EXTRA_NOTIFICATION_ID, arguments.notificationId)
            .putExtra(PushManager.EXTRA_NOTIFICATION_TAG, arguments.notificationTag)

        notification.deleteIntent?.let {
            deleteIntent.putExtra(PushManager.EXTRA_NOTIFICATION_DELETE_INTENT, it)
        }

        notification.contentIntent = PendingIntentCompat.getActivity(context, 0, contentIntent, 0)
        notification.deleteIntent = PendingIntentCompat.getBroadcast(context, 0, deleteIntent, 0)

        UALog.i("Posting notification: %s id: %s tag: $tag", notification, id)
        try {
            notificationManager.notify(tag, id, notification)
            return true
        } catch (e: Exception) {
            UALog.e(e, "Failed to post notification.")
            return false
        }
    }

    /**
     * Runs all the push actions for message.
     */
    private fun runActions() {
        val metadata = bundleOf(ActionArguments.PUSH_MESSAGE_METADATA to message)

        message.actions.forEach { (name, value) ->
            ActionRunRequest.createRequest(name)
                .setMetadata(metadata)
                .setValue(value)
                .setSituation(Action.Situation.PUSH_RECEIVED)
                .run()
        }
    }

    /**
     * Checks if the message should be processed for the given provider.
     *
     * @param airship The airship instance.
     * @param providerClass The provider class.
     * @return `true` if the message should be processed, otherwise `false`.
     */
    private fun checkProvider(airship: UAirship, providerClass: String?): Boolean {
        val provider = airship.pushManager.pushProvider

        if (provider == null || provider.javaClass.toString() != providerClass) {
            UALog.e("Received message callback from unexpected provider %s. Ignoring.", providerClass)
            return false
        }

        if (!provider.isAvailable(context)) {
            UALog.e("Received message callback when provider is unavailable. Ignoring.")
            return false
        }

        if (!airship.pushManager.isPushAvailable || !airship.pushManager.isPushEnabled) {
            UALog.e("Received message when push is disabled. Ignoring.")
            return false
        }

        return true
    }

    /**
     * Reschedules the push to finish processing at a later time.
     *
     * @param message The push message.
     */
    private fun reschedulePush(message: PushMessage) {
        val jobInfo = JobInfo.newBuilder()
            .setAction(PushManager.ACTION_DISPLAY_NOTIFICATION)
            .setConflictStrategy(JobInfo.ConflictStrategy.APPEND)
            .setAirshipComponent(PushManager::class.java)
            .setExtras(
                jsonMapOf(
                    PushProviderBridge.EXTRA_PUSH to message,
                    PushProviderBridge.EXTRA_PROVIDER_CLASS to providerClass
                )
            )
            .build()

        jobDispatcher.dispatch(jobInfo)
    }

    /**
     * IncomingPushRunnable builder.
     */
    internal class Builder(context: Context) {

        internal val context: Context = context.applicationContext
        var message: PushMessage? = null
            private set
        var providerClass: String? = null
            private set
        var isLongRunning: Boolean = false
            private set
        var isProcessed: Boolean = false
            private set
        var notificationManager: NotificationManagerCompat? = null
            private set
        var jobDispatcher: JobDispatcher? = null
            private set
        var activityMonitor: ActivityMonitor? = null
            private set

        /**
         * Sets the push message.
         *
         * @param message The push message.
         * @return The builder instance.
         */
        fun setMessage(message: PushMessage): Builder {
            return this.also { it.message = message }
        }

        /**
         * Sets the provider class.
         *
         * @param providerClass The provider class.
         * @return The builder instance.
         */
        fun setProviderClass(providerClass: String): Builder {
            return this.also { it.providerClass = providerClass }
        }

        /**
         * Sets if the runnable is long running or not.
         *
         * @param longRunning If the runnable is long running or not.
         * @return The builder instance.
         */
        fun setLongRunning(longRunning: Boolean): Builder {
            return this.also { it.isLongRunning = longRunning }
        }

        /**
         * Sets if the push has been processed. If so, the runnable
         * will proceed directly to notification display.
         *
         * @param processed `true `If the push has been processed, otherwise
         * `false`.
         * @return The builder instance.
         */
        fun setProcessed(processed: Boolean): Builder {
            return this.also { it.isProcessed = processed }
        }

        /**
         * Sets the notification manager.
         *
         * @param notificationManager The notification manager.
         * @return The builder instance.
         */
        fun setNotificationManager(notificationManager: NotificationManagerCompat): Builder {
            return this.also { it.notificationManager = notificationManager }
        }

        /**
         * Sets the job dispatcher.
         *
         * @param jobDispatcher The job dispatcher.
         * @return The builder instance.
         */
        fun setJobDispatcher(jobDispatcher: JobDispatcher): Builder {
            return this.also { it.jobDispatcher = jobDispatcher }
        }

        /**
         * Sets the activity monitor.
         *
         * @param activityMonitor The activity monitor.
         * @return The builder instance.
         */
        fun setActivityMonitor(activityMonitor: ActivityMonitor): Builder {
            return this.also { it.activityMonitor = activityMonitor }
        }

        /**
         * Builds the runnable.
         *
         * @return A [IncomingPushRunnable].
         * @throws IllegalArgumentException if provider and/or push message is missing.
         */
        @Throws(IllegalArgumentException::class)
        fun build(): IncomingPushRunnable {
            val provider = providerClass ?: throw IllegalArgumentException("Provider class missing")
            val message = message ?: throw IllegalArgumentException("Push message missing")

            return IncomingPushRunnable(
                message = message,
                providerClass = provider,
                builder = this
            )
        }
    }

    companion object {
        private val AIRSHIP_WAIT_TIME = 5.seconds
        private val LONG_AIRSHIP_WAIT_TIME = 10.seconds
    }
}
