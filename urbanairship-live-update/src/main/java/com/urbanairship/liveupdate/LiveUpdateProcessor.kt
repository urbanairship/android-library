package com.urbanairship.liveupdate

import androidx.annotation.VisibleForTesting
import com.urbanairship.Logger
import com.urbanairship.json.JsonMap
import com.urbanairship.liveupdate.api.LiveUpdateMutation
import com.urbanairship.liveupdate.data.LiveUpdateContent
import com.urbanairship.liveupdate.data.LiveUpdateDao
import com.urbanairship.liveupdate.data.LiveUpdateState
import com.urbanairship.push.PushMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal class LiveUpdateProcessor(
    private val dao: LiveUpdateDao,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.newSingleThreadDispatcher(),
) {
    private val scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob())

    private val callbacks = Channel<HandlerCallback>(Channel.UNLIMITED)
    val handlerCallbacks = callbacks.receiveAsFlow().flowOn(Dispatchers.Default)

    internal data class NotificationCancel(val type: String, val name: String)

    private val cancels = Channel<NotificationCancel>(Channel.UNLIMITED)
    val notificationCancels = cancels.receiveAsFlow().flowOn(Dispatchers.Default)

    private val updates = Channel<LiveUpdateMutation>(Channel.UNLIMITED)
    val channelUpdates = updates.receiveAsFlow().flowOn(Dispatchers.Default)

    private var processJob: Job? = null
    private val operationQueue = Channel<Operation>(Channel.UNLIMITED)

    @VisibleForTesting
    internal val isProcessing: Boolean
        get() = processJob?.isActive == true

    internal fun enqueue(operation: Operation) {
        operationQueue.trySend(operation)
        tryStartProcessing()
    }

    private fun tryStartProcessing() {
        // Bail out if we're already processing.
        if (processJob != null && processJob?.isActive == true) {
            return
        }

        // We don't check to see if there are any active Live Updates or registered handlers
        // here, as we want to be able to process and store any incoming updates that may occur
        // before a Live Update is started. This makes sure any out of order updates are still
        // handled appropriately.

        processJob = scope.launch {
            Logger.verbose("Live Update processor started.")
            for (operation in operationQueue) {
                process(operation)
            }
            Logger.verbose("Live Update processor finished.")
        }
    }

    private suspend fun tryStopProcessing() {
        @OptIn(ExperimentalCoroutinesApi::class)
        val isQueueEmpty = operationQueue.isEmpty

        if (isQueueEmpty && !dao.isAnyActive()) {
            processJob?.cancel()
            processJob = null
            Logger.verbose("Live Update processor stopped.")
        }
    }

    private suspend fun process(operation: Operation) {
        when (operation) {
            is Operation.Start -> processStart(operation)
            is Operation.Update -> processUpdate(operation)
            is Operation.Stop -> processStop(operation)
            is Operation.Cancel -> processCancel(operation)
            is Operation.ClearAll -> processClearAll()
        }
    }

    private suspend fun processStart(operation: Operation.Start): Unit = with(operation) {
        val state = dao.getState(name)

        // Check timestamp, as we may have received a stale start.
        val lastTimestamp = state?.timestamp ?: 0
        if (lastTimestamp > timestamp) {
            Logger.warn("Ignored start for Live Update '$name'. Start event was stale.")
            return
        }

        // If already started with a different type, process a stop and then re-start.
        if (state?.isActive == true && state.type != type) {
            enqueue(Operation.Stop(name = name, timestamp = timestamp))
            enqueue(operation)
            return
        }

        // If already started, ignore the start.
        if (state?.isActive == true) {
            Logger.warn("Ignored start for Live Update '$name'. Already started.")
            return
        }

        val startedState = LiveUpdateState(
            name = name,
            type = type,
            timestamp = timestamp,
            dismissalDate = dismissalTimestamp,
            isActive = true,
        )

        val startedContent = LiveUpdateContent(
            name = name,
            content = content,
            timestamp = timestamp
        )

        dao.upsert(startedState, startedContent)

        // Update Channel.
        updates.trySend(LiveUpdateMutation.Set(name = name, startTime = timestamp))

        // Notify handler of the start.
        val update = LiveUpdate.from(startedState, startedContent)
        callbacks.trySend(
            HandlerCallback(LiveUpdateEvent.START, update, operation.message)
        )
    }

    private suspend fun processUpdate(operation: Operation.Update): Unit = with(operation) {
        val liveUpdate = dao.get(name)
        val lastTimestamp = liveUpdate?.content?.timestamp ?: -1

        if (lastTimestamp > timestamp) {
            Logger.verbose("Ignoring stale Live Update content for '$name': $content")
            return
        }

        // Update the dismissal date, if present in the payload.
        val updatedState = liveUpdate?.state?.copy(
            dismissalDate = dismissalTimestamp ?: liveUpdate.state.dismissalDate
        )
        val updateContent = LiveUpdateContent(name, content, timestamp)
        dao.upsert(updatedState, updateContent)

        // Notify handlers of the update if the update is started.
        if (liveUpdate?.state?.isActive == true) {
            val update = LiveUpdate.from(liveUpdate.state, updateContent)
            callbacks.trySend(HandlerCallback(LiveUpdateEvent.UPDATE, update, operation.message))
        } else {
            Logger.warn("Ignoring Live Update for '$name'. Live Update is not started!")
        }
    }

    private suspend fun processStop(operation: Operation.Stop): Unit = with(operation) {
        try {
            val liveUpdate = dao.get(name)
            val lastState = liveUpdate?.state
            val lastContent = liveUpdate?.content

            if (lastState == null || lastContent == null || !lastState.isActive) {
                Logger.warn("Ignored stop for Live Update '$name'. Live Update is not started!")
                return
            }

            val lastTimestamp = lastState.timestamp
            if (lastTimestamp > timestamp) {
                Logger.verbose("Ignored stop for Live Update '$name'. Stop event was stale.")
                return
            }

            val updatedState = lastState.copy(
                isActive = false,
                timestamp = timestamp,
                dismissalDate = dismissalTimestamp ?: lastState.dismissalDate
            )

            val updatedContent = if (content != null) {
                lastContent.copy(content = content, timestamp = timestamp)
            } else {
                lastContent
            }

            dao.upsert(updatedState, updatedContent)

            // Update Channel.
            updates.trySend(LiveUpdateMutation.Remove(name = name, startTime = lastTimestamp))

            val updated = LiveUpdate.from(updatedState, updatedContent)

            // Notify the handler of the stop, so it can handle cleaning up.
            callbacks.trySend(HandlerCallback(LiveUpdateEvent.END, updated, operation.message))

            // Clean up content.
            dao.deleteContent(name)
        } finally {
            // Stop if there's nothing else to do at the moment.
            tryStopProcessing()
        }
    }

    private suspend fun processCancel(operation: Operation.Cancel) = with(operation) {
        val state = dao.getState(name)
        state?.let {
            cancels.trySend(NotificationCancel(type = state.type, name = name))
        }
    }

    private suspend fun processClearAll() {
        // Notify handlers that we're stopping all tracked Live Updates.
        dao.getAllActive()
            .mapNotNull {
                it.content?.let { content -> LiveUpdate.from(it.state, content) }
            }
            .forEach { update ->
                callbacks.trySend(
                    HandlerCallback(LiveUpdateEvent.END, update, message = null)
                )
            }

        // Clear all data.
        dao.deleteAll()

        // Stop.
        tryStopProcessing()
    }

    @VisibleForTesting
    internal sealed class Operation {
        abstract val timestamp: Long

        /** Start Live Updates for the given [name], and [type], with initial [content]. */
        data class Start(
            val name: String,
            val type: String,
            val content: JsonMap,
            override val timestamp: Long,
            val dismissalTimestamp: Long? = null,
            val message: PushMessage? = null
        ) : Operation()

        /** Store updated [content] for the given [name]. */
        data class Update(
            val name: String,
            val content: JsonMap,
            override val timestamp: Long,
            val dismissalTimestamp: Long? = null,
            val message: PushMessage? = null
        ) : Operation()

        /** Stop Live Updates for the given [name]. */
        data class Stop(
            val name: String,
            val content: JsonMap? = null,
            override val timestamp: Long,
            val dismissalTimestamp: Long? = null,
            val message: PushMessage? = null
        ) : Operation()

        /** Cancels an existing Live Update notification for the given [name]. */
        data class Cancel(
            val name: String,
            override val timestamp: Long = 0L
        ) : Operation()

        /** Clear all Live Updates and any locally stored data. */
        data class ClearAll(
            override val timestamp: Long = 0L
        ) : Operation()
    }

    internal data class HandlerCallback(
        val action: LiveUpdateEvent,
        val update: LiveUpdate,
        val message: PushMessage?
    )
}
