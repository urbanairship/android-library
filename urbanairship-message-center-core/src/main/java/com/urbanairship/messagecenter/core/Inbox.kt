/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter.core

import android.content.Context
import android.os.Looper
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.urbanairship.Cancelable
import com.urbanairship.CancelableOperation
import com.urbanairship.PendingResult
import com.urbanairship.Predicate
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.ApplicationListener
import com.urbanairship.app.GlobalActivityMonitor.Companion.shared
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.channel.AirshipChannelListener
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import com.urbanairship.json.jsonMapOf
import com.urbanairship.messagecenter.core.Inbox.FetchMessagesCallback
import com.urbanairship.util.Clock
import com.urbanairship.util.TaskSleeper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * The inbox provides access to the device's local inbox data. Modifications (e.g., deletions or
 * mark read) will be sent to the Airship server the next time the inbox is synchronized.
 *
 * @property user The [User].
 */
public class Inbox @VisibleForTesting internal constructor(
    dataStore: PreferenceDataStore,
    private val jobDispatcher: JobDispatcher,
    public val user: User,
    private val messageDao: MessageDao,
    private val activityMonitor: ActivityMonitor,
    private val airshipChannel: AirshipChannel,
    private val privacyManager: PrivacyManager,
    config: AirshipRuntimeConfig,
    private val taskSleeper: TaskSleeper = TaskSleeper.default,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(dispatcher + job)

    /** A callback used to be notified when refreshing messages. */
    public fun interface FetchMessagesCallback {

        /**
         * Called when a request to refresh messages is finished.
         *
         * @param success If the request was successful or not.
         */
        public fun onFinished(success: Boolean)
    }

    /**
     * Default constructor.
     *
     * @hide
     */
    internal constructor(
        context: Context,
        dataStore: PreferenceDataStore,
        airshipChannel: AirshipChannel,
        config: AirshipRuntimeConfig,
        privacyManager: PrivacyManager
    ) : this(
        dataStore = dataStore,
        jobDispatcher = JobDispatcher.shared(context),
        user = User(dataStore, airshipChannel),
        messageDao = MessageDatabase.createDatabase(context, config.configOptions).dao,
        activityMonitor = shared(context),
        airshipChannel = airshipChannel,
        privacyManager = privacyManager,
        config = config
    )

    private val listeners: MutableList<InboxListener> = CopyOnWriteArrayList()

    private var isFetchingMessages = false
    private var refreshOnMessageExpiresJob: Job? = null

    @VisibleForTesting
    internal var inboxJobHandler: InboxJobHandler = InboxJobHandler(
        inbox = this,
        user = user,
        channel = airshipChannel,
        runtimeConfig = config,
        dataStore = dataStore,
        messageDao = messageDao
    )

    private val isEnabled = AtomicBoolean(false)
    private val isStarted = AtomicBoolean(false)
    private val pendingFetchCallbacks: MutableList<PendingFetchMessagesCallback> = ArrayList()

    private val applicationListener: ApplicationListener = object : ApplicationListener {
        override fun onForeground(time: Long) = dispatchUpdateUserJob(
            forcefully = false,
            fetchMessages = true
        )

        override fun onBackground(time: Long) {
            jobDispatcher.dispatch(
                JobInfo.newBuilder()
                    .setAction(InboxJobHandler.ACTION_SYNC_MESSAGE_STATE)
                    .setAirshipComponent(MessageCenter::class.java)
                    .setConflictStrategy(JobInfo.KEEP)
                    .build()
            )

            refreshOnMessageExpiresJob?.cancel()
        }
    }

    private val channelListener: AirshipChannelListener =
        AirshipChannelListener { dispatchUpdateUserJob(true) }

    private val channelRegistrationPayloadExtender = AirshipChannel.Extender.Blocking { builder ->
        if (privacyManager.isEnabled(PrivacyManager.Feature.MESSAGE_CENTER)) {
            builder.setUserId(user.id)
        } else {
            builder
        }
    }

    private val userListener = User.Listener { success: Boolean ->
        if (success) {
            fetchMessages { UALog.v { "Inbox updated after user update." } }
        }
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun init() {
        updateEnabledState()
    }

    /**
     * @hide
     */
    @WorkerThread
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun onPerformJob(airship: UAirship, jobInfo: JobInfo): JobResult {
        if (!isEnabled.get()) {
            return JobResult.SUCCESS
        }

        return inboxJobHandler.performJob(jobInfo)
    }

    /**
     * Initializes or tears down the Inbox based on the current enabled state.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun updateEnabledState() {
        if (isEnabled.get()) {
            if (!isStarted.getAndSet(true)) {
                // Refresh the inbox whenever the user is updated.
                user.addListener(userListener)
                activityMonitor.addApplicationListener(applicationListener)
                airshipChannel.addChannelListener(channelListener)
                if (user.shouldUpdate()) {
                    // Update user and then fetch messages.
                    dispatchUpdateUserJob(forcefully = true, fetchMessages = true)
                } else if (activityMonitor.isAppForegrounded) {
                    // Fetch messages if the app is in the foreground.
                    fetchMessages(null, null)
                }
                airshipChannel.addChannelRegistrationPayloadExtender(
                    channelRegistrationPayloadExtender
                )
            }
        } else {
            // Clean up any Message Center data stored on the device.
            deleteAllMessagesInternal()
            inboxJobHandler.removeStoredData()
            tearDown()
        }
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun tearDown() {
        activityMonitor.removeApplicationListener(applicationListener)
        airshipChannel.removeChannelListener(channelListener)
        airshipChannel.removeChannelRegistrationPayloadExtender(channelRegistrationPayloadExtender)
        user.removeListener(userListener)
        isStarted.set(false)
        refreshOnMessageExpiresJob?.cancel()
    }

    private fun setupRefreshOnMessageExpiresJob() {
        refreshOnMessageExpiresJob?.cancel()

        refreshOnMessageExpiresJob = scope.launch {
            val now = clock.currentTimeMillis()

            val refreshDate = getMessages()
                .mapNotNull { it.expirationDate }
                .filter { it.time > now }
                .minOrNull()
            ?: return@launch

            val delay = (refreshDate.time - clock.currentTimeMillis()).milliseconds

            taskSleeper.sleep(delay)
            fetchMessages()
        }
    }

    /**
     * Enables or disables the Inbox.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun setEnabled(isEnabled: Boolean) {
        this.isEnabled.set(isEnabled)
    }

    /**
     * Subscribe a listener for inbox update event callbacks.
     *
     * @param listener An object implementing the [InboxListener] interface.
     */
    public fun addListener(listener: InboxListener) {
        listeners.add(listener)
    }

    /**
     * Unsubscribe a listener for inbox update event callbacks.
     *
     * @param listener An object implementing the [InboxListener] interface.
     */
    public fun removeListener(listener: InboxListener) {
        listeners.remove(listener)
    }

    /**
     * Fetches the latest inbox changes from Airship.
     *
     * Normally this method is not called directly as the message list is automatically fetched when
     * the application foregrounds or when a notification with an associated message is received.
     *
     * If the fetch request completes and results in a change to the messages,
     * [InboxListener.onInboxUpdated] will be called.
     *
     * @param callback Optional callback to be notified when the request finishes fetching the messages.
     * @return A cancelable object that can be used to cancel the callback.
     */
    public fun fetchMessages(callback: FetchMessagesCallback): Cancelable {
        return fetchMessages(null, callback)
    }

    /**
     * Fetches the latest inbox changes from Airship.
     *
     * Normally this method is not called directly as the message list is automatically fetched when
     * the application foregrounds or when a notification with an associated message is received.
     *
     * If the fetch request completes and results in a change to the messages,
     * [InboxListener.onInboxUpdated] will be called.
     *
     * @param callback Optional callback to be notified when the request finishes fetching the messages.
     * @param looper Optional `Looper` to post the callback on.
     * @return A cancelable object that can be used to cancel the callback.
     */
    public fun fetchMessages(
        looper: Looper? = null,
        callback: FetchMessagesCallback? = null
    ): Cancelable {
        val cancelableOperation = PendingFetchMessagesCallback(callback, looper)
        synchronized(pendingFetchCallbacks) {
            pendingFetchCallbacks.add(cancelableOperation)
            if (!isFetchingMessages) {
                val jobInfo: JobInfo =
                    JobInfo.newBuilder().setAction(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                        .setAirshipComponent(MessageCenter::class.java)
                        .setConflictStrategy(JobInfo.REPLACE).build()
                jobDispatcher.dispatch(jobInfo)
            }
            isFetchingMessages = true
        }
        return cancelableOperation
    }

    /**
     * Suspending function to fetch the latest inbox changes from Airship.
     *
     * If the fetch request completes and results in a change to the messages,
     * [InboxListener.onInboxUpdated] will be called.
     *
     * @return `true` if the fetch was successful, `false` otherwise.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @JvmSynthetic
    public suspend fun fetchMessages(): Boolean = suspendCancellableCoroutine { continuation ->
        val callback = FetchMessagesCallback { success: Boolean ->
            continuation.resume(success) {
                UALog.e { "Failed to resume fetchMessages coroutine." }
            }
        }

        val cancelable = fetchMessages(callback)

        continuation.invokeOnCancellation {
            cancelable.cancel()
        }
    }

    internal fun onUpdateMessagesFinished(result: Boolean) {
        synchronized(pendingFetchCallbacks) {
            for (callback: PendingFetchMessagesCallback in pendingFetchCallbacks) {
                callback.result = result
                callback.run()
            }
            isFetchingMessages = false
            pendingFetchCallbacks.clear()
        }

        setupRefreshOnMessageExpiresJob()
    }


    /** The total message count. */
    @JvmSynthetic
    public suspend fun getCount(): Int = messageDao.getMessageCount()

    /** A [PendingResult] of the total message count. */
    public fun getCountPendingResult(): PendingResult<Int> {
        val result = PendingResult<Int>()
        scope.launch {
            result.result = messageDao.getMessageCount()
        }
        return result
    }

    /** All the message IDs in the [Inbox]. */
    @JvmSynthetic
    public suspend fun getMessageIds(): Set<String> = messageDao.getMessageIds().toSet()

    /** A [PendingResult] of all the message IDs in the [Inbox]. */
    public fun getMessageIdsPendingResult(): PendingResult<Set<String>> {
        val result = PendingResult<Set<String>>()
        scope.launch {
            result.result = messageDao.getMessageIds().toSet()
        }
        return result
    }

    /** The number of read messages currently in the [Inbox]. */
    @JvmSynthetic
    public suspend fun getReadCount(): Int = messageDao.getReadMessageCount()

    /** A [PendingResult] of the read message count. */
    public fun getReadCountPendingResult(): PendingResult<Int> {
        val result = PendingResult<Int>()
        scope.launch {
            result.result = messageDao.getReadMessageCount()
        }
        return result
    }

    /** The number of unread messages currently in the [Inbox]. */
    @JvmSynthetic
    public suspend fun getUnreadCount(): Int = messageDao.getUnreadMessageCount()

    /** A [PendingResult] of the unread message count. */
    public fun getUnreadCountPendingResult(): PendingResult<Int> {
        val result = PendingResult<Int>()
        scope.launch {
            result.result = messageDao.getUnreadMessageCount()
        }
        return result
    }

    /**
     * Filters a collection of messages according to the supplied predicate
     *
     * @param messages The messages to filter
     * @param predicate The predicate. If null, the collection will be returned as-is.
     * @return A filtered collection of messages
     */
    private fun filterMessages(
        messages: Collection<Message>,
        predicate: Predicate<Message>?
    ): Collection<Message> = predicate?.let { messages.filter(it::apply) } ?: messages

    /**
     * Gets a list of RichPushMessages, filtered by the provided predicate, and sorted by descending sent-at date.
     *
     * @param predicate A predicate for filtering messages. If null, no predicate will be applied.
     * @return List of filtered and sorted [Message]s.
     */
    @JvmSynthetic
    public suspend fun getMessages(predicate: Predicate<Message>? = null): List<Message> =
        messageDao.getMessages()
            .mapNotNull { it.toMessage() }
            .let { filterMessages(it, predicate) }
            .sortedWith(Message.SENT_DATE_COMPARATOR)

    /**
     * Subscribes to the list of messages as a flow. The flow will emit the current list of messages,
     * filtered by the provided predicate, and sorted by descending sent-at date, and will emit new lists
     * whenever the inbox is updated.
     *
     * @param predicate A predicate for filtering messages. If null, no predicate will be applied.
     * @return A flow of filtered and sorted [Message]s.
     */
    @JvmSynthetic
    public fun getMessagesFlow(predicate: Predicate<Message>? = null): Flow<List<Message>> =
        messageDao.getMessagesFlow()
            .map {
                val messages = it.mapNotNull(MessageEntity::toMessage)
                filterMessages(messages, predicate)
                    .sortedWith(Message.SENT_DATE_COMPARATOR)
            }
            .distinctUntilChanged()

    /**
     * Gets a list of RichPushMessages as a [PendingResult], filtered by the provided predicate,
     * and sorted by descending sent-at date.
     *
     * @param predicate Optional predicate for filtering messages. If null, no predicate will be applied.
     * @return A [PendingResult] containing the list of filtered and sorted [Message]s.
     */
    @JvmOverloads
    public fun getMessagesPendingResult(predicate: Predicate<Message>? = null): PendingResult<List<Message>?> {
        val result = PendingResult<List<Message>?>()
        scope.launch {
            result.result = getMessages(predicate)
        }
        return result
    }

    /**
     * Gets a list of unread RichPushMessages, filtered by the provided predicate,
     * and sorted by descending sent-at date.
     *
     * @param predicate A predicate for filtering messages. If null, no predicate will be applied.
     * @return List of sorted [Message]s.
     */
    @JvmSynthetic
    public suspend fun getUnreadMessages(predicate: Predicate<Message>? = null): List<Message> =
        messageDao.getUnreadMessages()
            .mapNotNull { it.toMessage() }
            .let { filterMessages(it, predicate) }
            .sortedWith(Message.SENT_DATE_COMPARATOR)

    /**
     * Subscribes to the list of unread messages as a flow. The flow will emit the current list of unread messages,
     * filtered by the provided predicate, and sorted by descending sent-at date, and will emit new lists whenever
     * the inbox is updated.
     *
     * @param predicate A predicate for filtering messages. If null, no predicate will be applied.
     * @return A flow of filtered and sorted [Message]s.
     */
    @JvmSynthetic
    public fun getUnreadMessagesFlow(predicate: Predicate<Message>? = null): Flow<List<Message>> =
        messageDao.getUnreadMessagesFlow()
            .map {
                val messages = it.mapNotNull(MessageEntity::toMessage)
                filterMessages(messages, predicate)
                    .sortedWith(Message.SENT_DATE_COMPARATOR)
            }
            .distinctUntilChanged()

    /**
     * Gets a list of unread RichPushMessages as a [PendingResult], filtered by the provided predicate,
     * and sorted by descending sent-at date.
     *
     * @param predicate Optional predicate for filtering messages. If null, no predicate will be applied.
     * @return A [PendingResult] containing the list of filtered [Message]s.
     */
    @JvmOverloads
    public fun getUnreadMessagesPendingResult(predicate: Predicate<Message>? = null): PendingResult<List<Message>?> {
        val result = PendingResult<List<Message>?>()
        scope.launch {
            result.result = getUnreadMessages(predicate)
        }
        return result
    }

    /**
     * Gets a list of read RichPushMessages, filtered by the provided predicate.
     * Sorted by descending sent-at date.
     *
     * @param predicate A predicate for filtering messages. If null, no predicate will be applied.
     * @return List of sorted [Message]s.
     */
    @JvmSynthetic
    public suspend fun getReadMessages(predicate: Predicate<Message>? = null): List<Message> =
        messageDao.getReadMessages()
            .mapNotNull { it.toMessage() }
            .let { filterMessages(it, predicate) }
            .sortedWith(Message.SENT_DATE_COMPARATOR)

    /**
     * Gets a list of read RichPushMessages as a [PendingResult], filtered by the provided predicate,
     * and sorted by descending sent-at date.
     *
     * @param predicate Optional predicate for filtering messages. If null, no predicate will be applied.
     * @return A [PendingResult] containing the list of filtered [Message]s.
     */
    @JvmOverloads
    public fun getReadMessagesPendingResult(predicate: Predicate<Message>? = null): PendingResult<List<Message>?> {
        val result = PendingResult<List<Message>?>()
        scope.launch {
            result.result = getReadMessages(predicate)
        }
        return result
    }

    /**
     * Get the [Message] with the corresponding message ID.
     *
     * @param messageId The message ID of the desired [Message].
     * @return A [Message] or `null` if one does not exist.
     */
    @JvmSynthetic
    public suspend fun getMessage(messageId: String?): Message? =
        messageId?.let { messageDao.getMessage(it)?.toMessage() }

    /**
     * Get the [Message] with the corresponding message ID as a [PendingResult].
     *
     * @param messageId The message ID of the desired [Message].
     * @return A [PendingResult] containing the [Message] or `null` if one does not exist.
     */
    public fun getMessagePendingResult(messageId: String?): PendingResult<Message?> {
        val result = PendingResult<Message?>()
        scope.launch {
            result.result = getMessage(messageId)
        }
        return result
    }

    /**
     * Get the [Message] with the corresponding message body URL.
     *
     * @param messageUrl The message body URL of the desired [Message].
     * @return A [Message] or `null` if one does not exist.
     */
    @JvmSynthetic
    public suspend fun getMessageByUrl(messageUrl: String?): Message? =
        messageUrl?.let { messageDao.getMessageByUrl(it)?.toMessage() }

    /**
     * Get the [Message] with the corresponding message body URL as a [PendingResult].
     *
     * @param messageUrl The message body URL of the desired [Message].
     * @return A [PendingResult] containing the [Message] or `null` if one does not exist.
     */
    public fun getMessageByUrlPendingResult(messageUrl: String?): PendingResult<Message?> {
        val result = PendingResult<Message?>()
        scope.launch {
            result.result = getMessageByUrl(messageUrl)
        }
        return result
    }

    // actions

    /**
     * Mark [Message]s read in bulk.
     *
     * @param messageIds A set of message ids.
     */
    public fun markMessagesRead(messageIds: Set<String>) {
        scope.launch {
            messageDao.markMessagesRead(messageIds)
            notifyInboxUpdated()
        }
    }

    /**
     * Mark [Message]s read in bulk.
     *
     * @param messageIds A vararg of message IDs.
     */
    public fun markMessagesRead(vararg messageIds: String) {
        scope.launch {
            messageDao.markMessagesRead(messageIds.toSet())
            notifyInboxUpdated()
        }
    }

    /**
     * Mark [Message]s unread in bulk.
     *
     * @param messageIds A set of message ids.
     */
    public fun markMessagesUnread(messageIds: Set<String>) {
        scope.launch {
            messageDao.markMessagesUnread(messageIds)
            notifyInboxUpdated()
        }
    }

    /**
     * Mark [Message]s unread in bulk.
     *
     * @param messageIds A vararg of message ids.
     */
    public fun markMessagesUnread(vararg messageIds: String) {
        scope.launch {
            messageDao.markMessagesUnread(messageIds.toSet())
            notifyInboxUpdated()
        }
    }

    /**
     * Mark [Message]s deleted.
     *
     * Note that in most cases these messages aren't immediately deleted on the server, but they will
     * be inaccessible on the device as soon as they're marked deleted.
     *
     * @param messageIds A set of message ids.
     */
    public fun deleteMessages(messageIds: Set<String>) {
        scope.launch {
            messageDao.markMessagesDeleted(messageIds)
            notifyInboxUpdated()
        }
    }

    /**
     * Mark [Message]s deleted.
     *
     * Note that in most cases these messages aren't immediately deleted on the server, but they will
     * be inaccessible on the device as soon as they're marked deleted.
     *
     * @param messageIds A vararg of message ids.
     */
    public fun deleteMessages(vararg messageIds: String) {
        scope.launch {
            messageDao.markMessagesDeleted(messageIds.toSet())
            notifyInboxUpdated()
        }
    }

    /**
     * Mark all [Message]s deleted.
     *
     * Note that in most cases these messages aren't immediately deleted on the server, but they will
     * be inaccessible on the device as soon as they're marked deleted.
     */
    public fun deleteAllMessages() {
        scope.launch {
            messageDao.markAllMessagesDeleted()
            notifyInboxUpdated()
        }
    }

    /**
     * Delete all message data stored on the device.
     *
     * @hide
     */
    private fun deleteAllMessagesInternal() {
        scope.launch {
            messageDao.deleteAllMessages()
            notifyInboxUpdated()
        }
    }

    /** Notifies all of the registered listeners that the inbox updated. */
    internal fun notifyInboxUpdated() {
        scope.launch {
            for (listener in listeners) {
                withContext(Dispatchers.Main) {
                    listener.onInboxUpdated()
                }
            }
        }
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun dispatchUpdateUserJob(
        forcefully: Boolean,
        fetchMessages: Boolean = false
    ) {
        UALog.d("Updating user.")
        refreshOnMessageExpiresJob?.cancel()

        val jobInfo = JobInfo.newBuilder()
            .setAction(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE)
            .setAirshipComponent(MessageCenter::class.java)
            .setExtras(
                jsonMapOf(
                    InboxJobHandler.EXTRA_FORCEFULLY to forcefully,
                    InboxJobHandler.EXTRA_FETCH_MESSAGES to fetchMessages
                )
            )
            .setConflictStrategy(if (forcefully) JobInfo.REPLACE else JobInfo.KEEP)
            .build()

        jobDispatcher.dispatch(jobInfo)
    }

    internal class PendingFetchMessagesCallback(
        private val callback: FetchMessagesCallback?,
        looper: Looper?
    ) : CancelableOperation(looper) {
        var result = false
        override fun onRun() {
            callback?.onFinished(result)
        }
    }
}
