package com.urbanairship.chat

import androidx.annotation.WorkerThread

/**
 * Conversation listener.
 */
interface ConversationListener {

    /**
     * Called when messages in a conversation have been updated.
     */
    @WorkerThread
    fun onConversationUpdated()
}
