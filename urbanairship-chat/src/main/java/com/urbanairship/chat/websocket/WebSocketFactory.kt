/* Copyright Airship and Contributors */

package com.urbanairship.chat.websocket

/**
 * Web socket factory.
 */
internal interface WebSocketFactory {

    /**
     * Creates a web socket for the given URL and listener.
     * @param url The socket url.
     * @param listener The socket listener.
     * @return A web socket.
     */
    fun create(url: String, listener: WebSocketListener): WebSocket
}
