/* Copyright Airship and Contributors */

package com.urbanairship.chat

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.paging.DataSource
import com.urbanairship.Logger
import com.urbanairship.PreferenceDataStore
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.ApplicationListener
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.channel.AirshipChannelListener
import com.urbanairship.chat.api.ChatApiClient
import com.urbanairship.chat.api.ChatConnection
import com.urbanairship.chat.api.ChatConnection.CloseReason
import com.urbanairship.chat.api.ChatResponse
import com.urbanairship.chat.data.ChatDao
import com.urbanairship.chat.data.ChatDatabase
import com.urbanairship.chat.data.MessageEntity
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.util.DateUtils
import java.lang.IllegalArgumentException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Airship Chat.
 */
class Conversation

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@VisibleForTesting
internal constructor(
    private val dataStore: PreferenceDataStore,
    private val channel: AirshipChannel,
    private val chatDao: ChatDao,
    private val connection: ChatConnection,
    private val apiClient: ChatApiClient,
    private val activityMonitor: ActivityMonitor,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher
) {

    /**
     * "Default" convenience constructor.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal constructor(
        context: Context,
        dataStore: PreferenceDataStore,
        config: AirshipRuntimeConfig,
        channel: AirshipChannel
    ) : this(dataStore, channel, ChatDatabase.createDatabase(context, config).chatDao(), ChatConnection(config),
            ChatApiClient(config), GlobalActivityMonitor.shared(context), CoroutineScope(AirshipDispatchers.newSingleThreadDispatcher()),
            AirshipDispatchers.IO)

    companion object {
        private const val UVP_KEY = "com.urbanairship.chat.UVP"
        private const val RECONNECT_DELAY_MS = 3000L
    }

    private var retryConnectionJob: Job? = null
    private var isPendingSent = false
    private var enabledState: MutableStateFlow<Boolean> = MutableStateFlow(false)

    internal var isEnabled: Boolean
        get() = enabledState.value
        set(value) { enabledState.value = value }

    /**
     * Flag indicating if the conversation is connected or not.
     */
    var isConnected: Boolean = false
        private set

    /**
     * The message data source to be used in a paging adapter.
     */
    val messageDataSourceFactory: DataSource.Factory<Int, ChatMessage> by lazy {
        chatDao.getMessageDataSourceFactory().map { input: MessageEntity? ->
            input?.toChatMessage()
        }
    }

    init {
        connection.chatListener = ChatListener()
        activityMonitor.addApplicationListener(object : ApplicationListener {
            override fun onForeground(milliseconds: Long) = updateConnection()
            override fun onBackground(milliseconds: Long) = updateConnection()
        })

        channel.addChannelListener(object : AirshipChannelListener {
            override fun onChannelCreated(channelId: String) = updateConnection()
            override fun onChannelUpdated(channelId: String) = updateConnection()
        })

        scope.launch {
            enabledState.collect {
                updateConnection()
            }
        }
    }

    /**
     * Sends a message.
     * @param text The message text.
     * @param attachmentUrl An attachment URL.
     * @throws IllegalArgumentException if both text and attachment are null.
     */
    @JvmOverloads
    fun sendMessage(text: String?, attachmentUrl: String? = null) {
        require(text != null || attachmentUrl != null) {
            "Missing text and attachmentUrl"
        }

        val requestId = UUID.randomUUID().toString()

        scope.launch {
            val pending = MessageEntity(
                    messageId = requestId,
                    text = text,
                    attachment = attachmentUrl,
                    createdOn = System.currentTimeMillis(),
                    isPending = true,
                    direction = ChatDirection.OUTGOING
            )

            chatDao.upsert(pending)

            if (connection.isOpenOrOpening && isPendingSent) {
                connection.sendMessage(text, attachmentUrl, requestId)
            } else {
                updateConnection()
            }
        }
    }

    internal fun updateConnection(forceOpen: Boolean = false) {
        scope.launch {
            val uvp = getUvp()
            val isForeground = withContext(Dispatchers.Main) {
                activityMonitor.isAppForegrounded
            }

            if (uvp == null || !enabledState.value) {
                connection.close()
                return@launch
            }

            if (enabledState.value && (!isPendingSent || forceOpen || isForeground || chatDao.hasPendingMessages())) {
                if (!connection.isOpenOrOpening) {
                    try {
                        connection.open(uvp)
                        isPendingSent = false
                        connection.fetchConversation()
                    } catch (e: Exception) {
                        Logger.error(e, "Failed to establish chat WebSocket connection!")
                        retryConnectionUpdate()
                    }
                }
            } else {
                retryConnectionJob?.cancel()
                connection.close()
            }
        }
    }

    private fun retryConnectionUpdate() {
        Logger.verbose("Scheduling updateConnection in ${RECONNECT_DELAY_MS}ms...")
        retryConnectionJob?.cancel()
        retryConnectionJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            updateConnection()
        }
    }

    private suspend fun getUvp(): String? = withContext(ioDispatcher) {
        if (enabledState.value) {
            dataStore.getString(UVP_KEY, null)?.let {
                Logger.verbose("Loaded uvp from data store")
                it
            } ?: createUvp()
        } else {
            null
        }
    }

    @WorkerThread
    private fun createUvp(): String? {
        return channel.id?.let {
            try {
                val uvp = apiClient.fetchUvp(it)
                Logger.verbose("Fetched uvp from network")
                dataStore.put(UVP_KEY, uvp)
                uvp
            } catch (e: java.lang.Exception) {
                Logger.debug(e, "Failed to fetch uvp")
                null
            }
        }
    }

    private inner class ChatListener : ChatConnection.ChatListener {
        override fun onChatResponse(response: ChatResponse) {
            when (response) {
                is ChatResponse.ConversationLoaded -> with(response.conversation) {
                    val messages = messages ?: emptyList()
                    Logger.verbose("Conversation loaded: %s", messages)
                    scope.launch {
                        messages.forEach { message ->
                            if (message.requestId != null) {
                                chatDao.delete(message.requestId)
                            }
                            chatDao.upsert(message.toMessageEntity())
                        }
                        if (connection.isOpenOrOpening) {
                            chatDao.getPendingMessages().forEach { message ->
                                connection.sendMessage(message.text, message.attachment, message.messageId)
                            }

                            isPendingSent = true
                        }
                        updateConnection()
                    }
                }

                is ChatResponse.MessageReceived -> with(response.message) {
                    Logger.verbose("Message sent successfully: %s", message)
                    scope.launch {
                        if (message.requestId != null) {
                            chatDao.delete(message.requestId)
                        }
                        chatDao.upsert(message.toMessageEntity())
                        updateConnection()
                    }
                }
                is ChatResponse.NewMessage -> with(response.message) {
                    Logger.verbose("New message received: %s", message)
                    scope.launch {
                        chatDao.upsert(message.toMessageEntity())
                    }
                }
            }
        }

        override fun onOpen() {
            Logger.verbose("Chat connection open!")
            this@Conversation.isConnected = true
        }

        override fun onClose(reason: CloseReason) {
            this@Conversation.isConnected = false
            when (reason) {
                is CloseReason.Manual ->
                    Logger.verbose("Chat connection closed! $reason")
                is CloseReason.Server -> {
                    Logger.info("Chat connection was closed remotely! $reason")
                    this@Conversation.retryConnectionUpdate()
                }
                is CloseReason.Error -> {
                    Logger.warn(reason.error, "Chat connection was closed remotely! $reason")
                    this@Conversation.retryConnectionUpdate()
                }
            }
        }
    }
}

internal fun MessageEntity.toChatMessage(): ChatMessage {
    return ChatMessage(
            messageId = this.messageId,
            text = this.text,
            createdOn = this.createdOn,
            direction = this.direction,
            attachmentUrl = this.attachment,
            pending = this.isPending
    )
}

internal fun ChatResponse.Message.toMessageEntity(): MessageEntity {
    val chatDirection = if (direction == 0) {
        ChatDirection.OUTGOING
    } else {
        ChatDirection.INCOMING
    }

    return MessageEntity(
            messageId = this.requestId ?: this.messageId,
            text = this.text,
            attachment = this.attachment,
            createdOn = DateUtils.parseIso8601(this.createdOn, 0),
            direction = chatDirection,
            isPending = false
    )
}
