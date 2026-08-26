package com.urbanairship.liveupdate

import android.content.Context
import android.content.Intent
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
import com.urbanairship.liveupdate.data.LiveUpdateContent
import com.urbanairship.liveupdate.data.LiveUpdateDao
import com.urbanairship.liveupdate.data.LiveUpdateState
import com.urbanairship.liveupdate.notification.LiveUpdateNotificationReceiver
import com.urbanairship.liveupdate.notification.LiveUpdatePayload
import com.urbanairship.liveupdate.notification.NotificationTimeoutCompat
import com.urbanairship.push.NotificationProxyActivity
import com.urbanairship.push.PushManager
import com.urbanairship.push.PushMessage
import com.urbanairship.util.Clock
import com.urbanairship.util.PendingIntentCompat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours
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
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    /** Dispatcher that handler callbacks are invoked on. Injectable so tests can drive them. */
    private val handlerDispatcher: CoroutineDispatcher = Dispatchers.Default,
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
     * Ends any Live Update that has been inactive for longer than [MAX_INACTIVITY] *and* no longer
     * has a notification in the shade.
     *
     * Both conditions are required, and neither is sufficient alone:
     *
     * Inactivity alone is not enough, because a Live Update whose notification is still displayed
     * can still be refreshed by a later update, so there is nothing to clean up.
     *
     * A missing notification alone is also not enough. It may not have been posted yet, been
     * dropped by an app upgrade, per-app notification cap, or because notifications are disabled.
     *
     * Live Updates handled by a [CustomLiveUpdateHandler] are skipped entirely: they own their own
     * presentation, so we can't reason about whether they are stale.
     */
    fun endStaleLiveUpdates() {
        scope.launch {
            val now = clock.currentTimeMillis()

            // Cheap, local checks first. The notification snapshot is only read once we know
            // something is actually stale, which also means it can never be captured before the
            // query that decides which Live Updates we care about.
            val stale = dao.getAllActive().filter { (state, content) ->
                handlers[state.type] is NotificationLiveUpdateHandler &&
                        now - lastActivityAt(state, content) > MAX_INACTIVITY.inWholeMilliseconds
            }

            if (stale.isEmpty()) {
                return@launch
            }

            val activeNotifications = try {
                notificationManager.activeNotifications.map { it.tag }
            } catch (e: Exception) {
                // Known to throw on some OEM builds. Without a snapshot we cannot tell whether
                // these are still displayed, so leave them for the next launch.
                UALog.w(e) { "Unable to query active notifications. Skipping Live Update cleanup." }
                return@launch
            }

            stale
                .filter { (state, _) ->
                    notificationTag(state.type, state.name) !in activeNotifications
                }
                .forEach { (state, content) ->
                    UALog.v {
                        "Ending stale Live Update '${state.name}': inactive for " +
                                "${now - lastActivityAt(state, content)}ms with no notification " +
                                "displayed (tag=${notificationTag(state.type, state.name)})."
                    }
                    stop(state.name, content?.content, state.timestamp, state.dismissalDate)
                }
        }
    }

    /** The last time we heard anything at all about this Live Update. */
    private fun lastActivityAt(state: LiveUpdateState, content: LiveUpdateContent?): Long =
        maxOf(state.timestamp, content?.timestamp ?: 0L)

    private suspend fun handleCallback(callback: HandlerCallback) {
        val (action, update, message) = callback
        val type = update.type
        val handler = handlers[type]
        if (handler == null) {
            UALog.e("No handler was registered to handle events for Live Update type: $type!")
            return
        }

        when (handler) {
            is SuspendLiveUpdateNotificationHandler -> withContext(handlerDispatcher) {
                val result = handler.onUpdate(context, action, update)
                handleResult(action, result, handler, update, message)
            }
            is CallbackLiveUpdateNotificationHandler -> withContext(handlerDispatcher) {
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
            is CallbackLiveUpdateCustomHandler -> withContext(handlerDispatcher) {
                handler.onUpdate(context, action, update, object : CallbackLiveUpdateCustomHandler.LiveUpdateResultCallback {
                    override fun ok() {
                        handleResult(action, LiveUpdateResult.ok<Nothing>(), handler, update, message)
                    }

                    override fun cancel() {
                        handleResult(action, LiveUpdateResult.cancel<Nothing>(), handler, update, message)
                    }
                })
            }
            is SuspendLiveUpdateCustomHandler -> withContext(handlerDispatcher) {
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
                    stopUnlessAlreadyEnded(action, update, message)
                    cancelNotification(update.notificationTag)
                }
            }
            is CustomLiveUpdateHandler -> when (result) {
                is LiveUpdateResult.Ok -> {
                    // No-op. Custom handlers are responsible doing something with the update.
                }
                is LiveUpdateResult.Cancel -> {
                    stopUnlessAlreadyEnded(action, update, message = null)
                }
            }
        }

        return null
    }

    /**
     * Stops the Live Update in response to a handler cancelling it, unless it has already ended.
     *
     * An [LiveUpdateEvent.END] callback is itself the product of a stop, or of `clearAll`, so there
     * is nothing left to stop. Enqueueing another would log a warning on every ordinary end — and
     * if the Live Update has since been restarted under the same name, the redundant stop can end
     * the new one: [LiveUpdateProcessor.processStop] only rejects it while the Live Update is
     * inactive, and its staleness check compares the restart's timestamp against the current time,
     * which is always later.
     */
    private fun stopUnlessAlreadyEnded(
        action: LiveUpdateEvent,
        update: LiveUpdate,
        message: PushMessage?
    ) {
        if (action == LiveUpdateEvent.END) {
            return
        }

        stop(update.name, update.content, clock.currentTimeMillis(), null, message)
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

        /**
         * How long a Live Update may go without any activity before it becomes eligible to be
         * ended by [endStaleLiveUpdates].
         *
         * Anchored to the iOS Live Activity ceiling (roughly 8 hours of updates plus 4 hours on
         * the Lock Screen) so that the two platforms bound Live Updates on comparable timescales.
         * Note this bounds *inactivity*, measured from the last event we saw for the Live Update —
         * it is deliberately not a reimplementation of ActivityKit's dismissal from start time, so
         * a Live Update that keeps receiving updates is never ended by us.
         */
        @VisibleForTesting
        internal val MAX_INACTIVITY = 12.hours
    }
}

private val LiveUpdate.notificationTag: String
    get() = notificationTag(type, name)

private val LiveUpdateProcessor.NotificationCancel.notificationTag: String
    get() = notificationTag(type, name)

private fun notificationTag(type: String, name: String) = "$type:$name"
