/* Copyright Airship and Contributors */

package com.urbanairship.chat.websocket

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal class DefaultWebSocketFactory : WebSocketFactory {
    override fun create(url: String, listener: WebSocketListener): WebSocket {
        return DefaultWebSocket(url, listener)
    }
}

internal class DefaultWebSocket(private val url: String, override val listener: WebSocketListener) : WebSocket {
    companion object {
        private const val CLOSE_CODE = 1000
    }

    private val client: OkHttpClient = OkHttp.sharedClient
    private val webSocketListener = SocketListener()
    private val lock = Any()
    private var socket: okhttp3.WebSocket? = null

    override fun open() {
        synchronized(lock) {
            if (socket != null) {
                return
            }
            val request = Request.Builder().url(url).build()
            socket = client.newWebSocket(request, webSocketListener)
        }
    }

    override fun close() {
        synchronized(lock) {
            if (socket != null) {
                socket?.close(CLOSE_CODE, null)
                socket = null
            }
        }
    }
    override fun send(message: String): Boolean {
        synchronized(lock) {
            return socket?.send(message) ?: false
        }
    }

    private inner class SocketListener : okhttp3.WebSocketListener() {

        override fun onOpen(webSocket: okhttp3.WebSocket, response: Response) {
            synchronized(lock) {
                if (socket == webSocket) {
                    listener.onOpen()
                }
            }
        }

        override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
            synchronized(lock) {
                if (socket == webSocket) {
                    socket = null
                    listener.onClose(code, reason)
                }
            }
        }

        override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
            synchronized(lock) {
                if (socket == webSocket) {
                    listener.onReceive(text)
                }
            }
        }

        override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: Response?) {
            synchronized(lock) {
                if (socket == webSocket) {
                    listener.onError(t)
                }
            }
        }
    }
}
