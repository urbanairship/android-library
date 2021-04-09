/* Copyright Airship and Contributors */
package com.urbanairship.chat

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.AirshipComponent
import com.urbanairship.Logger
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UAirship
import com.urbanairship.app.ApplicationListener
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.chat.ChatConnection.CloseReason
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Airship Chat.
 */
class AirshipChat
/**
 * Full constructor (for tests).
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) @VisibleForTesting internal constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    private val channel: AirshipChannel,
    private val connection: ChatConnection,
    private val apiClient: ChatApiClient
) : AirshipComponent(context, dataStore) {

    companion object {
        // PreferenceDataStore keys
        private const val ENABLED_KEY = "com.urbanairship.chat.CHAT"
        private const val UVP_KEY = "com.urbanairship.chat.UVP"

        private const val RECONNECT_DELAY_MS = 3000L

        /**
         * Gets the shared `AirshipChat` instance.
         *
         * @return an instance of `AirshipChat`.
         */
        @JvmStatic
        fun shared(): AirshipChat {
            return UAirship.shared().requireComponent(AirshipChat::class.java)
        }
    }

    private val job = SupervisorJob()
    private val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main
    private val coroutineScope = CoroutineScope(coroutineContext)

    private val activityMonitor by lazy { GlobalActivityMonitor.shared(context) }

    private var uvp: String? = null
    private var reconnectJob: Job? = null

    /**
     * "Default" convenience constructor.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal constructor(
        context: Context,
        dataStore: PreferenceDataStore,
        channel: AirshipChannel
    ) : this(context, dataStore, channel, ChatConnection(), ChatApiClient()) {
        connection.chatListener = ChatListener()
    }

    init {
        dataStore.addListener { key ->
            if (key == ENABLED_KEY) {
                update()
            }
        }
    }

    /**
     * Enables or disables Airship Chat.
     *
     * The value is persisted in shared preferences.
     */
    var isEnabled: Boolean
        get() = dataStore.getBoolean(ENABLED_KEY, false)
        set(isEnabled) = dataStore.put(ENABLED_KEY, isEnabled)

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun onAirshipReady(airship: UAirship) {
        super.onAirshipReady(airship)

        // Add an application listener to open and close the connection when the app is
        // foregrounded and backgrounded.
        activityMonitor.addApplicationListener(applicationListener)

        if (isEnabled) {
            // Attempt to load a previously generated UVP from the data store.
            uvp = loadUvp()

            // If there was no stored UVP, register a callback for the channel ID and generate a
            // UVP when the ID is available.
            if (uvp == null) {
                channel.channelId.addResultCallback { channelId ->
                    channelId?.let { onChannelIdAvailable(it) }
                }
            }

            update()
        }
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun tearDown() {
        super.tearDown()

        activityMonitor.removeApplicationListener(applicationListener)

        disconnect()
        coroutineScope.cancel()
    }

    /** Update the AirshipChat enabled state, connecting or disconnecting as necessary. */
    private fun update() {
        if (isEnabled) {
            // Connect now if we're in the foreground.
            if (activityMonitor.isAppForegrounded) {
                connect()
            }
        } else {
            disconnect()
        }
    }

    /** Connects the WebSocket. */
    private fun connect() {
        if (connection.isOpenOrOpening) {
            Logger.debug("Ignoring connect(). Already connecting or connected!")
            return
        }

        val uvp = uvp
        if (uvp == null) {
            Logger.debug("Ignoring connect(). UVP not available yet.")
            scheduleReconnect()
            return
        }

        try {
            connection.open(uvp)
        } catch (e: Exception) {
            Logger.error(e, "Failed to establish chat WebSocket connection!")
            scheduleReconnect()
        }
    }

    private fun disconnect() {
        if (!connection.isOpenOrOpening) {
            Logger.debug("Ignoring disconnect(). Not connecting or connected!")
            return
        }

        reconnectJob?.cancel()
        connection.close()
    }

    private fun scheduleReconnect() {
        Logger.verbose("Scheduling reconnect in ${RECONNECT_DELAY_MS}ms...")
        reconnectJob?.cancel()
        reconnectJob = coroutineScope.launch {
            // TODO: use exponential backoff instead of hardcoded delay
            delay(RECONNECT_DELAY_MS)
            connect()
        }
    }

    private fun onChannelIdAvailable(channelId: String) {
        if (uvp != null) {
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            uvp = getUvp(channelId)
        }
    }

    private fun getUvp(channelId: String): String {
        return loadUvp() ?: fetchUvp(channelId)
    }

    private fun loadUvp(): String? {
        val uvp = dataStore.getString(UVP_KEY, null)
        if (uvp != null) {
            Logger.verbose("Loaded uvp from data store")
        }
        return uvp
    }

    private fun fetchUvp(channelId: String): String {
        val uvp = apiClient.fetchUvp(channelId)
        dataStore.put(UVP_KEY, uvp)

        Logger.verbose("Fetched uvp from network")
        return uvp
    }

    private inner class ChatListener : ChatConnection.ChatListener {

        override fun onChatResponse(response: ChatResponse) {
            when (response) {
                is ChatResponse.ConversationLoaded -> with(response.conversation) {
                    val messages = messages ?: emptyList()
                    Logger.verbose("Conversation loaded. ${messages.size} messages.")

                    // TODO: sync messages with local db and then we're ready to chat!
                    // connection.sendMessage("Testing! (${Random.nextInt(1000)})")
                }
                is ChatResponse.MessageReceived -> with(response.message) {
                    Logger.verbose("Message sent successfully: '${message.text}'")
                }
                is ChatResponse.NewMessage -> with(response.message) {
                    Logger.verbose("New message received: '${message.text}'")
                }
                is ChatResponse.ServerError -> {
                    Logger.debug("Error received: '${response.message}'")
                    // TODO: handle errors if we can or bubble them up to the UI
                }
            }
        }

        override fun onOpen() {
            Logger.verbose("Chat connection open!")

            connection.getConversation()
        }

        override fun onClose(reason: CloseReason) {
            when (reason) {
                is CloseReason.Manual ->
                    Logger.verbose("Chat connection closed! $reason")
                is CloseReason.Server -> {
                    Logger.info("Chat connection was closed remotely! $reason")
                    disconnect()
                }
                is CloseReason.Error -> {
                    Logger.warn(reason.error, "Chat connection was closed remotely! $reason")
                    disconnect()
                }
            }
        }
    }

    private val applicationListener = object : ApplicationListener {
        override fun onForeground(milliseconds: Long) {
            if (isEnabled) {
                connect()
            }
        }
        override fun onBackground(milliseconds: Long) {
            if (isEnabled) {
                disconnect()
            }
        }
    }
}
