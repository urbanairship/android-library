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
import com.urbanairship.Airship
import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionRunRequest
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.json.jsonMapOf
import com.urbanairship.push.notifications.NotificationArguments
import com.urbanairship.push.notifications.NotificationChannelCompat
import com.urbanairship.push.notifications.NotificationChannelUtils
import com.urbanairship.push.notifications.NotificationProvider
import com.urbanairship.push.notifications.NotificationResult
import com.urbanairship.util.PendingIntentCompat
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runnable that processes an incoming push.
 */
internal class IncomingPushRunnable private constructor(
    private val context: Context,
    private val message: PushMessage,
    private val providerClass: String,
    private val isLongRunning: Boolean,
    private val isProcessed: Boolean,
    private val pushManagerProvider: (Duration) -> PushManager?,
    private val notificationManager: NotificationManagerCompat,
    private val jobDispatcher: JobDispatcher,
    private val activityMonitor: ActivityMonitor
) {

    suspend fun run() {
        Autopilot.automaticTakeOff(context)

        val push = pushManagerProvider(
            if (isLongRunning) LONG_AIRSHIP_WAIT_TIME else AIRSHIP_WAIT_TIME
        )

        if (push == null) {
            UALog.e("Unable to process push, Airship is not ready. Make sure takeOff " +
                    "is called by either using autopilot or by calling takeOff in the application's onCreate method.")
            return
        }

        if (!message.isAccengagePush && !message.isAirshipPush) {
            UALog.d("Ignoring push: %s", message)
            return
        }

        if (checkProvider(push, providerClass)) {
            // If we've already processed the push, proceed to notification display
            if (isProcessed) {
                postProcessPush(push)
            } else {
                processPush(push)
            }
        }
    }

    private fun processPush(push: PushManager) {
        UALog.i("Processing push: $message")

        if (!push.isPushEnabled) {
            UALog.d("Push disabled, ignoring message")
            return
        }

        if (!push.isUniqueCanonicalId(message.canonicalPushId)) {
            UALog.d("Received a duplicate push with canonical ID: %s", message.canonicalPushId)
            return
        }

        if (message.isExpired) {
            UALog.d("Received expired push message, ignoring.")
            return
        }

        if (message.isPing || message.isRemoteDataUpdate) {
            UALog.v("Received internal push.")
            push.onPushReceived(message, false)
            return
        }

        if (!message.isChannelIdNullOrMatching()) {
            UALog.d { "Received push message for another channel ID, ignoring." }
            return
        }

        // Run the push actions
        runActions()

        // Set last received metadata
        push.lastReceivedMetadata = message.metadata

        // Finish processing the push
        postProcessPush(push)
    }

    @Throws(IllegalArgumentException::class)
    private fun postProcessPush(push: PushManager) {
        if (!push.isOptIn) {
            UALog.i(
                "User notifications opted out. Unable to display notification for message: $message",
            )
            postProcessPushFinished(push, message, false)
            return
        }

        if (activityMonitor.isAppForegrounded) {
            if (!message.isForegroundDisplayable) {
                UALog.i(
                    "Push message flagged as not able to be displayed in the foreground: $message",
                )
                postProcessPushFinished(push, message, false)
                return
            }

            val displayForegroundPredicate = push.foregroundNotificationDisplayPredicate
            if (displayForegroundPredicate?.apply(message) == false) {
                UALog.i(
                    "Foreground notification display predicate prevented the display of message: $message",
                )
                postProcessPushFinished(push, message, false)
                return
            }
        }

        val provider = getNotificationProvider(push) ?: run {
            UALog.e(
                "Notification provider is null. Unable to display notification for message: $message",
            )
            postProcessPushFinished(push, message, false)
            return
        }

        val arguments = try {
            provider.onCreateNotificationArguments(context, message)
        } catch (e: Exception) {
            UALog.e(e, "Failed to generate notification arguments for message. Skipping.")
            postProcessPushFinished(push, message, false)
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

                val notificationChannel = getNotificationChannel(push, notification, arguments)

                if (notificationChannel != null) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        NotificationChannelUtils.applyLegacySettings(
                            notification,
                            notificationChannel
                        )
                    }
                } else {
                    UALog.e("Missing required notification channel. Notification will most likely not display.")
                }

                // Notify the provider the notification was created
                provider.onNotificationCreated(context, notification, arguments)

                // Post the notification
                val posted = postNotification(notification, arguments)

                postProcessPushFinished(push, message, posted)

                if (posted) {
                    push.onNotificationPosted(
                        message, arguments.notificationId, arguments.notificationTag
                    )
                }
            }

            NotificationResult.Status.CANCEL -> postProcessPushFinished(push, message, false)

            NotificationResult.Status.RETRY -> {
                UALog.d("Scheduling notification to be retried for a later time: %s", message)
                reschedulePush(message)
            }
        }
    }

    private fun postProcessPushFinished(
        push: PushManager,
        message: PushMessage,
        notificationPosted: Boolean
    ) = push.onPushReceived(message, notificationPosted)

    private fun getNotificationProvider(push: PushManager): NotificationProvider? {
        if (message.isAirshipPush) {
            return push.notificationProvider
        }

        return null
    }

    private fun getNotificationChannel(
        push: PushManager,
        notification: Notification,
        arguments: NotificationArguments
    ): NotificationChannelCompat? {
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.getChannelId(notification)
        } else {
            arguments.notificationChannelId
        }

        return channelId?.let {
            push.notificationChannelRegistry.getNotificationChannelSync(it)
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
     * @param push The [PushManager] instance.
     * @param providerClass The provider class.
     * @return `true` if the message should be processed, otherwise `false`.
     */
    private fun checkProvider(push: PushManager, providerClass: String?): Boolean {
        val provider = push.pushProvider

        if (provider == null || provider.javaClass.toString() != providerClass) {
            UALog.e("Received message callback from unexpected provider %s. Ignoring.", providerClass)
            return false
        }

        if (!provider.isAvailable(context)) {
            UALog.e("Received message callback when provider is unavailable. Ignoring.")
            return false
        }

        if (!push.isPushAvailable || !push.isPushEnabled) {
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
            .setScope(PushManager::class.java.name)
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
    internal class Builder(
        context: Context,
        val pushManagerProvider: (Duration) -> PushManager? = {
            if (Airship.waitForReadyBlocking(duration = it)) {
                Airship.push
            } else {
                null
            }
        }
    ) {
        internal val context: Context = context.applicationContext
        var message: PushMessage? = null
            private set
        var providerClass: String? = null
            private set
        var isLongRunning = false
            private set
        var isProcessed = false
            private set
        var notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(context)
            private set
        var jobDispatcher: JobDispatcher = JobDispatcher.shared(context)
            private set
        var activityMonitor: ActivityMonitor = GlobalActivityMonitor.shared(context)
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
                context = context,
                message = message,
                providerClass = provider,
                isLongRunning = isLongRunning,
                isProcessed = isProcessed,
                pushManagerProvider = pushManagerProvider,
                notificationManager = notificationManager,
                jobDispatcher = jobDispatcher,
                activityMonitor = activityMonitor
            )
        }
    }

    companion object {
        private val AIRSHIP_WAIT_TIME = 5.seconds
        private val LONG_AIRSHIP_WAIT_TIME = 10.seconds
    }
}
