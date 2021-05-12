/* Copyright Airship and Contributors */

package com.urbanairship.chat.websocket

/**
 * Web socket.
 */
internal interface WebSocket {

    /**
     * Opens the socket.
     */
    fun open()

    /**
     * Closes the socket.
     */
    fun close()

    /**
     * Sends a message to the socket.
     * @param message The message.
     * @return true if the message sent, otherwise false.
     */
    fun send(message: String): Boolean

    /**
     *  Web socket listener.
     */
    val listener: WebSocketListener
}
