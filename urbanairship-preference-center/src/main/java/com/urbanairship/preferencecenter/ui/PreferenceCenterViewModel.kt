package com.urbanairship.preferencecenter.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.urbanairship.Logger
import com.urbanairship.preferencecenter.PreferenceCenter
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.data.PreferenceCenterConfig
import com.urbanairship.preferencecenter.testing.OpenForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@OpenForTesting
internal class PreferenceCenterViewModel @JvmOverloads constructor(
    application: Application,
    preferenceCenterId: String,
    preferenceCenter: PreferenceCenter = PreferenceCenter.shared(),
) : AndroidViewModel(application) {

    class PreferenceCenterViewModelFactory(
        private val application: Application,
        private val preferenceCenterId: String
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PreferenceCenterViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PreferenceCenterViewModel(application, preferenceCenterId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.canonicalName}")
        }
    }

    private val stateFlow: MutableStateFlow<State> = MutableStateFlow(State.Loading)
    internal val states: StateFlow<State> = stateFlow.asStateFlow()

    init {
        preferenceCenter.getConfig(preferenceCenterId)
            .addResultCallback { config ->
                if (config != null) {
                    onConfigLoaded(config)
                } else {
                    Logger.error("Failed to load PreferenceCenter config!")
                    stateFlow.value = State.Error()
                }
            }
    }

    fun onPreferenceItemChanged(item: Item, isEnabled: Boolean) {
        when (item) {
            is Item.ChannelSubscription -> updateSubscription(item.id, isEnabled)
        }
    }

    private fun onConfigLoaded(config: PreferenceCenterConfig) {
        stateFlow.value = State.Content(title = config.display.title, list = config.asPrefCenterItems())
    }

    private fun updateSubscription(id: String, isEnabled: Boolean) {
        // TODO: make this work
        if (isEnabled) {
            Logger.verbose("Subscribing to channel '$id'")
        } else {
            Logger.verbose("Unsubscribing from channel '$id'")
        }
    }

    internal sealed class State {
        object Loading : State()
        data class Error(val message: String? = null) : State()
        data class Content(val title: String?, val list: List<PrefCenterItem>) : State()
    }
}

internal fun PreferenceCenterConfig.asPrefCenterItems(): List<PrefCenterItem> =
    sections.flatMap { section ->
        listOf(
            SectionItem(
                id = section.id,
                title = section.display.title,
                subtitle = section.display.subtitle
            )
        ) + section.items.map { item ->
            when (item) {
                is Item.ChannelSubscription -> ChannelSubscriptionItem(
                    id = item.id,
                    subscriptionId = item.subscriptionId,
                    title = item.display.title,
                    subtitle = item.display.subtitle
                )
            }
        }
    }
