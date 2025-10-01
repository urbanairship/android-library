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
import androidx.lifecycle.ViewModel
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
import kotlinx.coroutines.flow.map

/**
 * View model for the HomeScreen.
 */
internal open class HomeViewModel() : ViewModel() {

    open var channelId = Airship.shared().channel.channelIdFlow
    open var unreadMessageCount = MessageCenter.shared().inbox.getUnreadMessagesFlow()

    internal open fun copyToClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Channel ID", channelId.value))

        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
