package com.urbanairship.chat

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import com.urbanairship.Logger
import java.util.UUID
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class ChatConnection @VisibleForTesting constructor(
    private val client: OkHttpClient
) {

    companion object {
        private val BASE_URL = "wss://4na0gg07ee.execute-api.us-west-1.amazonaws.com/Prod".toUri()
        private const val CLOSE_CODE = 1000
        private const val CLOSE_REASON = "Bye now"
    }

    internal constructor() : this(
        client = OkHttp.sharedClient
    )

    internal interface ChatListener {
        fun onChatResponse(response: ChatResponse)
        fun onOpen()
        fun onClose(reason: CloseReason)
    }

    private val webSocketListener = SocketListener()

    private val lock = Any()

    private var uvp: String? = null
    private var socket: WebSocket? = null

    internal val isOpenOrOpening: Boolean
        get() = socket != null

    internal var chatListener: ChatListener? = null

    @Synchronized
    internal fun open(uvp: String) {
        if (isOpenOrOpening) {
            return
        }

        this.uvp = uvp

        socket = client.newWebSocket(buildRequest(uvp), webSocketListener)
    }

    internal fun close() {
        close(CloseReason.Manual)
    }

    private fun close(reason: CloseReason) {
        synchronized(lock) {
            if (isOpenOrOpening) {
                if (reason is CloseReason.Manual) {
                    socket?.close(CLOSE_CODE, CLOSE_REASON)
                }
                socket = null
                chatListener?.onClose(reason)
            }
        }
    }

    internal fun sendMessage(
        text: String,
        requestId: String = UUID.randomUUID().toString()
    ): Boolean {
        if (!isOpenOrOpening) {
            Logger.warn("Failed to send chat message. Connection not ready!")
            return false
        }

        val uvp = uvp ?: throw IllegalStateException("Failed to send message. UVP is null!")

        return send(ChatRequest.SendMessage(uvp, text, requestId))
    }

    internal fun getConversation(): Boolean {
        if (!isOpenOrOpening) {
            Logger.warn("Failed to get conversation. Connection not ready!")
            return false
        }

        val uvp = uvp ?: throw IllegalStateException("Failed to get conversation. UVP is null!")

        return send(ChatRequest.GetConversation(uvp))
    }

    private fun buildRequest(uvp: String): Request {
        val url = BASE_URL.buildUpon()
            .appendQueryParameter("uvp", uvp)
            .build()
            .toString()
        return Request.Builder().url(url).build()
    }

    private fun send(request: ChatRequest): Boolean {
        synchronized(lock) {
            val payload = request.toJsonString()
            Logger.verbose("Sending: $payload")
            return socket?.send(payload) ?: false
        }
    }

    private inner class SocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(lock) {
                if (socket == webSocket) {
                    chatListener?.onOpen()
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            synchronized(lock) {
                if (socket == webSocket) {
                    close(CloseReason.Server(code, reason))
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            synchronized(lock) {
                if (socket == webSocket) {
                    Logger.verbose("Received: '%s'", text)

                    ChatResponse.parse(text)?.let { response ->
                        chatListener?.onChatResponse(response)
                    }
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            synchronized(lock) {
                if (socket == webSocket) {
                    close(CloseReason.Error(t))
                }
            }
        }
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
