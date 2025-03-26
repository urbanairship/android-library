/* Copyright Airship and Contributors */

package com.urbanairship.push

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class PushNotificationStatusObserver @JvmOverloads constructor(
    initialValue: PushNotificationStatus,
    listenerDispatcher: CoroutineDispatcher = Dispatchers.Main
) {

    private val listenerScope = CoroutineScope(listenerDispatcher + SupervisorJob())

    private val _pushNotificationStatusFlow: MutableStateFlow<PushNotificationStatus> = MutableStateFlow(initialValue)
    val pushNotificationStatusFlow: StateFlow<PushNotificationStatus> = _pushNotificationStatusFlow

    val changeListeners: MutableList<PushNotificationStatusListener> = CopyOnWriteArrayList()

    // Used to skip the initial value for listeners
    private var initialStateSkipped = false

    init {
        listenerScope.launch {
            pushNotificationStatusFlow.collect { status ->
                if (initialStateSkipped || status != initialValue) {
                    changeListeners.forEach { it.onChange(status) }
                    initialStateSkipped = true
                }
            }
        }
    }

    fun update(status: PushNotificationStatus) {
        _pushNotificationStatusFlow.tryEmit(status)
    }
}
