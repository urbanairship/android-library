/* Copyright Airship and Contributors */

package com.urbanairship.chat.websocket

/**
 * Web socket listener.
 */
internal interface WebSocketListener {

    /**
     * Called when the socket is opened.
     */
    fun onOpen()

    /**
     * Called when the socket is closed by the server.
     * @param code The code that closed the socket.
     * @param reason The reason the socket closed.
     */
    fun onClose(code: Int, reason: String)

    /**
     * Called when the socket received a message.
     * @param message The message.
     */
    fun onReceive(message: String)

    /**
     * Called when the socket received an error.
     * @param error The error
     */
    fun onError(error: Throwable)
}
