/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.urbanairship.AirshipExecutors
import com.urbanairship.Cancelable
import com.urbanairship.CancelableOperation
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
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

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
    private val deletedMessageIds: MutableSet<String> = HashSet()
    private val unreadMessages: MutableMap<String, Message> = HashMap()
    private val readMessages: MutableMap<String, Message> = HashMap()
    private val messageUrlMap: MutableMap<String, Message> = HashMap()
    private val handler = Handler(Looper.getMainLooper())

    private var isFetchingMessages = false

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
        override fun onForeground(time: Long) = jobDispatcher.dispatch(
            JobInfo.newBuilder()
                .setAction(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                .setAirshipComponent(MessageCenter::class.java)
                .setConflictStrategy(JobInfo.KEEP)
                .build()
        )
        override fun onBackground(time: Long) = jobDispatcher.dispatch(
            JobInfo.newBuilder()
                .setAction(InboxJobHandler.ACTION_SYNC_MESSAGE_STATE)
                .setAirshipComponent(MessageCenter::class.java)
                .setConflictStrategy(JobInfo.KEEP)
                .build()
        )
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
            fetchMessages()
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
                scope.launch {
                    refresh(false)
                }
                activityMonitor.addApplicationListener(applicationListener)
                airshipChannel.addChannelListener(channelListener)
                if (user.shouldUpdate()) {
                    dispatchUpdateUserJob(true)
                }
                airshipChannel.addChannelRegistrationPayloadExtender(
                    channelRegistrationPayloadExtender
                )
            }
        } else {
            // Clean up any Message Center data stored on the device.
            deleteAllMessages()
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
     *
     * Normally this method is not called directly as the message list is automatically fetched when
     * the application foregrounds or when a notification with an associated message is received.
     *
     *
     * If the fetch request completes and results in a change to the messages,
     * [InboxListener.onInboxUpdated] will be called.
     */
    public fun fetchMessages(): Cancelable = fetchMessages(null, null)

    /**
     * Fetches the latest inbox changes from Airship.
     *
     *
     * Normally this method is not called directly as the message list is automatically fetched when
     * the application foregrounds or when a notification with an associated message is received.
     *
     *
     * If the fetch request completes and results in a change to the messages,
     * [InboxListener.onInboxUpdated] will be called.
     *
     * @param callback Callback to be notified when the request finishes fetching the messages.
     * @return A cancelable object that can be used to cancel the callback.
     */
    public fun fetchMessages(callback: FetchMessagesCallback): Cancelable =
        fetchMessages(null, callback)

    /**
     * Fetches the latest inbox changes from Airship.
     *
     *
     * Normally this method is not called directly as the message list is automatically fetched when
     * the application foregrounds or when a notification with an associated message is received.
     *
     *
     * If the fetch request completes and results in a change to the messages,
     * [InboxListener.onInboxUpdated] will be called.
     *
     * @param callback Callback to be notified when the request finishes fetching the messages.
     * @param looper The looper to post the callback on.
     * @return A cancelable object that can be used to cancel the callback.
     */
    public fun fetchMessages(looper: Looper?, callback: FetchMessagesCallback?): Cancelable {
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

    internal fun onUpdateMessagesFinished(result: Boolean) =
        synchronized(pendingFetchCallbacks) {
            for (callback: PendingFetchMessagesCallback in pendingFetchCallbacks) {
                callback.result = result
                callback.run()
            }
            isFetchingMessages = false
            pendingFetchCallbacks.clear()
        }

    /** The total message count. */
    public val count: Int
        get() = synchronized(inboxLock) { return unreadMessages.size + readMessages.size }

    /** All the message IDs in the [Inbox]. */
    public val messageIds: Set<String>
        get() = synchronized(inboxLock) {
            val messageIds: MutableSet<String> = HashSet(count)
            messageIds.addAll(readMessages.keys)
            messageIds.addAll(unreadMessages.keys)
            return messageIds
        }


    /** The number of read messages currently in the [Inbox]. */
    public val readCount: Int
        get() = synchronized(inboxLock) { return readMessages.size }


    /** The number of unread messages currently in the [Inbox]. */
    public val unreadCount: Int
        get() = synchronized(inboxLock) { return unreadMessages.size }

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
    ): Collection<Message> {
        val filteredMessages = mutableListOf<Message>()
        if (predicate == null) {
            return messages
        }
        for (message: Message in messages) {
            if (predicate.apply(message)) {
                filteredMessages.add(message)
            }
        }
        return filteredMessages
    }

    /**
     * Gets a list of RichPushMessages, filtered by the provided predicate.
     * Sorted by descending sent-at date.
     *
     * @param predicate A predicate for filtering messages. If null, no predicate will be applied.
     * @return List of filtered and sorted [Message]s.
     */
    public fun getMessages(predicate: Predicate<Message>?): List<Message> =
        synchronized(inboxLock) {
            val messages: MutableList<Message> = ArrayList()
            messages.addAll(filterMessages(unreadMessages.values, predicate))
            messages.addAll(filterMessages(readMessages.values, predicate))
            Collections.sort(messages, MESSAGE_COMPARATOR)
            return messages
        }

    /** The list of messages in the [Inbox]. Sorted by descending sent-at date. */
    public val messages: List<Message>
        get() = getMessages(null)

    /**
     * Gets a list of unread RichPushMessages, filtered by the provided predicate.
     * Sorted by descending sent-at date.
     *
     * @param predicate A predicate for filtering messages. If null, no predicate will be applied.
     * @return List of sorted [Message]s.
     */
    public fun getUnreadMessages(predicate: Predicate<Message>?): List<Message> =
        synchronized(inboxLock) {
            val messages: List<Message> =
                ArrayList(filterMessages(unreadMessages.values, predicate))
            Collections.sort(messages, MESSAGE_COMPARATOR)
            return messages
        }

    /**
     * Gets a list of unread RichPushMessages. Sorted by descending sent-at date.
     *
     * @return List of sorted [Message]s.
     */
    // TODO: could be a val if we renamed this.unreadMessages to this.unreadMessagesMap?
    public fun getUnreadMessages(): List<Message> = getUnreadMessages(null)

    /**
     * Gets a list of read RichPushMessages, filtered by the provided predicate.
     * Sorted by descending sent-at date.
     *
     * @param predicate A predicate for filtering messages. If null, no predicate will be applied.
     * @return List of sorted [Message]s.
     */
    public fun getReadMessages(predicate: Predicate<Message>?): List<Message> =
        synchronized(inboxLock) {
            val messages: List<Message> = ArrayList(filterMessages(readMessages.values, predicate))
            Collections.sort(messages, MESSAGE_COMPARATOR)
            return messages
        }

    /**
     * Gets a list of read RichPushMessages. Sorted by descending sent-at date.
     *
     * @return List of sorted [Message]s.
     */
    // TODO: could be a val if we renamed this.readMessages to this.readMessagesMap?
    public fun getReadMessages(): List<Message> = getReadMessages(null)

    /**
     * Get the [Message] with the corresponding message ID.
     *
     * @param messageId The message ID of the desired [Message].
     * @return A [Message] or `null` if one does not exist.
     */
    public fun getMessage(messageId: String?): Message? {
        if (messageId == null) {
            return null
        }
        synchronized(inboxLock) {
            if (unreadMessages.containsKey(messageId)) {
                return unreadMessages[messageId]
            }
            return readMessages[messageId]
        }
    }

    /**
     * Get the [Message] with the corresponding message body URL.
     *
     * @param messageUrl The message body URL of the desired [Message].
     * @return A [Message] or `null` if one does not exist.
     */
    public fun getMessageByUrl(messageUrl: String?): Message? {
        if (messageUrl == null) {
            return null
        }
        synchronized(inboxLock) { return messageUrlMap[messageUrl] }
    }

    // actions

    /**
     * Mark [Message]s read in bulk.
     *
     * @param messageIds A set of message ids.
     */
    public fun markMessagesRead(messageIds: Set<String>) {
        scope.launch {
            messageDao.markMessagesRead(messageIds.toList())
        }
        synchronized(inboxLock) {
            for (messageId: String in messageIds) {
                val message: Message? = unreadMessages[messageId]
                if (message != null) {
                    message.unreadClient = false
                    unreadMessages.remove(messageId)
                    readMessages[messageId] = message
                }
            }
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
            messageDao.markMessagesUnread(messageIds.toList())
        }
        synchronized(inboxLock) {
            for (messageId: String in messageIds) {
                val message: Message? = readMessages[messageId]
                if (message != null) {
                    message.unreadClient = true
                    readMessages.remove(messageId)
                    unreadMessages[messageId] = message
                }
            }
        }
        notifyInboxUpdated()
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
            messageDao.markMessagesDeleted(messageIds.toList())
        }
        synchronized(inboxLock) {
            for (messageId: String in messageIds) {
                getMessage(messageId)?.let { message ->
                    message.deleted = true
                    unreadMessages.remove(messageId)
                    readMessages.remove(messageId)
                    deletedMessageIds.add(messageId)
                }
            }
        }
        notifyInboxUpdated()
    }

    /**
     * Delete all message data stored on the device.
     *
     * @hide
     */
    private fun deleteAllMessages() {
        scope.launch {
            messageDao.deleteAllMessages()
        }

        synchronized(inboxLock) {
            unreadMessages.clear()
            readMessages.clear()
            deletedMessageIds.clear()
        }
        notifyInboxUpdated()
    }

    /**
     * Refreshes the inbox messages from the DB.
     *
     * @param notify `true` to notify listeners, otherwise `false`.
     */
    internal suspend fun refresh(notify: Boolean) {
        val messageList = messageDao.getMessages()

        // Sync the messages
        synchronized(inboxLock) {
            // Save the unreadMessageIds
            val previousUnreadMessageIds: Set<String> = HashSet(unreadMessages.keys)
            val previousReadMessageIds: Set<String> = HashSet(readMessages.keys)
            val previousDeletedMessageIds: Set<String> = HashSet(deletedMessageIds)

            // Clear the current messages
            unreadMessages.clear()
            readMessages.clear()
            messageUrlMap.clear()

            // Process the new messages
            for (messageEntity: MessageEntity in messageList) {
                val message = messageEntity.createMessageFromEntity(messageEntity) ?: continue

                // Deleted
                if (message.isDeleted || previousDeletedMessageIds.contains(message.messageId)) {
                    deletedMessageIds.add(message.messageId)
                    continue
                }

                // Expired
                if (message.isExpired) {
                    deletedMessageIds.add(message.messageId)
                    continue
                }

                // Populate message url map
                messageUrlMap[message.messageBodyUrl] = message

                // Unread - check the previousUnreadMessageIds if any mark reads are still in process
                if (previousUnreadMessageIds.contains(message.messageId)) {
                    message.unreadClient = true
                    unreadMessages[message.messageId] = message
                    continue
                }

                // Read - check the previousUnreadMessageIds if any mark reads are still in process
                if (previousReadMessageIds.contains(message.messageId)) {
                    message.unreadClient = false
                    readMessages[message.messageId] = message
                    continue
                }

                // Otherwise fallback to the current state
                if (message.unreadClient) {
                    unreadMessages[message.messageId] = message
                } else {
                    readMessages[message.messageId] = message
                }
            }
        }
        if (notify) {
            notifyInboxUpdated()
        }
    }

    /** Notifies all of the registered listeners that the inbox updated. */
    private fun notifyInboxUpdated() {
        handler.post {
            for (listener: InboxListener in listeners) {
                listener.onInboxUpdated()
            }
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun dispatchUpdateUserJob(forcefully: Boolean) {
        UALog.d("Updating user.")
        val jobInfo = JobInfo.newBuilder()
            .setAction(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE)
            .setAirshipComponent(MessageCenter::class.java)
            .setExtras(
                jsonMapOf(InboxJobHandler.EXTRA_FORCEFULLY to forcefully)
            )
            .setConflictStrategy(if (forcefully) JobInfo.REPLACE else JobInfo.KEEP)
            .build()

        jobDispatcher.dispatch(jobInfo)
    }

    internal class SentAtRichPushMessageComparator : Comparator<Message> {
        override fun compare(lhs: Message, rhs: Message): Int {
            return if (rhs.sentDateMS == lhs.sentDateMS) {
                lhs.messageId.compareTo(rhs.messageId)
            } else {
                rhs.sentDateMS.compareTo(lhs.sentDateMS)
            }
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

    private companion object {
        private val MESSAGE_COMPARATOR = SentAtRichPushMessageComparator()
        private val inboxLock = Any()
    }
}
