package com.urbanairship.preferencecenter.ui

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.actions.ActionRunner
import com.urbanairship.actions.DefaultActionRunner
import com.urbanairship.actions.run
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.contacts.Contact
import com.urbanairship.contacts.ContactChannel
import com.urbanairship.contacts.EmailRegistrationOptions
import com.urbanairship.contacts.Scope
import com.urbanairship.contacts.SmsRegistrationOptions
import com.urbanairship.json.JsonValue
import com.urbanairship.preferencecenter.ConditionStateMonitor
import com.urbanairship.preferencecenter.PreferenceCenter
import com.urbanairship.preferencecenter.data.Condition
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.data.Item.ContactManagement.RegistrationOptions
import com.urbanairship.preferencecenter.data.PreferenceCenterConfig
import com.urbanairship.preferencecenter.data.Section
import com.urbanairship.preferencecenter.data.evaluate
import com.urbanairship.preferencecenter.ui.item.AlertItem
import com.urbanairship.preferencecenter.ui.item.ChannelSubscriptionItem
import com.urbanairship.preferencecenter.ui.item.ContactManagementItem
import com.urbanairship.preferencecenter.ui.item.ContactSubscriptionGroupItem
import com.urbanairship.preferencecenter.ui.item.ContactSubscriptionItem
import com.urbanairship.preferencecenter.ui.item.PrefCenterItem
import com.urbanairship.preferencecenter.ui.item.SectionBreakItem
import com.urbanairship.preferencecenter.ui.item.SectionItem
import com.urbanairship.preferencecenter.util.scanConcat
import com.urbanairship.preferencecenter.widget.ContactChannelDialogInputView
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@OpenForTesting
internal class PreferenceCenterViewModel @JvmOverloads constructor(
    private val preferenceCenterId: String,
    private val preferenceCenter: PreferenceCenter = PreferenceCenter.shared(),
    private val channel: AirshipChannel = UAirship.shared().channel,
    private val contact: Contact = UAirship.shared().contact,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val actionRunner: ActionRunner = DefaultActionRunner,
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
    private val effectsChannel = Channel<Effect>()

    val states: StateFlow<State> = stateFlow.asStateFlow()
    val effects: Flow<Effect> = effectsChannel.receiveAsFlow()
        .onEach { UALog.v("! $it")}

    init {
        viewModelScope.launch {
            actions.collect { action ->
                UALog.v("< $action")

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
            contact.namedUserIdFlow
                .drop(1)
                .collect {
                    actions.emit(Action.Refresh)
                }
        }

        viewModelScope.launch {
            states.collect { state -> UALog.v("> $state") }
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

    private suspend fun map(action: Action): Flow<Change> =
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
                actionRunner.run(actions)
                emptyFlow()
            }
            is Action.ConditionStateChanged ->
                flowOf(Change.UpdateConditionState(action.state))
            is Action.AddChannel -> {
                effectsChannel.send(
                    Effect.ShowContactManagementAddDialog(action.item)
                )
                emptyFlow()
            }
            is Action.ConfirmAddChannel -> {
                effectsChannel.send(
                    Effect.ShowContactManagementAddConfirmDialog(action.item, action.result)
                )
                emptyFlow()
            }
            is Action.RemoveChannel -> {
                effectsChannel.send(
                    Effect.ShowContactManagementRemoveDialog(action.item, action.channel)
                )
                emptyFlow()
            }
            is Action.RegisterChannel -> {
                when (action) {
                    is Action.RegisterChannel.Email -> {
                        contact.registerEmail(
                            action.address,
                            EmailRegistrationOptions.options(doubleOptIn = true)
                        )
                    }
                    is Action.RegisterChannel.Sms -> {
                        contact.registerSms(
                            action.address,
                            SmsRegistrationOptions.options(action.senderId)
                        )
                    }
                }

                emptyFlow()
            }
            is Action.UnregisterChannel -> {
                contact.disassociateChannel(action.channel)
                emptyFlow()
            }
        }

    private suspend fun reduce(state: State, change: Change): Flow<State> =
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
            is Change.ShowLoading -> State.Loading

            is Change.AddContactChannel -> when (state) {
                is State.Content -> {
                    state.copy(contactChannels = state.contactChannels + change.channel)
                }
                else -> state
            }

            is Change.RemoveContactChannel -> when (state) {
                is State.Content -> {
                    state.copy(contactChannels = state.contactChannels - change.channel)
                }
                else -> state
            }

            // TODO: wire up API calls and update state, as needed!
            is Change.AssociateContactChannel -> state
            is Change.DisassociateContactChannel -> state
        }.let { flowOf(it) }

    private data class EnrichedConfig(
        val config: PreferenceCenterConfig,
        val channelSubscriptions: Set<String> = emptySet(),
        val contactSubscriptions: Map<String, Set<Scope>> = emptyMap(),
        val contactChannels: Set<ContactChannel> = emptySet()
    )

    private fun refresh(): Flow<Change> = flow {
        emit(Change.ShowLoading)

        emitAll(
            // Fetch config first to determine which subscriptions are needed and flat map them into the flow.
            enrichedConfig()
            .map { (config, channelSubscriptions, contactSubscriptions, contactChannels) ->
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
                        contactChannels = contactChannels,
                        conditionState = conditionState
                    )
                )
            }.catch<Change> { error ->
                UALog.e(error, "Failed to fetch preference center data!")
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
        UALog.v("Updating preference item: " +
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
            else -> Unit // No-op.
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun enrichedConfig(): Flow<EnrichedConfig> =
        // Fetch config first to determine which subscriptions are needed and flat map them into the flow.
        getConfig(preferenceCenterId)
            .map(::EnrichedConfig)
            .flatMapConcat { enrichedConfig ->
                val config = enrichedConfig.config
                val mergeChannelDataToContact = config.options?.mergeChannelDataToContact ?: false
                val fetchChannelSubscriptions = config.hasChannelSubscriptions || mergeChannelDataToContact
                val fetchContactSubscriptions = config.hasContactSubscriptions
                val fetchContactChannels = config.hasContactManagement

                combine(
                    if (fetchChannelSubscriptions) getChannelSubscriptions() else flowOf(emptySet()),
                    if (fetchContactSubscriptions) getContactSubscriptions() else flowOf(emptyMap()),
                    if (fetchContactChannels) getAssociatedChannels() else flowOf(emptySet())
                ) { channelSubs, contactSubs, contactChannels ->
                    enrichedConfig.copy(
                        channelSubscriptions = channelSubs,
                        contactSubscriptions = if (mergeChannelDataToContact) mergeSubscriptions(channelSubs, contactSubs) else contactSubs,
                        contactChannels = contactChannels
                    )
                }
            }

    private fun getConfig(preferenceCenterId: String): Flow<PreferenceCenterConfig> = flow {
        emit(preferenceCenter.getConfig(preferenceCenterId) ?: throw IllegalStateException("Null preference center for id: $preferenceCenterId"))
    }

    private fun getChannelSubscriptions(): Flow<Set<String>> = flow {
        emit(channel.fetchSubscriptionLists().getOrThrow())
    }

    private fun getContactSubscriptions(): Flow<Map<String, Set<Scope>>> = flow {
        emit(contact.fetchSubscriptionLists().getOrThrow())
    }

    private fun getAssociatedChannels(): Flow<Set<ContactChannel>> = contact.channelContacts.mapNotNull {
        it.getOrThrow().toSet()
    }

    internal sealed class State {
        data object Loading : State()
        data class Error(val message: String? = null, val error: Throwable? = null) : State()
        data class Content(
            val config: PreferenceCenterConfig,
            val listItems: List<PrefCenterItem>,
            val title: String?,
            val subtitle: String?,
            val channelSubscriptions: Set<String>,
            val contactSubscriptions: Map<String, Set<Scope>>,
            val contactChannels: Set<ContactChannel>,
            val conditionState: Condition.State,
        ) : State() {

            override fun toString(): String {
                return "Content(title=$title, subtitle=$subtitle, channelSubscriptions=$channelSubscriptions, contactSubscriptions=$contactSubscriptions, contactChannels=$contactChannels, conditionState=$conditionState)"
            }
        }
    }

    internal sealed class Action { data object Refresh : Action()
        data class PreferenceItemChanged(val item: Item, val isEnabled: Boolean) : Action()
        data class ScopedPreferenceItemChanged(
            val item: Item,
            val scopes: Set<Scope>,
            val isEnabled: Boolean
        ) : Action()

        data class ButtonActions(val actions: Map<String, JsonValue>) : Action()
        data class ConditionStateChanged(val state: Condition.State) : Action()

        // Contact Management
        data class AddChannel(val item: Item.ContactManagement) : Action()
        data class RemoveChannel(val item: Item.ContactManagement, val channel: ContactChannel) : Action()

        data class ConfirmAddChannel(
            val item: Item.ContactManagement,
            val result: ContactChannelDialogInputView.DialogResult
        ): Action()

        sealed class RegisterChannel : Action() {
            abstract val address: String

            data class Email(override val address: String) : RegisterChannel()

            data class Sms(override val address: String, val senderId: String) : RegisterChannel()
        }

        data class UnregisterChannel(val channel: ContactChannel) : Action()
    }

    internal sealed class Change {
        data object ShowLoading : Change()
        data class ShowError(val message: String? = null, val error: Throwable? = null) : Change()
        data class ShowContent(val state: State.Content) : Change()
        data class UpdateSubscriptions(val subscriptionId: String, val isSubscribed: Boolean) : Change()
        data class UpdateScopedSubscriptions(val subscriptionId: String, val scopes: Set<Scope>, val isSubscribed: Boolean) : Change()
        data class UpdateConditionState(val state: Condition.State) : Change()

        // Contact Management
        data class AddContactChannel(val channel: ContactChannel) : Change()
        data class RemoveContactChannel(val channel: ContactChannel) : Change()
        data class AssociateContactChannel(val address: String, val options: RegistrationOptions) : Change()
        data class DisassociateContactChannel(val channel: ContactChannel) : Change()
    }

    internal sealed class Effect {
        data class ShowContactManagementAddDialog(val item: Item.ContactManagement) : Effect()
        data class ShowContactManagementAddConfirmDialog(
            val item: Item.ContactManagement,
            val result: ContactChannelDialogInputView.DialogResult
        ) : Effect()
        data class ShowContactManagementRemoveDialog(val item: Item.ContactManagement, val channel: ContactChannel) : Effect()
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
            is Section.SectionBreak -> listOf(SectionBreakItem(section))
            is Section.Common -> {
                if (section.display.isEmpty()) {
                    // Ignore sections with no title and subtitle to avoid unwanted whitespace in
                    // the list if a section has no title/description and is being used as a
                    // container for an alert.
                    emptyList<PrefCenterItem>()
                } else {
                    listOf(SectionItem(section))
                } + section.items.map { item ->
                    when (item) {
                        is Item.ChannelSubscription -> ChannelSubscriptionItem(item)
                        is Item.ContactSubscription -> ContactSubscriptionItem(item)
                        is Item.ContactSubscriptionGroup -> ContactSubscriptionGroupItem(item)
                        is Item.Alert -> AlertItem(item)
                        is Item.ContactManagement -> ContactManagementItem(item)
                    }
                }
            }
        }
    }
