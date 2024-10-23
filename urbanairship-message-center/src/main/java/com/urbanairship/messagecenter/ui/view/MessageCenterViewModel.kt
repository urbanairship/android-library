/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter.ui.view

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.urbanairship.messagecenter.Inbox
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.MessageCenter

internal class MessageCenterViewModel(
    private val inbox: Inbox = MessageCenter.shared().inbox,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    internal suspend fun getOrFetchMessage(messageId: String): Message? {
        // Try to load the message from local storage
        val message = inbox.getMessage(messageId) ?: run {
            // If we don't have the message, refresh the inbox
            if (!inbox.fetchMessages()) {
                // Fetch failed, return an error
                return null
            }

            // Try to get the message again, now that we've refreshed
            inbox.getMessage(messageId) ?: return null
        }

        if (message.isExpired) {
            // Message is expired, return an error
            return null
        }

        // Message is available, return it
        return message
    }

    companion object {
        /** Factory for creating [MessageCenterView]. */
        @JvmStatic
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MessageCenterViewModel(
                    inbox = MessageCenter.shared().inbox,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
