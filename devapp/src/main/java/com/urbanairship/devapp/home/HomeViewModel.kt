/* Copyright Airship and Contributors */
package com.urbanairship.devapp.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.urbanairship.Airship
import com.urbanairship.Cancelable
import com.urbanairship.PrivacyManager
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.MessageCenter
import com.urbanairship.permission.PermissionPromptFallback
import com.urbanairship.push.pushNotificationStatusFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * View model for the HomeScreen.
 */

internal interface HomeViewModel {
    val channelId: StateFlow<String?>
    val namedUserId: StateFlow<String?>
    val isOptedInForPushes: StateFlow<Boolean>
    val unreadMessageCount: Flow<List<Message>>
    fun copyToClipboard(context: Context)
    fun togglePushStatus()
}
internal class DefaultHomeViewModel() : HomeViewModel, ViewModel() {

    private val _notificationStatus = MutableStateFlow(false)
    override val isOptedInForPushes: StateFlow<Boolean> = _notificationStatus.asStateFlow()

    init {
        Airship.shared { instance ->
            _notificationStatus.update { instance.pushManager.isOptIn }

            viewModelScope.launch {
                instance.pushManager.pushNotificationStatusFlow.collect { status ->
                    _notificationStatus.update { status.isOptIn }
                }
            }
        }
    }
    override val channelId: StateFlow<String?>
        get() = Airship.shared().channel.channelIdFlow

    override val namedUserId: StateFlow<String?>
        get() = Airship.shared().contact.namedUserIdFlow

    override val unreadMessageCount: Flow<List<Message>>
        get() = MessageCenter.shared().inbox.getUnreadMessagesFlow()

    override fun copyToClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Channel ID", channelId.value))

        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    override fun togglePushStatus() {
        val newValue = !isOptedInForPushes.value

        when(newValue) {
            true -> {
                Airship.shared().privacyManager.enable(PrivacyManager.Feature.PUSH)
                Airship.shared().pushManager.enableUserNotifications(PermissionPromptFallback.SystemSettings) {}
            }
            false -> Airship.shared().pushManager.userNotificationsEnabled = false
        }
    }

}
