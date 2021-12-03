/* Copyright Airship and Contributors */

package com.urbanairship.chat

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import androidx.paging.DataSource
import com.urbanairship.Logger
import com.urbanairship.PendingResult
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
import com.urbanairship.chat.data.ChatDatabase
import com.urbanairship.chat.data.MessageEntity
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.util.DateUtils
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Chat Conversation.
 */
class Conversation
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@VisibleForTesting
internal constructor(
    private val context: Context,
    private val dataStore: PreferenceDataStore,
    private val config: AirshipRuntimeConfig,
    private val channel: AirshipChannel,
    private val chatDatabase: ChatDatabase,
    private val connection: ChatConnection,
    private val apiClient: ChatApiClient,
    private val activityMonitor: ActivityMonitor,
    private val connectionDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher
) {

    private val chatDao = chatDatabase.chatDao()

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
    ) : this(context, dataStore, config, channel, ChatDatabase.createDatabase(context, config), ChatConnection(config),
            ChatApiClient(config), GlobalActivityMonitor.shared(context), AirshipDispatchers.newSingleThreadDispatcher(),
            AirshipDispatchers.IO)

    companion object {
        private const val UVP_KEY = "com.urbanairship.chat.UVP"
        private const val RECONNECT_DELAY_MS = 10000L
        private const val REFRESH_TIMEOUT_MS = 30000L
    }

    private var retryConnectionJob: Job? = null
    private var isPendingSent: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private var enabledState: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val scope = CoroutineScope(connectionDispatcher)
    private val conversationListeners = CopyOnWriteArrayList<ConversationListener>()
    private var shouldConnect = false

    internal var isEnabled: Boolean
        get() = enabledState.value
        set(value) { enabledState.value = value }

    /**
     * Flag indicating if the conversation is connected or not.
     */
    var isConnected: Boolean = false
        private set

    /**
     * Determines which agent a conversation gets assigned to by matching this value to an agent in Live Chat Manager.
     */
    var routing: ChatRouting? = ChatRouting("")

    /**
     * The message data source to be used in a paging adapter.
     */
    val messageDataSourceFactory: DataSource.Factory<Int, ChatMessage> by lazy {
        chatDao.getMessageDataSourceFactory().map { input: MessageEntity ->
            input.toChatMessage()
        }
    }

    init {
        connection.chatListener = ChatListener()

        activityMonitor.addApplicationListener(object : ApplicationListener {
            override fun onForeground(milliseconds: Long) = launchConnectionUpdate()
            override fun onBackground(milliseconds: Long) {
                shouldConnect = false
                launchConnectionUpdate()
            }
        })
        channel.addChannelListener(object : AirshipChannelListener {
            override fun onChannelCreated(channelId: String) = launchConnectionUpdate()
            override fun onChannelUpdated(channelId: String) = launchConnectionUpdate()
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
        if (!isEnabled) {
            Logger.error("AirshipChat disabled. Unable to send message.")
        }

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

            if (connection.isOpenOrOpening && isPendingSent.value) {
                connection.sendMessage(text, attachmentUrl, requestId, ChatDirection.OUTGOING, null, routing)
            } else {
                updateConnection()
            }
        }
    }

    /**
     * Returns a list of up to 50 of the most recent messages in the conversation.
     *
     * @return A {@code PendingResult} containing a list of messages.
     */
    fun getMessages(): PendingResult<List<ChatMessage>> {
        val pendingResult = PendingResult<List<ChatMessage>>()
        scope.launch(ioDispatcher) {
            pendingResult.result = chatDao.getMessages().map { it.toChatMessage() }
        }
        return pendingResult
    }

    /**
     * Sends messages from a JSONArray.
     * @param messages The list of messages to send.
     */
    internal fun addIncoming(messages: List<ChatIncomingMessage>) {
        for (msg in messages) {
            val requestId = msg.id ?: UUID.randomUUID().toString()
            val createdOn = msg.date?.let { DateUtils.parseIso8601(it) } ?: System.currentTimeMillis()

            scope.launch {
                val pending = MessageEntity(
                        messageId = requestId,
                        text = msg.message,
                        attachment = msg.url,
                        createdOn = createdOn,
                        isPending = true,
                        direction = ChatDirection.INCOMING
                )

                chatDao.upsert(pending)

                if (connection.isOpenOrOpening && isPendingSent.value) {
                    connection.sendMessage(msg.message, msg.url, requestId, ChatDirection.INCOMING, createdOn, routing)
                } else {
                    updateConnection()
                }
            }
        }
    }

    /**
     * Adds a conversation listener.
     * @param listener The listener.
     */
    fun addConversationListener(listener: ConversationListener) {
        conversationListeners.add(listener)
    }

    /**
     * Removes a conversation listener.
     * @param listener The listener.
     */
    fun removeConversationListener(listener: ConversationListener) {
        conversationListeners.remove(listener)
    }

    internal fun clearData() {
        scope.launch {
            connection.close()
            dataStore.remove(UVP_KEY)
            if (chatDatabase.exists(context)) {
                chatDao.deleteMessages()
            }
        }
    }

    suspend fun refreshMessages(timeOut: Long = REFRESH_TIMEOUT_MS): Boolean {
        return withContext(connectionDispatcher) {
            withTimeoutOrNull(timeOut) {
                if (updateConnection(true)) {
                    if (!isPendingSent.value) {
                        isPendingSent.drop(1).first()
                    } else {
                        true
                    }
                } else {
                    false
                }
            } ?: false
        }
    }

    internal fun launchConnectionUpdate(forceOpen: Boolean = false) {
        scope.launch {
            updateConnection(forceOpen)
        }
    }

    private suspend fun updateConnection(forceOpen: Boolean = false): Boolean {
        return withContext(connectionDispatcher) {
            retryConnectionJob?.cancel()

            if (!(enabledState.value && isConfigured())) {
                connection.close()
                return@withContext false
            }

            val uvp = getUvp()
            if (uvp == null) {
                connection.close()
                retryConnectionUpdate()
                return@withContext false
            }

            val isForeground = withContext(Dispatchers.Main) {
                activityMonitor.isAppForegrounded
            }

            if (!isPendingSent.value || (isForeground && shouldConnect) || forceOpen || chatDao.hasPendingMessages()) {
                if (!connection.isOpenOrOpening) {
                    try {
                        connection.open(uvp)
                        isPendingSent.value = false
                        connection.fetchConversation()
                        return@withContext true
                    } catch (e: Exception) {
                        Logger.error(e, "Failed to establish chat WebSocket connection!")
                        retryConnectionUpdate()
                        return@withContext false
                    }
                } else {
                    return@withContext true
                }
            } else {
                connection.close()
                return@withContext false
            }
        }
    }

    private fun retryConnectionUpdate() {
        Logger.verbose("Scheduling updateConnection in ${RECONNECT_DELAY_MS}ms...")
        retryConnectionJob?.cancel()
        retryConnectionJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            launchConnectionUpdate()
        }
    }

    /**
     * Opens the connection for the Conversation
     */
    fun connect() {
        shouldConnect = true
        launchConnectionUpdate()
    }

    /**
     * Opens the connection and attach a LifecycleObserver so the connection stays open
     * while the Conversation is on foreground.
     * @param lifecycleOwner The view's LifecycleOwner
     */
    fun connect(lifecycleOwner: LifecycleOwner) {
        val lifeCycleObserver: LifecycleObserver = object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_START)
            fun onStart() {
                connect()
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            fun onDestroy() {
                lifecycleOwner.lifecycle.removeObserver(this)
            }
        }

        lifecycleOwner.lifecycle.addObserver(lifeCycleObserver)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            connect()
        }
    }

    private suspend fun getUvp(): String? = withContext(ioDispatcher) {
        dataStore.getString(UVP_KEY, null)?.let {
            Logger.verbose("Loaded uvp from data store")
            it
        } ?: createUvp()
    }

    private fun isConfigured(): Boolean {
        return config.urlConfig.isChatUrlAvailable && config.urlConfig.isChatSocketUrlAvailable
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

    private fun notifyConversationUpdated() {
        conversationListeners.forEach { listener ->
            listener.onConversationUpdated()
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
                                val date = if (message.direction == ChatDirection.INCOMING) message.createdOn else null
                                connection.sendMessage(message.text, message.attachment, message.messageId, message.direction, date, routing)
                            }
                            isPendingSent.value = true
                        }
                        updateConnection()
                        notifyConversationUpdated()
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
                        notifyConversationUpdated()
                    }
                }
                is ChatResponse.NewMessage -> with(response.message) {
                    Logger.verbose("New message received: %s", message)
                    scope.launch {
                        chatDao.upsert(message.toMessageEntity())
                        notifyConversationUpdated()
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
    var pending = false
    if (this.direction == ChatDirection.OUTGOING) {
        pending = this.isPending
    }

    return ChatMessage(
            messageId = this.messageId,
            text = this.text,
            createdOn = this.createdOn,
            direction = this.direction,
            attachmentUrl = this.attachment,
            pending = pending
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
