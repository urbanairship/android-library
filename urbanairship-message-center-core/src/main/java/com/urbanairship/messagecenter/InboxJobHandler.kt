/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter

import android.net.Uri
import androidx.annotation.VisibleForTesting
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UALog
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.iam.content.AirshipLayout
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import java.net.HttpURLConnection
import kotlin.time.Duration.Companion.hours

/** Job handler for [Inbox] component. */
internal class InboxJobHandler @VisibleForTesting internal constructor(
    private val user: User,
    private val dataStore: PreferenceDataStore,
    private val messageDao: MessageDao,
    private val inboxApiClient: InboxApiClient,
) {
    internal constructor(
        user: User,
        runtimeConfig: AirshipRuntimeConfig,
        dataStore: PreferenceDataStore,
        messageDao: MessageDao
    ) : this(
        user = user,
        dataStore = dataStore,
        messageDao = messageDao,
        inboxApiClient = InboxApiClient(runtimeConfig)
    )

    /** Delete saved state from the data store. */
    internal fun removeStoredData() {
        dataStore.remove(LAST_MESSAGE_REFRESH_TIME)
        dataStore.remove(LAST_UPDATE_TIME)
    }

    suspend fun getOrCreateUserCredentials(channelId: String): UserCredentials? {
        val lastUpdateTime = dataStore.getLong(LAST_UPDATE_TIME, 0)
        val now = System.currentTimeMillis()

        val credentials = user.userCredentials
        return if (credentials == null) {
            createUser(channelId)
        } else if (user.registeredChannelId != channelId || lastUpdateTime > now || lastUpdateTime + USER_UPDATE_INTERVAL_MS > now) {
            updateUser(credentials, channelId)
        } else {
            credentials
        }
    }

    suspend fun syncMessageList(
        userCredentials: UserCredentials,
        channelId: String
    ): Boolean {
        UALog.i { "Refreshing inbox messages." }
        val response = inboxApiClient.fetchMessages(
            userCredentials, channelId, dataStore.getString(LAST_MESSAGE_REFRESH_TIME, null)
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
        if (message.contentType != Message.ContentType.NATIVE) {
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
            dataStore.put(LAST_UPDATE_TIME, System.currentTimeMillis())
            dataStore.remove(LAST_MESSAGE_REFRESH_TIME)
            user.onCreated(userCredentials, channelId)
            userCredentials
        } else {
            UALog.d { "Rich Push user creation failed: $response" }
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
                dataStore.put(LAST_UPDATE_TIME, System.currentTimeMillis())
                user.onUpdated(channelId)
                userCredentials
            }
            HttpURLConnection.HTTP_UNAUTHORIZED -> {
                UALog.d { "Re-creating Rich Push user." }
                dataStore.put(LAST_UPDATE_TIME, 0)
                createUser(channelId)
            }
            else -> {
                dataStore.put(LAST_UPDATE_TIME, 0)
                null
            }
        }
    }

    internal companion object {

        @VisibleForTesting
        internal const val LAST_MESSAGE_REFRESH_TIME = "com.urbanairship.messages.LAST_MESSAGE_REFRESH_TIME"

        internal const val LAST_UPDATE_TIME = "com.urbanairship.user.LAST_UPDATE_TIME"

        internal val USER_UPDATE_INTERVAL_MS = 24.hours.inWholeMilliseconds
    }
}
