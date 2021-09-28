/* Copyright Airship and Contributors */

package com.urbanairship.chat.api

import androidx.annotation.RestrictTo
import com.urbanairship.Logger
import com.urbanairship.chat.AirshipDispatchers
import com.urbanairship.chat.ChatDirection
import com.urbanairship.chat.ChatRouting
import com.urbanairship.chat.websocket.DefaultWebSocketFactory
import com.urbanairship.chat.websocket.WebSocket
import com.urbanairship.chat.websocket.WebSocketFactory
import com.urbanairship.chat.websocket.WebSocketListener
import com.urbanairship.config.AirshipRuntimeConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class ChatConnection(
    private val config: AirshipRuntimeConfig,
    private val socketFactory: WebSocketFactory = DefaultWebSocketFactory(),
    private val scope: CoroutineScope = CoroutineScope(AirshipDispatchers.IO)
) {

    companion object {
        private const val HEARTBEAT_MS = 60000L
    }

    private val webSocketListener = SocketListener()
    private val lock = Any()
    private var uvp: String? = null
    private var socket: WebSocket? = null
    private var heartbeatJob: Job? = null

    internal val isOpenOrOpening: Boolean
        get() = socket != null

    internal var chatListener: ChatListener? = null

    internal fun open(uvp: String) {
        synchronized(lock) {
            if (isOpenOrOpening) {
                return
            }

            this.uvp = uvp
            socket = socketFactory.create(createUrl(uvp), webSocketListener).apply {
                open()
            }

            heartbeatJob = scope.launch {
                do {
                    send(ChatRequest.Heartbeat(uvp))
                    delay(HEARTBEAT_MS)
                } while (isActive)
            }
        }
    }

    internal fun close() {
        close(CloseReason.Manual)
    }

    private fun close(reason: CloseReason) {
        synchronized(lock) {

            if (isOpenOrOpening) {
                heartbeatJob?.cancel()
                heartbeatJob = null
                socket?.close()
                socket = null
                chatListener?.onClose(reason)
            }
        }
    }

    internal fun sendMessage(text: String?, attachment: String?, requestId: String, direction: ChatDirection, date: Long?, routing: ChatRouting?): Boolean {
        val uvp = this.uvp
        if (uvp == null) {
            Logger.error("Failed to send message. UVP is null.")
            return false
        }

        if (text == null && attachment == null) {
            Logger.error("Failed to send message. Text and attachment are both null.")
            return false
        }

        return send(ChatRequest.SendMessage(uvp, text, attachment, requestId, direction, date, routing))
    }

    internal fun fetchConversation(): Boolean {
        val uvp = this.uvp
        if (uvp == null) {
            Logger.error("Failed to send message. UVP is null.")
            return false
        }

        return send(ChatRequest.FetchConversation(uvp))
    }

    private fun createUrl(uvp: String): String {
        return config.urlConfig.chatSocketUrl()
            .appendQueryParameter("uvp", uvp)
            .build()
            .toString()
    }

    private fun send(request: ChatRequest): Boolean {
        synchronized(lock) {
            if (!isOpenOrOpening) {
                Logger.warn("Connection not open. Unable to send request: %s", request)
                return false
            }

            val payload = request.toJsonString()
            Logger.verbose("Sending: $payload")
            return socket?.send(payload) ?: false
        }
    }

    private inner class SocketListener : WebSocketListener {
        override fun onOpen() {
            Logger.verbose("Socket opened")
            chatListener?.onOpen()
        }

        override fun onClose(code: Int, reason: String) {
            Logger.verbose("Socket closed: %s %s", code, reason)
            chatListener?.onClose(CloseReason.Server(code, reason))
        }

        override fun onReceive(message: String) {
            Logger.verbose("Received: %s", message)
            ChatResponse.parse(message)?.let { response ->
                chatListener?.onChatResponse(response)
            }
        }

        override fun onError(error: Throwable) {
            Logger.debug(error, "Socket error")
            close(CloseReason.Error(error))
        }
    }

    internal interface ChatListener {
        fun onChatResponse(response: ChatResponse)
        fun onOpen()
        fun onClose(reason: CloseReason)
    }

    /** Close reasons. */
    sealed class CloseReason {
        /** A call to `close` was made. */
        object Manual : CloseReason() {
            override fun toString() = "Closed manually"
        }

        /** Server terminated the connection. */
        data class Server(val code: Int, val reason: String) : CloseReason() {
            override fun toString() = "Closed by server: ($code) $reason"
        }

        /** Closed due to an error. */
        data class Error(val error: Throwable) : CloseReason() {
            override fun toString() = "Server error"
        }
    }
}
