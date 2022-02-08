package com.urbanairship.preferencecenter

import com.urbanairship.UAirship
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.channel.AirshipChannelListener
import com.urbanairship.preferencecenter.data.Condition
import com.urbanairship.push.PushManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onSubscription

internal class ConditionStateMonitor(
    private val channel: AirshipChannel = UAirship.shared().channel,
    private val pushManager: PushManager = UAirship.shared().pushManager
) {
    private val stateFlow = MutableStateFlow(currentState)

    val states = stateFlow.asStateFlow()
        .onSubscription {
            channel.addChannelListener(channelListener)
            checkState()
        }
        .onCompletion {
            channel.removeChannelListener(channelListener)
        }

    val currentState
        get() = Condition.State(isOptedIn = isOptedIn)

    private val isOptedIn: Boolean
        get() = pushManager.isOptIn

    private fun checkState() {
        stateFlow.getAndUpdate { state ->
            state.copy(isOptedIn = isOptedIn)
        }
    }

    private val channelListener = object : AirshipChannelListener {
        override fun onChannelUpdated(channelId: String) = checkState()
        override fun onChannelCreated(channelId: String) = checkState()
    }
}
