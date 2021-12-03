package com.urbanairship.preferencecenter.ui

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.urbanairship.Logger
import com.urbanairship.UAirship
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.preferencecenter.PreferenceCenter
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.data.PreferenceCenterConfig
import com.urbanairship.preferencecenter.testing.OpenForTesting
import com.urbanairship.preferencecenter.util.scanConcat
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
@OpenForTesting
internal class PreferenceCenterViewModel @JvmOverloads constructor(
    private val preferenceCenterId: String,
    private val preferenceCenter: PreferenceCenter = PreferenceCenter.shared(),
    private val channel: AirshipChannel = UAirship.shared().channel,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    class PreferenceCenterViewModelFactory(
        private val preferenceCenterId: String
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PreferenceCenterViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PreferenceCenterViewModel(preferenceCenterId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.canonicalName}")
        }
    }

    private val stateFlow: MutableStateFlow<State> = MutableStateFlow(State.Loading)
    private val actions: MutableSharedFlow<Action> = MutableSharedFlow()

    val states: StateFlow<State> = stateFlow.asStateFlow()

    init {
        viewModelScope.launch {
            actions.collect { action ->
                Logger.verbose("< $action")

                launch {
                    map(action)
                        .scanConcat(states.value, ::reduce)
                        .collect { state ->
                            stateFlow.value = state
                        }
                }
            }
        }

        viewModelScope.launch {
            states.collect { state -> Logger.verbose("> $state") }
        }
    }

    fun handle(action: Action) {
        viewModelScope.launch { actions.emit(action) }
    }

    private fun map(action: Action): Flow<Change> =
        when (action) {
            is Action.Refresh -> refresh()
            is Action.PreferenceItemChanged -> updatePreference(action.item, action.isEnabled)
        }

    private fun reduce(state: State, change: Change): Flow<State> =
        when (change) {
            is Change.ShowContent -> change.state
            is Change.UpdateSubscriptions -> when (state) {
                is State.Content -> {
                    val updatedSubscriptions = if (change.isSubscribed) {
                        state.subscriptions + change.subscriptionId
                    } else {
                        state.subscriptions - change.subscriptionId
                    }
                    state.copy(subscriptions = updatedSubscriptions)
                }
                else -> state
            }
            is Change.ShowError -> State.Error(error = change.error)
            is Change.ShowLoading -> State.Loading
        }.let { flowOf(it) }

    private fun refresh(): Flow<Change> = flow {
        emit(Change.ShowLoading)

        val configFlow = flow { emit(getConfig(preferenceCenterId)) }
        val subscriptionsFlow = flow { emit(getSubscriptions()) }

        emitAll(
            configFlow.zip(subscriptionsFlow) { config, subscriptions ->
                Change.ShowContent(
                    State.Content(
                        title = config.display.name,
                        subtitle = config.display.description,
                        listItems = config.asPrefCenterItems(),
                        subscriptions = subscriptions
                    )
                )
            }.catch<Change> { error ->
                Logger.error(error, "Failed to fetch preference center data!")
                emit(Change.ShowError(error = error))
            }.flowOn(ioDispatcher)
        )
    }

    fun updatePreference(item: Item, isEnabled: Boolean): Flow<Change> = flow {
        Logger.verbose("Updating preference item: id = ${item.id}, title = ${item.display.name}, state = $isEnabled")

        when (item) {
            is Item.ChannelSubscription -> with(item) {
                channel.editSubscriptionLists()
                    .mutate(subscriptionId, isEnabled)
                    .apply()

                emit(Change.UpdateSubscriptions(subscriptionId, isEnabled))
            }
        }
    }

    internal sealed class State {
        object Loading : State()
        data class Error(val message: String? = null, val error: Throwable? = null) : State()
        data class Content(
            val title: String?,
            val subtitle: String?,
            val listItems: List<PrefCenterItem>,
            val subscriptions: Set<String>
        ) : State()
    }

    internal sealed class Action {
        object Refresh : Action()
        data class PreferenceItemChanged(val item: Item, val isEnabled: Boolean) : Action()
    }

    internal sealed class Change {
        object ShowLoading : Change()
        data class ShowError(val message: String? = null, val error: Throwable? = null) : Change()
        data class ShowContent(val state: State.Content) : Change()
        data class UpdateSubscriptions(val subscriptionId: String, val isSubscribed: Boolean) : Change()
    }

    private suspend fun getConfig(preferenceCenterId: String): PreferenceCenterConfig =
        suspendCancellableCoroutine { continuation ->
            preferenceCenter.getConfig(preferenceCenterId).addResultCallback { config ->
                if (config != null) {
                    continuation.resume(config)
                } else {
                    continuation.resumeWithException(IllegalStateException("Null preference center for id: $preferenceCenterId"))
                }
            }
        }

    private suspend fun getSubscriptions(): Set<String> =
        suspendCancellableCoroutine { continuation ->
            channel.getSubscriptionLists(/* includePendingUpdates = */ true).addResultCallback { subscriptions ->
                if (subscriptions != null) {
                    continuation.resume(subscriptions)
                } else {
                    continuation.resumeWithException(IllegalStateException("Null subscription listing for channel id: ${channel.id}"))
                }
            }
        }
}

/**
 * Helper extension that builds a list of `PrefCenterItem` objects from a `PreferenceCenterConfig`.
 *
 * @hide
 */
@VisibleForTesting
internal fun PreferenceCenterConfig.asPrefCenterItems(): List<PrefCenterItem> =
    sections.flatMap { section ->
        listOf(PrefCenterItem.SectionItem(section)) + section.items.map { item ->
            when (item) {
                is Item.ChannelSubscription -> PrefCenterItem.ChannelSubscriptionItem(item)
            }
        }
    }
