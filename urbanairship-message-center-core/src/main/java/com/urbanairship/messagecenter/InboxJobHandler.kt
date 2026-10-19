/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter

import android.net.Uri
import androidx.annotation.VisibleForTesting
import com.urbanairship.preferences.AsyncPrefKey
import com.urbanairship.preferences.PreferenceStore
import com.urbanairship.UALog
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.iam.content.AirshipLayout
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.util.Clock
import java.net.HttpURLConnection
import kotlin.time.Duration.Companion.hours

/** Job handler for [Inbox] component. */
internal class InboxJobHandler @VisibleForTesting internal constructor(
    private val user: User,
    private val dataStore: PreferenceStore,
    private val messageDao: MessageDao,
    private val inboxApiClient: InboxApiClient,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
) {
    internal constructor(
        user: User,
        runtimeConfig: AirshipRuntimeConfig,
        dataStore: PreferenceStore,
        messageDao: MessageDao
    ) : this(
        user = user,
        dataStore = dataStore,
        messageDao = messageDao,
        inboxApiClient = InboxApiClient(runtimeConfig)
    )

    /** Delete saved state from the data store. */
    internal suspend fun removeStoredData() {
        dataStore.remove(LAST_MESSAGE_REFRESH_TIME)
        dataStore.remove(LAST_UPDATE_TIME)
        dataStore.remove(USER_REQUIRES_UPDATE)
    }

    /** Forces a user update on the next sync, regardless of [USER_UPDATE_INTERVAL_MS]. */
    internal suspend fun requireUserUpdate() {
        dataStore.put(USER_REQUIRES_UPDATE, true)
    }

    suspend fun getOrCreateUserCredentials(channelId: String): UserCredentials? {
        val credentials = user.userCredentials
        return if (credentials == null) {
            createUser(channelId)
        } else if (isUserUpdateDue(channelId)) {
            updateUser(credentials, channelId)
        } else {
            credentials
        }
    }

    /**
     * Returns `true` if the user must be re-sent: the channel changed, an update was required,
     * or more than [USER_UPDATE_INTERVAL_MS] has passed since the last one.
     *
     * A last update time in the future means the clock moved backwards, so it counts as due.
     *
     * @param channelId The current channel ID.
     * @return `true` if [updateUser] should run before syncing.
     */
    private suspend fun isUserUpdateDue(channelId: String): Boolean {
        if (user.registeredChannelId != channelId) return true
        if (dataStore.get(USER_REQUIRES_UPDATE) == true) return true

        val lastUpdateTime = dataStore.get(LAST_UPDATE_TIME) ?: 0L
        val now = clock.now().toEpochMilli()
        return lastUpdateTime > now || now - lastUpdateTime > USER_UPDATE_INTERVAL_MS
    }

    suspend fun syncMessageList(
        userCredentials: UserCredentials,
        channelId: String
    ): Boolean {
        UALog.i { "Refreshing inbox messages." }
        val response = inboxApiClient.fetchMessages(
            userCredentials, channelId, dataStore.get(LAST_MESSAGE_REFRESH_TIME)
        )

        UALog.v { "Fetch inbox messages response: $response" }

        // 200-299
        val responseValue = response.value
        if (response.isSuccessful && responseValue != null) {
            UALog.i { "InboxJobHandler - Received ${responseValue.size()} inbox messages." }
            updateInbox(responseValue)
            dataStore.put(LAST_MESSAGE_REFRESH_TIME, response.headers?.get("Last-Modified"))
            return true
        }

        // 304
        if (response.status == HttpURLConnection.HTTP_NOT_MODIFIED) {
            UALog.d { "Inbox messages already up-to-date." }
            return true
        }

        UALog.d { "Unable to update inbox messages $response." }
        return false
    }

    suspend fun loadAirshipLayout(message: Message): AirshipLayout? {
        if (message.contentType !is Message.ContentType.Native) {
            return null
        }

        try {
            val url = Uri.parse(message.bodyUrl)
            val response = inboxApiClient.loadAirshipLayout(url, user.userCredentials)
            return response.value?.let { AirshipLayout(it) }
        } catch (ex: Exception) {
            UALog.w { "Failed to load Airship layout: ${ex.message}" }
            return null
        }
    }

    /**
     * Update the Rich Push Inbox.
     *
     * @param serverMessages The messages from the server.
     */
    private suspend fun updateInbox(serverMessages: JsonList) {
        val parsedMessages = serverMessages.mapNotNull {
            val content = it.map ?: run {
                UALog.e { "InboxJobHandler - Invalid message payload: $it" }
                return@mapNotNull null
            }

            val messageId = content[Message.KEY_ID]?.string ?: run {
                UALog.e { "InboxJobHandler - Invalid message payload, missing message ID: $it" }
                return@mapNotNull null
            }

            val dbEntry = MessageEntity.createMessageFromPayload(messageId, content)
            if (dbEntry == null) {
                UALog.e { "InboxJobHandler - Message Entity is null" }
                return@mapNotNull null
            }

            return@mapNotNull messageId to it
        }
            .toMap()

        val messagesToInsert = parsedMessages.filter { (messageId, _) ->
            !messageDao.messageExists(messageId)
        }

        // Bulk insert any new messages
        if (messagesToInsert.isNotEmpty()) {
            try {
                val messages = MessageEntity.createMessagesFromPayload(messagesToInsert.values.toList())
                messageDao.insertMessages(messages)
            } catch (e: JsonException) {
                UALog.e(e) { "Failed to create messages from payload." }
            }
        }

        val deletedMessageIds = messageDao.getMessageIds().toMutableSet()
        deletedMessageIds.removeAll(parsedMessages.keys)
        messageDao.deleteMessages(deletedMessageIds)
    }

    /** Synchronizes local deleted message state with the server. */
    suspend fun syncDeletedMessageState(
        userCredentials: UserCredentials,
        channelId: String
    ): Boolean {
        val messagesToUpdate = messageDao.getLocallyDeletedMessages().mapNotNull { message ->
            message.messageReporting?.let {
                message.messageId to it
            }
        }

        if (messagesToUpdate.isEmpty()) {  return true }

        UALog.v { "Found ${messagesToUpdate.size} messages to delete." }

        val response = inboxApiClient.syncDeletedMessageState(
            userCredentials,
            channelId,
            messagesToUpdate.map { it.second }
        )

        UALog.v { "Delete inbox messages response: $response" }

        return if (response.status == HttpURLConnection.HTTP_OK) {
            messageDao.deleteMessages(messagesToUpdate.map { it.first }.toSet())
            true
        } else {
            UALog.d(response.exception) { "Deleted message state synchronize failed." }
            false
        }
    }

    /** Synchronizes local read messages state with the server. */
    suspend fun syncReadMessageState(
        userCredentials: UserCredentials,
        channelId: String
    ): Boolean {
        val messagesToUpdate = messageDao.getLocallyReadMessages().mapNotNull { message ->
            message.messageReporting?.let {
                message.messageId to it
            }
        }

        if (messagesToUpdate.isEmpty()) {  return true  }

        UALog.v { "Found ${messagesToUpdate.size} messages to mark read." }
        val response = inboxApiClient.syncReadMessageState(
            userCredentials,
            channelId,
            messagesToUpdate.map { it.second }
        )

        UALog.v { "Mark inbox messages read response: $response" }
        return if (response.status == HttpURLConnection.HTTP_OK) {
            messageDao.markMessagesReadOrigin(messagesToUpdate.map { it.first }.toSet())
            true
        } else {
            false
        }
    }

    private suspend fun createUser(
        channelId: String
    ): UserCredentials? {
        val response = inboxApiClient.createUser(channelId)
        val userCredentials = response.value

        return if (response.isSuccessful && userCredentials != null) {
            UALog.i { "InboxJobHandler - Created Rich Push user: ${userCredentials.username }" }
            dataStore.put(LAST_UPDATE_TIME, clock.now().toEpochMilli())
            dataStore.remove(USER_REQUIRES_UPDATE)
            dataStore.remove(LAST_MESSAGE_REFRESH_TIME)
            user.onCreated(userCredentials, channelId)
            user.onUserUpdated(true)
            userCredentials
        } else {
            UALog.d { "Rich Push user creation failed: $response" }
            user.onUserUpdated(false)
            null
        }
    }

    private suspend fun updateUser(
        userCredentials: UserCredentials,
        channelId: String
    ): UserCredentials? {
        val response = inboxApiClient.updateUser(userCredentials, channelId)
        UALog.v { "Update Rich Push user response: $response" }
        return when (response.status) {
            HttpURLConnection.HTTP_OK -> {
                UALog.i { "Rich Push user updated." }
                dataStore.put(LAST_UPDATE_TIME, clock.now().toEpochMilli())
                dataStore.remove(USER_REQUIRES_UPDATE)
                user.onUpdated(channelId)
                user.onUserUpdated(true)
                userCredentials
            }
            HttpURLConnection.HTTP_UNAUTHORIZED -> {
                UALog.d { "Re-creating Rich Push user." }
                dataStore.put(LAST_UPDATE_TIME, 0L)
                createUser(channelId)
            }
            else -> {
                // The stored credentials still work for the message sync; the update retries next sync.
                dataStore.put(LAST_UPDATE_TIME, 0L)
                user.onUserUpdated(false)
                userCredentials
            }
        }
    }

    internal companion object {

        @VisibleForTesting
        internal val LAST_MESSAGE_REFRESH_TIME = AsyncPrefKey.string("com.urbanairship.messages.LAST_MESSAGE_REFRESH_TIME")

        internal val LAST_UPDATE_TIME = AsyncPrefKey.long("com.urbanairship.user.LAST_UPDATE_TIME")

        internal val USER_REQUIRES_UPDATE = AsyncPrefKey.boolean("com.urbanairship.user.REQUIRES_UPDATE")

        internal val USER_UPDATE_INTERVAL_MS = 24.hours.inWholeMilliseconds
    }
}
