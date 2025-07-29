/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.deviceinfo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import com.urbanairship.UAirship
import com.urbanairship.push.PushProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine

internal interface DeviceInfoViewModel {

    val isPushEnabled: Flow<Boolean>
    val pushOptInt: Flow<Boolean>
    val pushToken: Flow<String?>
    val pushProvider: Flow<PushProvider.DeliveryType?>

    val namedUser: Flow<String?>
    val channelId: Flow<String?>
    val channelTags: Flow<Set<String>>
    val contactId: Flow<String?>

    fun togglePushEnabled()
    fun copyPushToken(context: Context)
    fun copyChannelId(context: Context)
    fun copyUserId(context: Context)
}

internal class DefaultDeviceInfoViewModel: DeviceInfoViewModel, ViewModel() {

    private val _pushStatus = MutableStateFlow(false)
    override val isPushEnabled: Flow<Boolean> = _pushStatus.asStateFlow()

    private val _optInStatus = MutableStateFlow(false)
    override val pushOptInt: Flow<Boolean> = _pushStatus.asStateFlow()

    private val _pushToken = MutableStateFlow<String?>(null)
    override val pushToken: Flow<String?> = _pushToken.asStateFlow()

    private val _pushProvider = MutableStateFlow<PushProvider.DeliveryType?>(null)
    override val pushProvider: Flow<PushProvider.DeliveryType?> = _pushProvider.asStateFlow()
    override val namedUser: Flow<String?>
        get() {
            return if (!UAirship.isFlying()) {
                emptyFlow()
            } else {
                UAirship.shared().contact.namedUserIdFlow
            }
        }

    override val channelTags: Flow<Set<String>>
        get() {
            return if (!UAirship.isFlying()) {
                emptyFlow()
            } else {
                flowOf(UAirship.shared().channel.tags)
            }
        }

    override val channelId: Flow<String?>
        get() {
            return if (!UAirship.isFlying()) {
                emptyFlow()
            } else {
                UAirship.shared().channel.channelIdFlow
            }
        }

    override val contactId: Flow<String?>
        get() {
            return if (!UAirship.isFlying()) {
                emptyFlow()
            } else {
                flowOf(UAirship.shared().contact.lastContactId)
            }
        }


    init {
        if (!UAirship.isFlying()) {
            UAirship.shared { refresh() }
        } else {
            refresh()
        }
    }

    override fun togglePushEnabled() {
        if (!UAirship.isFlying()) {
            _pushStatus.update { false }
            return
        }

        UAirship.shared().pushManager.userNotificationsEnabled = !_pushStatus.value
        refresh()
    }

    override fun copyPushToken(context: Context) {
        val token = _pushToken.value ?: return
        copyToClipboard(context, token, "Push Token")
    }

    override fun copyChannelId(context: Context) {
        val id = UAirship.shared().channel.id ?: return
        copyToClipboard(context, id, "Channel Id")
    }

    override fun copyUserId(context: Context) {
        val id = UAirship.shared().contact.lastContactId ?: return
        copyToClipboard(context, id, "User Id")
    }

    private fun copyToClipboard(context: Context, text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))

        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT)
    }

    private fun refresh() {
        if (!UAirship.isFlying()) {
            return
        }

        _pushStatus.update { UAirship.shared().pushManager.userNotificationsEnabled }
        _optInStatus.update { UAirship.shared().pushManager.isOptIn }
        _pushToken.update { UAirship.shared().pushManager.pushToken }
        _pushProvider.update { UAirship.shared().pushManager.pushProvider?.deliveryType }
    }
}
