/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import android.content.Context
import android.os.Looper
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.AirshipDispatchers
import com.urbanairship.Cancelable
import com.urbanairship.CancelableOperation
import com.urbanairship.PendingResult
import com.urbanairship.Predicate
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UALog
import com.urbanairship.analytics.Analytics
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.analytics.LayoutEventRecorder
import com.urbanairship.android.layout.analytics.LayoutListener
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.ApplicationListener
import com.urbanairship.app.GlobalActivityMonitor.Companion.shared
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.iam.content.AirshipLayout
import com.urbanairship.meteredusage.AirshipMeteredUsage
import com.urbanairship.util.Clock
import com.urbanairship.util.TaskSleeper
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The inbox provides access to the device's local inbox data. Modifications (e.g., deletions or
 * mark read) will be sent to the Airship server the next time the inbox is synchronized.
 *
 * @property user The [User].
 */
public class Inbox @VisibleForTesting internal constructor(
    dataStore: PreferenceDataStore,
    public val user: User,
    private val messageDao: MessageDao,
    private val activityMonitor: ActivityMonitor,
    private val airshipChannel: AirshipChannel,
    private val config: AirshipRuntimeConfig,
    private val analytics: Analytics,
    private val meteredUsage: AirshipMeteredUsage,
    private val taskSleeper: TaskSleeper = TaskSleeper.default,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val refreshDispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher(),
    private val updateScheduler: (UpdateType) -> Unit
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
        analytics: Analytics,
        meteredUsage: AirshipMeteredUsage,
        updateScheduler: (UpdateType) -> Unit
    ) : this(
        dataStore = dataStore,
        user = User(dataStore),
        messageDao = MessageDatabase.createDatabase(context, config.configOptions).dao,
        activityMonitor = shared(context),
        airshipChannel = airshipChannel,
        config = config,
        updateScheduler = updateScheduler,
        analytics = analytics,
        meteredUsage = meteredUsage
    )

    @VisibleForTesting
    internal var inboxJobHandler: InboxJobHandler = InboxJobHandler(
        user = user,
        runtimeConfig = config,
        dataStore = dataStore,
        messageDao = messageDao
    )

    internal enum class RefreshResult { LOCAL, REMOTE_SUCCESS, REMOTE_FAILED }
    internal enum class UpdateType { REQUIRED, BEST_ATTEMPT }

    // The DAO flows will not auto refresh on expired messages, so we use this and update it
    // with a random UUID string to force updates.
    private val expiryRefresh = MutableStateFlow<String?>(null)
    @VisibleForTesting
    internal val refreshResults = MutableSharedFlow<RefreshResult>()
    private val listeners: MutableList<InboxListener> = CopyOnWriteArrayList()
    private var refreshOnMessageExpiresJob: Job? = null
    private val isEnabled = AtomicBoolean(false)

    private val updatesFlow = MutableSharedFlow<Unit>()
    public val inboxUpdated: Flow<Unit> = updatesFlow.asSharedFlow()

    private val applicationListener = object : ApplicationListener {
        override fun onForeground(milliseconds: Long) = scheduleUpdateIfEnabled(UpdateType.BEST_ATTEMPT)
        override fun onBackground(milliseconds: Long) = scheduleUpdateIfEnabled(UpdateType.BEST_ATTEMPT)
    }

    private val configListener = AirshipRuntimeConfig.ConfigChangeListener {
        scheduleUpdateIfEnabled(UpdateType.REQUIRED)
    }

    private val channelRegistrationPayloadExtender = AirshipChannel.Extender.Suspending { builder ->
        if (isEnabled.get()) {
            builder.setUserId(user.id)
        } else {
            builder
        }
    }

    init {
        scope.launch {
            val id = airshipChannel.id
            airshipChannel.channelIdFlow.filter { it == id }.collect {
                scheduleUpdate(UpdateType.REQUIRED)
            }
        }

        scope.launch {
            refreshResults.collect { status ->
                notifyInboxUpdated()
                if (status == RefreshResult.REMOTE_SUCCESS) {
                    setupRefreshOnMessageExpiresJob()
                }
            }
        }

        scope.launch {
            inboxUpdated.collect {
                listeners.forEach { listener ->
                    withContext(Dispatchers.Main) {
                        listener.onInboxUpdated()
                    }
                }
            }
        }

        airshipChannel.addChannelRegistrationPayloadExtender(channelRegistrationPayloadExtender)
        activityMonitor.addApplicationListener(applicationListener)
        config.addConfigListener(configListener)
        setupRefreshOnMessageExpiresJob()
    }

    internal suspend fun performUpdate(): Result<Boolean> {
        if (!isEnabled.get()) {
            refreshResults.emit(RefreshResult.REMOTE_FAILED)
            return Result.failure(IllegalStateException("Unable to update when disabled"))
        }

        return updateInbox().let {
            if (it) {
                refreshResults.emit(RefreshResult.REMOTE_SUCCESS)
            } else {
                refreshResults.emit(RefreshResult.REMOTE_FAILED)
            }
            Result.success(it)
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public suspend fun loadMessageLayout(message: Message): AirshipLayout? {
        return inboxJobHandler.loadAirshipLayout(message)
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun makeNativeMessageAnalytics(
        message: Message,
        onDismiss: () -> Unit
    ): ThomasListenerInterface {
        return LayoutListener(
            analytics = MessageAnalytics(
                message = message,
                eventRecorder = LayoutEventRecorder(
                    analytics = analytics,
                    meteredUsage = meteredUsage
                )
            ),
            onDismiss = { _ -> onDismiss() }
        )
    }

    private suspend fun updateInbox(): Boolean = withContext(refreshDispatcher) {
        val channelId = airshipChannel.id ?: return@withContext false
        val userCredentials = inboxJobHandler.getOrCreateUserCredentials(channelId) ?: return@withContext false

        val syncedRead = inboxJobHandler.syncReadMessageState(
            userCredentials = userCredentials, channelId = channelId
        )

        val syncedDeleted = inboxJobHandler.syncDeletedMessageState(
            userCredentials = userCredentials, channelId = channelId
        )

        if (!syncedRead || !syncedDeleted) {
            return@withContext false
        }

        return@withContext inboxJobHandler.syncMessageList(
            userCredentials = userCredentials, channelId = channelId
        )
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun tearDown() {
        activityMonitor.removeApplicationListener(applicationListener)
        airshipChannel.removeChannelRegistrationPayloadExtender(channelRegistrationPayloadExtender)
        refreshOnMessageExpiresJob?.cancel()
        config.removeRemoteConfigListener(configListener)
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun setupRefreshOnMessageExpiresJob() {
        refreshOnMessageExpiresJob?.cancel()

        refreshOnMessageExpiresJob = scope.launch {
            val now = clock.currentTimeMillis()

            val refreshDate = getMessages()
                .mapNotNull { it.expirationDate }
                .filter { it.time > now }
                .minOrNull() ?: return@launch

            val delay = (refreshDate.time - clock.currentTimeMillis()).milliseconds
            taskSleeper.sleep(delay)

            if (isActive) {
                expiryRefresh.update { UUID.randomUUID().toString() }
                refreshResults.tryEmit(RefreshResult.LOCAL)
            }
        }
    }

    /**
     * Enables or disables the Inbox.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun setEnabled(isEnabled: Boolean) {
        if (this.isEnabled.compareAndSet(!isEnabled, isEnabled)) {
            if (isEnabled) {
                scheduleUpdate(UpdateType.BEST_ATTEMPT)
            } else {
                // Clean up any Message Center data stored on the device.
                deleteAllMessagesInternal()
                inboxJobHandler.removeStoredData()
            }
        }
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
        looper: Looper? = null, callback: FetchMessagesCallback? = null
    ): Cancelable {
        val cancelableOperation = PendingFetchMessagesCallback(callback, looper)
        scope.launch {
            cancelableOperation.result = fetchMessages()
            cancelableOperation.run()
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
    @JvmSynthetic
    public suspend fun fetchMessages(): Boolean {
        if (!isEnabled.get()) {
            UALog.e { "Failed to resume fetchMessages, Message Center is disabled." }
            return false
        }

        scope.launch {
            scheduleUpdate(UpdateType.REQUIRED)
        }

        return refreshResults.first { it != RefreshResult.LOCAL } == RefreshResult.REMOTE_SUCCESS
    }

    /** The total message count. */
    @JvmSynthetic
    public suspend fun getCount(): Int = messageDao.getMessageCount()

    /** A [PendingResult] of the total message count. */
    public fun getCountPendingResult(): PendingResult<Int> {
        val result = PendingResult<Int>()
        scope.launch {
            result.setResult(messageDao.getMessageCount())
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
            result.setResult(messageDao.getMessageIds().toSet())
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
            result.setResult(messageDao.getReadMessageCount())
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
            result.setResult(messageDao.getUnreadMessageCount())
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
        messages: Collection<Message>, predicate: Predicate<Message>?
    ): Collection<Message> = predicate?.let { messages.filter(it::apply) } ?: messages

    /**
     * Gets a list of RichPushMessages, filtered by the provided predicate, and sorted by descending sent-at date.
     *
     * @param predicate A predicate for filtering messages. If null, no predicate will be applied.
     * @return List of filtered and sorted [Message]s.
     */
    @JvmSynthetic
    public suspend fun getMessages(predicate: Predicate<Message>? = null): List<Message> =
        messageDao.getMessages().mapNotNull { it.toMessage() }.let { filterMessages(it, predicate) }
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
    public fun getMessagesFlow(predicate: Predicate<Message>? = null): Flow<List<Message>> {
        return combine(messageDao.getMessagesFlow(), expiryRefresh) { messages, _ ->
            messages
        }.map {
            val messages = it.mapNotNull(MessageEntity::toMessage)
            filterMessages(messages, predicate)
                .sortedWith(Message.SENT_DATE_COMPARATOR)
                .filter { message ->
                    !message.isExpired
                }
        }.distinctUntilChanged()
    }

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
            result.setResult(getMessages(predicate))
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
    public fun getUnreadMessagesFlow(predicate: Predicate<Message>? = null): Flow<List<Message>> {
        return combine(messageDao.getUnreadMessagesFlow(), expiryRefresh) { messages, _ ->
            messages
        }.map {
            val messages = it.mapNotNull(MessageEntity::toMessage)
            filterMessages(messages, predicate)
                .sortedWith(Message.SENT_DATE_COMPARATOR)
                .filter { message ->
                    !message.isExpired
                }
        }.distinctUntilChanged()
    }

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
            result.setResult(getUnreadMessages(predicate))
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
            result.setResult(getReadMessages(predicate))
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
            result.setResult(getMessage(messageId))
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
            result.setResult(getMessageByUrl(messageUrl))
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
            refreshResults.tryEmit(RefreshResult.LOCAL)
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
            refreshResults.tryEmit(RefreshResult.LOCAL)
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
            refreshResults.tryEmit(RefreshResult.LOCAL)
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
            refreshResults.tryEmit(RefreshResult.LOCAL)
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
            refreshResults.tryEmit(RefreshResult.LOCAL)
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
            refreshResults.tryEmit(RefreshResult.LOCAL)
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
            refreshResults.tryEmit(RefreshResult.LOCAL)
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
            refreshResults.tryEmit(RefreshResult.LOCAL)
        }
    }

    /** Notifies all of the registered listeners that the inbox updated. */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun notifyInboxUpdated() {
        scope.launch {
            updatesFlow.emit(Unit)
        }
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    private fun scheduleUpdate(
        reason: UpdateType
    ) {
        updateScheduler(reason)
    }

    internal fun scheduleUpdateIfEnabled(
        reason: UpdateType
    ) {
        if (isEnabled.get()) {
            updateScheduler(reason)
        }
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
