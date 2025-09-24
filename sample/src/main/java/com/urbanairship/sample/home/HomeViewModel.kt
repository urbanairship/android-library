/* Copyright Airship and Contributors */
package com.urbanairship.sample.home

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import com.urbanairship.Airship
import com.urbanairship.PendingResult
import com.urbanairship.ResultCallback
import com.urbanairship.channel.AirshipChannelListener
import com.urbanairship.messagecenter.InboxListener
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.MessageCenter
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * View model for the HomeScreen.
 */
internal class HomeViewModel(application: Application) : AndroidViewModel(application) {

    val channelId = MutableStateFlow<String?>(null)
    private val _unreadMessageCount = MutableStateFlow(0)
    val unreadMessageCount: StateFlow<Int> = _unreadMessageCount.asStateFlow()
    private val inboxListener = object : InboxListener {
        override fun onInboxUpdated() {
            getUnreadMessages()
        }
    }
    private var messageCenterLastSentDate: Long = 0
    private var messageCenterSnackbar: Snackbar? = null


    private val channelListener: AirshipChannelListener = object : AirshipChannelListener {
        override fun onChannelCreated(channelId: String) {
            Handler(Looper.getMainLooper()).post { refreshChannel() }
        }
    }

    init {
        Airship.shared().channel.addChannelListener(channelListener)
        MessageCenter.shared().inbox.addListener(inboxListener)
        refreshChannel()
    }

    private fun refreshChannel() {
        channelId.value = Airship.shared().channel.id
    }

    override fun onCleared() {
        super.onCleared()
        Airship.shared().channel.removeChannelListener(channelListener)
        MessageCenter.shared().inbox.removeListener(inboxListener)
    }

    internal fun copyToClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Channel ID", channelId.value))

        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    /**
     * Shows a Message Center indicator.
     */
     private fun getUnreadMessages() {
         val pendingResult: PendingResult<List<Message>?> = MessageCenter.shared().inbox.getUnreadMessagesPendingResult()

         pendingResult.addResultCallback(ResultCallback { messages: List<Message?>? ->
             // Skip showing the indicator if we have no unread messages or no new messages since the last display
             if (messages == null || messages.isEmpty() || messageCenterLastSentDate >= messages[0]!!.sentDate.getTime()) {
                 return@ResultCallback
             }

             // Track the message sent date to track if we have a new message
             messageCenterLastSentDate = messages.get(0)!!.sentDate.getTime()

             // Skip showing the indicator if its already displaying
             if (messageCenterSnackbar != null && messageCenterSnackbar!!.isShownOrQueued()) {
                 return@ResultCallback
             }

             _unreadMessageCount.value = messages.size

         })
     }
}
