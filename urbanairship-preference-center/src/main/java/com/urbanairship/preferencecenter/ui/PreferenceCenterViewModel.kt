package com.urbanairship.preferencecenter.ui

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.urbanairship.Logger
import com.urbanairship.UAirship
import com.urbanairship.actions.ActionRunRequestFactory
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.contacts.Contact
import com.urbanairship.contacts.Scope
import com.urbanairship.json.JsonValue
import com.urbanairship.preferencecenter.ConditionStateMonitor
import com.urbanairship.preferencecenter.PreferenceCenter
import com.urbanairship.preferencecenter.data.Condition
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.data.PreferenceCenterConfig
import com.urbanairship.preferencecenter.data.Section
import com.urbanairship.preferencecenter.data.evaluate
import com.urbanairship.preferencecenter.testing.OpenForTesting
import com.urbanairship.preferencecenter.util.execute
import com.urbanairship.preferencecenter.util.scanConcat
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@OpenForTesting
internal class PreferenceCenterViewModel @JvmOverloads constructor(
    private val preferenceCenterId: String,
    private val preferenceCenter: PreferenceCenter = PreferenceCenter.shared(),
    private val channel: AirshipChannel = UAirship.shared().channel,
    private val contact: Contact = UAirship.shared().contact,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val actionRunRequestFactory: ActionRunRequestFactory = ActionRunRequestFactory(),
    private val conditionMonitor: ConditionStateMonitor = ConditionStateMonitor()
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

        // Collect updates from the condition monitor and repost them on the actions flow.
        conditionMonitor.states
            .map { Action.ConditionStateChanged(state = it) }
            .onEach { actions.emit(it) }
            .launchIn(viewModelScope)
    }

    fun handle(action: Action) {
        viewModelScope.launch { actions.emit(action) }
    }

    private fun map(action: Action): Flow<Change> =
        when (action) {
            is Action.Refresh ->
                refresh()
            is Action.PreferenceItemChanged ->
                updatePreference(
                    item = action.item,
                    isEnabled = action.isEnabled
                )
            is Action.ScopedPreferenceItemChanged ->
                updatePreference(
                    item = action.item,
                    scopes = action.scopes,
                    isEnabled = action.isEnabled
                )
            is Action.ButtonActions -> with(action) {
                actions.execute(requestFactory = actionRunRequestFactory)
                emptyFlow()
            }
            is Action.ConditionStateChanged ->
                flowOf(Change.UpdateConditionState(action.state))
        }

    private fun reduce(state: State, change: Change): Flow<State> =
        when (change) {
            is Change.ShowContent -> change.state
            is Change.UpdateSubscriptions -> when (state) {
                is State.Content -> {
                    val updatedSubscriptions = if (change.isSubscribed) {
                        state.channelSubscriptions + change.subscriptionId
                    } else {
                        state.channelSubscriptions - change.subscriptionId
                    }
                    state.copy(channelSubscriptions = updatedSubscriptions)
                }
                else -> state
            }
            is Change.UpdateScopedSubscriptions -> when (state) {
                is State.Content -> {
                    val currentScopes = state.contactSubscriptions[change.subscriptionId] ?: emptySet()
                    val updatedScopes = if (change.isSubscribed) {
                         currentScopes + change.scopes
                    } else {
                         currentScopes - change.scopes
                    }
                    val updatedSubscriptions = state.contactSubscriptions.toMutableMap().apply {
                        set(change.subscriptionId, updatedScopes)
                    }
                    state.copy(contactSubscriptions = updatedSubscriptions)
                }
                else -> state
            }
            is Change.UpdateConditionState -> when (state) {
                is State.Content -> {
                    val conditions = change.state
                    state.copy(
                        listItems = state.config.filterByConditions(conditions).asPrefCenterItems(),
                        conditionState = conditions
                    )
                }
                else -> state
            }
            is Change.ShowError -> State.Error(error = change.error)
            is Change.ShowLoading -> when (state) {
                is State.Content -> state
                else -> State.Loading
            }
        }.let { flowOf(it) }

    @OptIn(FlowPreview::class)
    private fun refresh(): Flow<Change> = flow {
        emit(Change.ShowLoading)

        val configFlow = flow { emit(getConfig(preferenceCenterId)) }
        val channelSubscriptionsFlow = flow { emit(getChannelSubscriptions()) }
        val contactSubscriptionsFlow = flow { emit(getContactSubscriptions()) }

        emitAll(
            // Fetch config first to determine which subscriptions are needed and flat map them into the flow.
            configFlow.flatMapConcat { config ->

                val mergeChannelDataToContact = config.options?.mergeChannelDataToContact ?: false
                val fetchChannelSubscriptions = config.hasChannelSubscriptions || mergeChannelDataToContact
                val fetchContactSubscriptions = config.hasContactSubscriptions

                when {
                    fetchChannelSubscriptions && fetchContactSubscriptions ->
                        channelSubscriptionsFlow.zip(contactSubscriptionsFlow) { channelSubs, contactSubs ->
                            if (mergeChannelDataToContact) {
                                Triple(config, channelSubs, mergeSubscriptions(channelSubs, contactSubs))
                            } else {
                                Triple(config, channelSubs, contactSubs)
                            }
                        }
                    fetchChannelSubscriptions ->
                        channelSubscriptionsFlow.map { channelSubs ->
                            Triple(config, channelSubs, emptyMap())
                        }
                    fetchContactSubscriptions ->
                        contactSubscriptionsFlow.map { contactSubs ->
                            Triple(config, emptySet(), contactSubs)
                        }
                    else -> // We shouldn't ever get here, unless backend somehow serves a pref center with no items.
                        flowOf(Triple(config, emptySet(), emptyMap()))
                }
            }.map { (config, channelSubscriptions, contactSubscriptions) ->
                val conditionState = conditionMonitor.currentState
                val filteredItems = config.filterByConditions(conditionState).asPrefCenterItems()
                val display = config.display
                Change.ShowContent(
                    State.Content(
                        config = config,
                        listItems = filteredItems,
                        title = display.name,
                        subtitle = display.description,
                        channelSubscriptions = channelSubscriptions,
                        contactSubscriptions = contactSubscriptions,
                        conditionState = conditionState
                    )
                )
            }.catch<Change> { error ->
                Logger.error(error, "Failed to fetch preference center data!")
                emit(Change.ShowError(error = error))
            }.flowOn(ioDispatcher)
        )
    }

    private fun mergeSubscriptions(channelSubscriptions: Set<String>, contactSubscriptions: Map<String, Set<Scope>>): Map<String, Set<Scope>> {
        val map = contactSubscriptions.toMutableMap()
        channelSubscriptions.forEach {
            map[it] = map[it]?.toMutableSet()?.apply {
                add(Scope.APP)
            } ?: setOf(Scope.APP)
        }
        return map.toMap()
    }

    private fun updatePreference(
        item: Item,
        scopes: Set<Scope> = emptySet(),
        isEnabled: Boolean
    ): Flow<Change> = flow {
        Logger.verbose("Updating preference item: " +
            "id = ${item.id}, title = ${item.display.name}, scopes = $scopes, state = $isEnabled")

        when (item) {
            is Item.ChannelSubscription -> with(item) {
                channel.editSubscriptionLists()
                    .mutate(subscriptionId, isEnabled)
                    .apply()

                emit(Change.UpdateSubscriptions(subscriptionId, isEnabled))
            }
            is Item.ContactSubscription -> with(item) {
                contact.editSubscriptionLists()
                    .mutate(subscriptionId, scopes, isEnabled)
                    .apply()

                emit(Change.UpdateScopedSubscriptions(subscriptionId, scopes, isEnabled))
            }
            is Item.ContactSubscriptionGroup -> with(item) {
                contact.editSubscriptionLists()
                    .mutate(subscriptionId, scopes, isEnabled)
                    .apply()

                emit(Change.UpdateScopedSubscriptions(subscriptionId, scopes, isEnabled))
            }
            is Item.Alert -> {} // No-op.
        }
    }

    internal sealed class State {
        object Loading : State()
        data class Error(val message: String? = null, val error: Throwable? = null) : State()
        data class Content(
            val config: PreferenceCenterConfig,
            val listItems: List<PrefCenterItem>,
            val title: String?,
            val subtitle: String?,
            val channelSubscriptions: Set<String>,
            val contactSubscriptions: Map<String, Set<Scope>>,
            val conditionState: Condition.State
        ) : State()
    }

    internal sealed class Action {
        object Refresh : Action()
        data class PreferenceItemChanged(val item: Item, val isEnabled: Boolean) : Action()
        data class ScopedPreferenceItemChanged(val item: Item, val scopes: Set<Scope>, val isEnabled: Boolean) : Action()
        data class ButtonActions(val actions: Map<String, JsonValue>) : Action()
        data class ConditionStateChanged(val state: Condition.State) : Action()
    }

    internal sealed class Change {
        object ShowLoading : Change()
        data class ShowError(val message: String? = null, val error: Throwable? = null) : Change()
        data class ShowContent(val state: State.Content) : Change()
        data class UpdateSubscriptions(val subscriptionId: String, val isSubscribed: Boolean) : Change()
        data class UpdateScopedSubscriptions(val subscriptionId: String, val scopes: Set<Scope>, val isSubscribed: Boolean) : Change()
        data class UpdateConditionState(val state: Condition.State) : Change()
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

    private suspend fun getChannelSubscriptions(): Set<String> =
        suspendCancellableCoroutine { continuation ->
            channel.getSubscriptionLists(/* includePendingUpdates = */ true).addResultCallback { subscriptions ->
                if (subscriptions != null) {
                    continuation.resume(subscriptions)
                } else {
                    continuation.resumeWithException(IllegalStateException("Null subscription listing for channel id: ${channel.id}"))
                }
            }
        }

    private suspend fun getContactSubscriptions(): Map<String, Set<Scope>> =
        suspendCancellableCoroutine { continuation ->
            contact.getSubscriptionLists(/* includePendingUpdates = */ true).addResultCallback { subscriptions ->
                if (subscriptions != null) {
                    continuation.resume(subscriptions)
                } else {
                    continuation.resumeWithException(IllegalStateException("Null subscription listing for contact id: ${contact.namedUserId}"))
                }
            }
        }
}

/**
 * Helper extension that returns a subset of pref center items based on the given condition [state].
 */
@VisibleForTesting
internal fun PreferenceCenterConfig.filterByConditions(
    state: Condition.State
): PreferenceCenterConfig {
    return this.copy(
        sections = sections.filter { section ->
            section.conditions.evaluate(state)
        }.map { section ->
            section.filterItems { item ->
                item.conditions.evaluate(state)
            }
        }
    )
}

/**
 * Helper extension that builds a list of `PrefCenterItem` objects from a `PreferenceCenterConfig`.
 *
 * @hide
 */
@VisibleForTesting
internal fun PreferenceCenterConfig.asPrefCenterItems(): List<PrefCenterItem> =
    sections.flatMap { section ->
        when (section) {
            is Section.SectionBreak -> listOf(PrefCenterItem.SectionBreakItem(section))
            is Section.Common -> {
                if (section.display.isEmpty()) {
                    // Ignore sections with no title and subtitle to avoid unwanted whitespace in
                    // the list if a section has no title/description and is being used as a
                    // container for an alert.
                    emptyList()
                } else {
                    listOf(PrefCenterItem.SectionItem(section))
                } + section.items.map { item ->
                    when (item) {
                        is Item.ChannelSubscription ->
                            PrefCenterItem.ChannelSubscriptionItem(item)
                        is Item.ContactSubscription ->
                            PrefCenterItem.ContactSubscriptionItem(item)
                        is Item.ContactSubscriptionGroup ->
                            PrefCenterItem.ContactSubscriptionGroupItem(item)
                        is Item.Alert ->
                            PrefCenterItem.AlertItem(item)
                    }
                }
            }
        }
    }
