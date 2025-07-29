package com.urbanairship.liveupdate

import android.app.NotificationManager
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.json.JsonMap
import com.urbanairship.liveupdate.CallbackLiveUpdateNotificationHandler.NotificationResult
import com.urbanairship.liveupdate.LiveUpdateProcessor.HandlerCallback
import com.urbanairship.liveupdate.LiveUpdateProcessor.Operation
import com.urbanairship.liveupdate.data.LiveUpdateDao
import com.urbanairship.liveupdate.notification.LiveUpdateNotificationReceiver
import com.urbanairship.liveupdate.notification.LiveUpdatePayload
import com.urbanairship.liveupdate.notification.NotificationTimeoutCompat
import com.urbanairship.push.NotificationProxyActivity
import com.urbanairship.push.PushManager
import com.urbanairship.push.PushMessage
import com.urbanairship.util.PendingIntentCompat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Manages Live Update handlers and an operation queue to process Live Update events. */
internal class LiveUpdateRegistrar(
    private val context: Context,
    private val channel: AirshipChannel,
    private val dao: LiveUpdateDao,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.IO,
    private val processor: LiveUpdateProcessor = LiveUpdateProcessor(dao),
    private val notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(context),
    private val notificationTimeoutCompat: NotificationTimeoutCompat = NotificationTimeoutCompat(context),
) {
    private val job = SupervisorJob()
    private val scope: CoroutineScope = CoroutineScope(dispatcher + job)
    @VisibleForTesting
    internal val handlers = ConcurrentHashMap<String, LiveUpdateHandler>()

    init {
        // Handle callbacks from the processor.
        processor.handlerCallbacks
            .onEach { handleCallback(it) }
            .launchIn(scope)

        // Handle notification cancel requests from the processor.
        processor.notificationCancels
            .onEach { cancelNotification(it.notificationTag) }
            .launchIn(scope)

        // Handle Channel updates from the processor.
        processor.channelUpdates
            .onEach { channel.trackLiveUpdateMutation(it) }
            .launchIn(scope)
    }

    fun register(type: String, handler: LiveUpdateHandler) {
        handlers[type] = handler
    }

    fun start(
        name: String,
        type: String,
        content: JsonMap,
        timestamp: Long,
        dismissalTimestamp: Long?,
        message: PushMessage? = null
    ) {
        val handler = handlers[type]
        if (handler == null) {
            UALog.e("Can't start Live Update '$name'. No handler registered for type '$type'!")
            return
        }

        processor.enqueue(
            Operation.Start(
                name = name,
                type = type,
                content = content,
                timestamp = timestamp,
                dismissalTimestamp = dismissalTimestamp,
                message = message
            )
        )
    }

    fun update(
        name: String,
        content: JsonMap,
        timestamp: Long,
        dismissalTimestamp: Long?,
        message: PushMessage? = null
    ) = processor.enqueue(
        Operation.Update(
            name = name,
            content = content,
            timestamp = timestamp,
            dismissalTimestamp = dismissalTimestamp,
            message = message
        )
    )

    fun stop(
        name: String,
        content: JsonMap?,
        timestamp: Long,
        dismissalTimestamp: Long?,
        message: PushMessage? = null
    ) = processor.enqueue(
        Operation.Stop(
            name = name,
            content = content,
            timestamp = timestamp,
            dismissalTimestamp = dismissalTimestamp,
            message = message
        )
    )

    fun cancel(name: String, timestamp: Long = System.currentTimeMillis()) =
        processor.enqueue(
            Operation.Cancel(name = name, timestamp = timestamp)
        )

    fun clearAll(timestamp: Long = System.currentTimeMillis()) =
        processor.enqueue(
            Operation.ClearAll(timestamp = timestamp)
        )

    fun onLiveUpdatePushReceived(message: PushMessage, payload: LiveUpdatePayload) {
        with(payload) {
            when (event) {
                LiveUpdateEvent.START -> if (type != null) {
                    start(name, type, content, timestamp, dismissalDate, message)
                } else {
                    UALog.w("Unable to start Live Update: $name. Missing required type!")
                }
                LiveUpdateEvent.END -> stop(name, content, timestamp, dismissalDate, message)
                LiveUpdateEvent.UPDATE -> update(name, content, timestamp, dismissalDate, message)
            }
        }
    }

    suspend fun getAllActiveUpdates(): List<LiveUpdate> {
        return dao
            .getAllActive()
            .mapNotNull { (state, content) ->
                content?.let { LiveUpdate.from(state, it) }
            }
    }

    /**
     * End any Live Updates notifications that are no longer displayed.
     * On API 21 and 22, the notification manager does not provide a way to query for
     * active notifications, so this method will no-op.
     */
    fun stopLiveUpdatesForClearedNotifications() {
        scope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val nm = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                val activeNotifications = nm.activeNotifications.map { it.tag }

                dao.getAllActive()
                    // Filter out any LUs that use custom handlers or have active notifications
                    .filter { (update, _) ->
                        handlers[update.type] is NotificationLiveUpdateHandler &&
                                notificationTag(update.type, update.name) !in activeNotifications
                    }
                    // End any Live Updates that are no longer displayed
                    .forEach { (update, content) ->
                        stop(update.name, content?.content, update.timestamp, update.dismissalDate)
                    }
            }
        }
    }

    private suspend fun handleCallback(callback: HandlerCallback) {
        val (action, update, message) = callback
        val type = update.type
        val handler = handlers[type]
        if (handler == null) {
            UALog.e("No handler was registered to handle events for Live Update type: $type!")
            return
        }

        when (handler) {
            is SuspendLiveUpdateNotificationHandler -> withContext(Dispatchers.Default) {
                val result = handler.onUpdate(context, action, update)
                handleResult(action, result, handler, update, message)
            }
            is CallbackLiveUpdateNotificationHandler -> withContext(Dispatchers.Default) {
                handler.onUpdate(context, action, update, object : CallbackLiveUpdateNotificationHandler.LiveUpdateResultCallback {
                    override fun ok(builder: NotificationCompat.Builder): NotificationResult? {
                        val result = LiveUpdateResult.ok(builder)
                        return handleResult(action, result, handler, update, message)
                    }

                    override fun cancel() {
                        handleResult(action, LiveUpdateResult.cancel<Nothing>(), handler, update, message)
                    }
                })
            }
            is CallbackLiveUpdateCustomHandler -> withContext(Dispatchers.Default) {
                handler.onUpdate(context, action, update, object : CallbackLiveUpdateCustomHandler.LiveUpdateResultCallback {
                    override fun ok() {
                        handleResult(action, LiveUpdateResult.ok<Nothing>(), handler, update, message)
                    }

                    override fun cancel() {
                        handleResult(action, LiveUpdateResult.cancel<Nothing>(), handler, update, message)
                    }
                })
            }
            is SuspendLiveUpdateCustomHandler -> withContext(Dispatchers.Default) {
                val result = handler.onUpdate(context, action, update)
                handleResult(action, result, handler, update, message)
            }
        }
    }

    private fun handleResult(
        action: LiveUpdateEvent,
        result: LiveUpdateResult<*>,
        handler: LiveUpdateHandler,
        update: LiveUpdate,
        message: PushMessage?
    ): NotificationResult? {
        when (handler) {
            is NotificationLiveUpdateHandler ->  when (result) {
                is LiveUpdateResult.Ok -> if (result.value is NotificationCompat.Builder) {
                    return postNotification(context, update, result.value, result.extender, message)
                }
                is LiveUpdateResult.Cancel -> {
                    stop(update.name, update.content, System.currentTimeMillis(), null, message)
                    cancelNotification(update.notificationTag)
                }
            }
            is CustomLiveUpdateHandler -> when (result) {
                is LiveUpdateResult.Ok -> {
                    // No-op. Custom handlers are responsible doing something with the update.
                }
                is LiveUpdateResult.Cancel -> {
                    stop(update.name, update.content, System.currentTimeMillis(), null, null)
                }
            }
        }

        return null
    }

    private fun postNotification(
        context: Context,
        update: LiveUpdate,
        builder: NotificationCompat.Builder,
        extender: LiveUpdateResult.NotificationExtender?,
        message: PushMessage?,
    ): NotificationResult? {
        // Set dismissal time on the notification, if the live update specifies one.
        update.dismissalTime?.let { dismissalTime ->
            notificationTimeoutCompat.setTimeoutAt(builder, dismissalTime, update.name)
        }

        val notification = builder.build()

        // If this live update event was triggered by a push, wrap the content intent so that we can
        // launch the proxy activity to handle the push open.
        if (message != null) {
            val contentIntent = Intent(context, NotificationProxyActivity::class.java)
                .setAction(PushManager.ACTION_NOTIFICATION_RESPONSE)
                .addCategory(UUID.randomUUID().toString())
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, message.getPushBundle())
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
                .putExtra(PushManager.EXTRA_NOTIFICATION_TAG, update.notificationTag)

            // Store existing content intent, if present, so we can forward to it to the proxy activity.
            notification.contentIntent?.let { original ->
                contentIntent.putExtra(PushManager.EXTRA_NOTIFICATION_CONTENT_INTENT, original)
            }
            // Set our content intent.
            notification.contentIntent = PendingIntentCompat.getActivity(context, 0, contentIntent, 0)
        }

        val deleteIntent = LiveUpdateNotificationReceiver.deleteIntent(context, update.name)
        // Store existing delete intent, if there is one, so we can forward to it in our receiver.
        notification.deleteIntent?.let { original ->
            deleteIntent.putExtra(PushManager.EXTRA_NOTIFICATION_DELETE_INTENT, original)
        }
        // Set our delete intent.
        notification.deleteIntent = PendingIntentCompat.getBroadcast(context, 0, deleteIntent, 0)

        UALog.d("Posting live update notification for: ${update.name}")

        try {
            val tag = update.notificationTag
            extender?.extend(notification, NOTIFICATION_ID, tag)
                ?.let { notificationManager.notify(tag, NOTIFICATION_ID, notification) }
                ?: notificationManager.notify(tag, NOTIFICATION_ID, notification)

            return NotificationResult(tag, NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            UALog.e(e, "Failed to post live update notification for: ${update.name}")
        }

        return null
    }

    private fun cancelNotification(tag: String) =
        notificationManager.cancel(tag, NOTIFICATION_ID)

    internal companion object {
        @VisibleForTesting
        internal const val NOTIFICATION_ID = 1010
    }
}

private val LiveUpdate.notificationTag: String
    get() = notificationTag(type, name)

private val LiveUpdateProcessor.NotificationCancel.notificationTag: String
    get() = notificationTag(type, name)

private fun notificationTag(type: String, name: String) = "$type:$name"
