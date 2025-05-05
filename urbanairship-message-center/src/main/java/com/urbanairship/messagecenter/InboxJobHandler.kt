/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter

import androidx.annotation.VisibleForTesting
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UALog
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.RequestException
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonValue
import java.net.HttpURLConnection
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.runBlocking

/** Job handler for [Inbox] component. */
public class InboxJobHandler @VisibleForTesting internal constructor(
    private val inbox: Inbox,
    private val user: User,
    private val channel: AirshipChannel,
    private val dataStore: PreferenceDataStore,
    private val messageDao: MessageDao,
    private val inboxApiClient: InboxApiClient,
) {
    internal constructor(
        inbox: Inbox,
        user: User,
        channel: AirshipChannel,
        runtimeConfig: AirshipRuntimeConfig,
        dataStore: PreferenceDataStore,
        messageDao: MessageDao
    ) : this(
        inbox = inbox,
        user = user,
        channel = channel,
        dataStore = dataStore,
        messageDao = messageDao,
        inboxApiClient = InboxApiClient(runtimeConfig)
    )

    /** Delete saved state from the data store. */
    internal fun removeStoredData() {
        dataStore.remove(LAST_MESSAGE_REFRESH_TIME)
        dataStore.remove(LAST_UPDATE_TIME)
    }

    /**
     * Called to handle jobs from [Inbox.onPerformJob].
     *
     * @param jobInfo The airship jobInfo.
     * @return The job result.
     */
    internal fun performJob(jobInfo: JobInfo): JobResult = runBlocking {
        when (jobInfo.action) {
            ACTION_RICH_PUSH_USER_UPDATE -> onUpdateUser(
                forcefully = jobInfo.extras.opt(EXTRA_FORCEFULLY).getBoolean(false),
                fetchMessages = jobInfo.extras.opt(EXTRA_FETCH_MESSAGES).getBoolean(false)
            ).let { JobResult.SUCCESS }

            ACTION_RICH_PUSH_MESSAGES_UPDATE -> onUpdateMessages().let { JobResult.SUCCESS }
            ACTION_SYNC_MESSAGE_STATE -> onSyncMessages().let { JobResult.SUCCESS }
            else -> JobResult.SUCCESS
        }
    }

    /** Updates the message list. */
    private suspend fun onUpdateMessages() {
        if (!user.isUserCreated) {
            UALog.d { "User has not been created, canceling messages update" }
            inbox.onUpdateMessagesFinished(false)
        } else {
            val result = updateMessages()
            inbox.notifyInboxUpdated()
            inbox.onUpdateMessagesFinished(result.getOrDefault(false))
            syncReadMessageState()
            syncDeletedMessageState()
        }
    }

    /** Sync message state. */
    private suspend fun onSyncMessages() {
        syncReadMessageState()
        syncDeletedMessageState()
    }

    /**
     * Updates the rich push user.
     *
     * @param forcefully If the user should be updated even if its been recently updated.
     */
    private suspend fun onUpdateUser(forcefully: Boolean, fetchMessages: Boolean) {
        if (!forcefully) {
            val lastUpdateTime = dataStore.getLong(LAST_UPDATE_TIME, 0)
            val now = System.currentTimeMillis()
            if (!(lastUpdateTime > now || lastUpdateTime + USER_UPDATE_INTERVAL_MS < now)) {
                // Not ready to update user, but we can still update messages
                if (fetchMessages) {
                    onUpdateMessages()
                }
                return
            }
        }
        val result = if (!user.isUserCreated) createUser() else updateUser()
        user.onUserUpdated(result.getOrDefault(false))

        if (fetchMessages) {
            onUpdateMessages()
        }
    }

    /**
     * Update the inbox messages.
     *
     * @return `true` if messages were updated, otherwise `false`.
     */
    private suspend fun updateMessages(): Result<Boolean> {
        UALog.i { "Refreshing inbox messages." }
        val channelId = channel.id
        if (channelId.isNullOrEmpty()) {
            UALog.v { "The channel ID does not exist." }
            return Result.failure(IllegalStateException("Channel ID does not exist."))
        }

        UALog.v { "Fetching inbox messages." }
        return try {
            val response = inboxApiClient.fetchMessages(
                user, channelId, dataStore.getString(LAST_MESSAGE_REFRESH_TIME, null)
            )
            UALog.v { "Fetch inbox messages response: $response" }

            // 200-299
            val responseValue = response.value
            if (response.isSuccessful && responseValue != null) {
                UALog.i { "InboxJobHandler - Received ${responseValue.size()} inbox messages." }
                updateInbox(responseValue)
                dataStore.put(LAST_MESSAGE_REFRESH_TIME, response.headers?.get("Last-Modified"))
                return Result.success(true)
            }

            // 304
            if (response.status == HttpURLConnection.HTTP_NOT_MODIFIED) {
                UALog.d { "Inbox messages already up-to-date." }
                return  Result.success(true)
            }
            UALog.d { "Unable to update inbox messages $response." }
            Result.success(false)
        } catch (e: RequestException) {
            UALog.d(e) { "Update Messages failed." }
            Result.failure(e)
        }
    }

    /**
     * Update the Rich Push Inbox.
     *
     * @param serverMessages The messages from the server.
     */
    private suspend fun updateInbox(serverMessages: JsonList) {
        val messagesToInsert: MutableList<JsonValue> = ArrayList()
        val serverMessageIds = HashSet<String>()
        for (message in serverMessages) {
            if (!message.isJsonMap) {
                UALog.e { "InboxJobHandler - Invalid message payload: $message" }
                continue
            }

            val messageId = message.optMap().opt(Message.KEY_ID).string
            if (messageId == null) {
                UALog.e { "InboxJobHandler - Invalid message payload, missing message ID: $message" }
                continue
            }

            serverMessageIds.add(messageId)

            val jsonMap = message.map
            if (jsonMap == null) {
                UALog.e { "InboxJobHandler - Invalid message payload: $message" }
                continue
            }

            val messageEntity = MessageEntity.createMessageFromPayload(messageId, jsonMap)
            if (messageEntity == null) {
                UALog.e { "InboxJobHandler - Message Entity is null" }
                continue
            }

            if (!messageDao.messageExists(messageEntity.messageId)) {
                messagesToInsert.add(message)
            }
        }

        // Bulk insert any new messages
        if (messagesToInsert.isNotEmpty()) {
            try {
                val messages = MessageEntity.createMessagesFromPayload(messagesToInsert)
                messageDao.insertMessages(messages)
            } catch (e: JsonException) {
                UALog.e(e) { "Failed to create messages from payload." }
            }
        }

        val deletedMessageIds = messageDao.getMessageIds().toMutableSet()
        deletedMessageIds.removeAll(serverMessageIds)
        messageDao.deleteMessages(deletedMessageIds)
    }

    /** Synchronizes local deleted message state with the server. */
    private suspend fun syncDeletedMessageState() {
        val channelId = channel.id
        if (channelId.isNullOrEmpty()) {
            return
        }
        val messagesToUpdate: Collection<MessageEntity> = messageDao.getLocallyDeletedMessages()
        val idsToDelete = mutableListOf<String>()
        val reportings = mutableListOf<JsonValue>()

        for (message in messagesToUpdate) {
            message.messageReporting?.let { reporting ->
                reportings.add(reporting)
                idsToDelete.add(message.messageId)
            }
        }

        if (idsToDelete.isEmpty()) {
            // nothing to do
            return
        }

        UALog.v { "Found ${idsToDelete.size} messages to delete." }
        try {
            val response = inboxApiClient.syncDeletedMessageState(user, channelId, reportings)

            UALog.v { "Delete inbox messages response: $response" }

            if (response.status == HttpURLConnection.HTTP_OK) {
                messageDao.deleteMessages(idsToDelete.toSet())
            }
        } catch (e: RequestException) {
            UALog.d(e) { "Deleted message state synchronize failed." }
        }
    }

    /** Synchronizes local read messages state with the server. */
    private suspend fun syncReadMessageState() {
        val channelId = channel.id
        if (channelId.isNullOrEmpty()) {
            return
        }
        val messagesToUpdate: Collection<MessageEntity> = messageDao.getLocallyReadMessages()
        val idsToUpdate = mutableListOf<String>()
        val reportings = mutableListOf<JsonValue>()

        for (message in messagesToUpdate) {
            message.messageReporting?.let { reporting ->
                reportings.add(reporting)
                idsToUpdate.add(message.messageId)
            }
        }

        if (idsToUpdate.isEmpty()) {
            return
        }

        UALog.v { "Found ${idsToUpdate.size} messages to mark read." }
        try {
            val response = inboxApiClient.syncReadMessageState(
                user, channelId, reportings
            )
            UALog.v { "Mark inbox messages read response: $response" }
            if (response.status == HttpURLConnection.HTTP_OK) {
                messageDao.markMessagesReadOrigin(idsToUpdate.toSet())
            }
        } catch (e: RequestException) {
            UALog.d(e) { "Read message state synchronize failed." }
        }
    }

    /**
     * Create the user.
     *
     * @return `true` if user was created, otherwise `false`.
     */
    private suspend fun createUser(): Result<Boolean> {
        val channelId = channel.id
        if (channelId.isNullOrEmpty()) {
            UALog.d { "No Channel. User will be created after channel registration finishes." }
            return Result.failure(IllegalStateException("Channel ID does not exist."))
        }
        return try {
            val response = inboxApiClient.createUser(channelId)

            // 200-209
            val userCredentials = response.value
            if (response.isSuccessful && userCredentials != null) {
                UALog.i { "InboxJobHandler - Created Rich Push user: ${userCredentials.username }" }
                dataStore.put(LAST_UPDATE_TIME, System.currentTimeMillis())
                dataStore.remove(LAST_MESSAGE_REFRESH_TIME)
                user.onCreated(userCredentials.username, userCredentials.password, channelId)
                return Result.success(true)
            }
            UALog.d { "Rich Push user creation failed: $response" }
            Result.success(false)
        } catch (e: RequestException) {
            UALog.d(e) { "User creation failed." }
            Result.failure(e)
        }
    }

    /**
     * Update the user.
     *
     * If the update returns a `401: NOT AUTHORIZED` response, re-creation of the [User]
     * will be attempted via [.createUser].
     *
     * @return `true` if user was updated, otherwise `false`.
     */
    private suspend fun updateUser(): Result<Boolean> {
        val channelId = channel.id
        if (channelId.isNullOrEmpty()) {
            UALog.d { "No Channel. Skipping Rich Push user update." }
            return Result.failure(IllegalStateException("Channel ID does not exist."))
        }
        return try {
            val response = inboxApiClient.updateUser(user, channelId)
            UALog.v { "Update Rich Push user response: $response" }
            when (response.status) {
                HttpURLConnection.HTTP_OK -> {
                    UALog.i { "Rich Push user updated." }
                    dataStore.put(LAST_UPDATE_TIME, System.currentTimeMillis())
                    user.onUpdated(channelId)
                    Result.success(true)
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> {
                    UALog.d { "Re-creating Rich Push user." }
                    dataStore.put(LAST_UPDATE_TIME, 0)
                    createUser()
                }
                else -> {
                    dataStore.put(LAST_UPDATE_TIME, 0)
                    Result.success(false)
                }
            }
        } catch (e: RequestException) {
            UALog.d(e) { "User update failed." }
            Result.failure(e)
        }
    }

    internal companion object {

        /** Starts the service in order to update just the [Message]'s messages. */
        const val ACTION_RICH_PUSH_MESSAGES_UPDATE = "ACTION_RICH_PUSH_MESSAGES_UPDATE"

        /** Starts the service to sync message state. */
        const val ACTION_SYNC_MESSAGE_STATE = "ACTION_SYNC_MESSAGE_STATE"

        /** Starts the service in order to update just the [User] itself. */
        const val ACTION_RICH_PUSH_USER_UPDATE = "ACTION_RICH_PUSH_USER_UPDATE"

        /** Extra key to indicate if the rich push user needs to be updated forcefully. */
        const val EXTRA_FORCEFULLY = "EXTRA_FORCEFULLY"

        /** Extra key to indicate if messages should be fetched after updating the rich push user. */
        const val EXTRA_FETCH_MESSAGES = "EXTRA_FETCH_MESSAGES"

        @VisibleForTesting
        internal const val LAST_MESSAGE_REFRESH_TIME = "com.urbanairship.messages.LAST_MESSAGE_REFRESH_TIME"

        private const val LAST_UPDATE_TIME = "com.urbanairship.user.LAST_UPDATE_TIME"

        private val USER_UPDATE_INTERVAL_MS = 24.hours.inWholeMilliseconds
    }
}
