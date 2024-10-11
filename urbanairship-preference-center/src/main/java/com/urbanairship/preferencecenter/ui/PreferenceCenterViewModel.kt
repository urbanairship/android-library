package com.urbanairship.preferencecenter.ui

import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.preferencecenter.ConditionStateMonitor
import com.urbanairship.preferencecenter.PreferenceCenter
import com.urbanairship.preferencecenter.data.Condition
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.data.PreferenceCenterConfig
import com.urbanairship.preferencecenter.data.PreferenceCenterConfigParceler
import com.urbanairship.preferencecenter.data.Section
import com.urbanairship.preferencecenter.data.evaluate
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.State.Content.ContactChannelState
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
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import kotlinx.parcelize.TypeParceler

@OpenForTesting
internal class PreferenceCenterViewModel @JvmOverloads constructor(
    private val preferenceCenterId: String,
    private val savedStateHandle: SavedStateHandle,
    private val preferenceCenter: PreferenceCenter = PreferenceCenter.shared(),
    private val channel: AirshipChannel = UAirship.shared().channel,
    private val contact: Contact = UAirship.shared().contact,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val actionRunner: ActionRunner = DefaultActionRunner,
    private val conditionMonitor: ConditionStateMonitor = ConditionStateMonitor(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    internal companion object {
        private val defaultPendingLabelHideDelay = 30.seconds
        private val defaultResendLabelHideDelay = 15.seconds

        internal fun factory(preferenceCenterId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val savedStateHandle = createSavedStateHandle()
                PreferenceCenterViewModel(preferenceCenterId, savedStateHandle = savedStateHandle)
            }
        }
    }

    private val restoredState: State.Content? = savedStateHandle.get<State.Content>("state")

    private val stateFlow: MutableStateFlow<State> = MutableStateFlow(restoredState ?: State.Loading)
    private val actions: MutableSharedFlow<Action> = MutableSharedFlow()
    private val effectsChannel = Channel<Effect>()

    val states: StateFlow<State> = stateFlow.asStateFlow()
    val effects: Flow<Effect> = effectsChannel.receiveAsFlow()
        .onEach { UALog.v("! $it")}

    init {
        viewModelScope.launch(dispatcher) {
            actions.collect { action ->
                UALog.v("< $action")

                launch {
                    changes(action)
                        .scanConcat(states.value, ::states)
                        .collect { state ->
                            (state as? State.Content)?.let { savedStateHandle["state"] = it }
                            stateFlow.value = state
                        }
                }

                launch {
                    effects(action)
                        .collect(effectsChannel::send)
                }
            }
        }

        viewModelScope.launch(dispatcher) {
            contact.namedUserIdFlow
                .drop(1)
                .collect {
                    actions.emit(Action.Refresh)
                }
        }

        viewModelScope.launch(dispatcher) {
            states.collect { state -> UALog.v("> $state") }
        }

        // Collect updates from the condition monitor and repost them on the actions flow.
        conditionMonitor.states
            .map { Action.ConditionStateChanged(state = it) }
            .onEach { actions.emit(it) }
            .flowOn(dispatcher)
            .launchIn(viewModelScope)
    }

    fun handle(action: Action) {
        viewModelScope.launch(dispatcher) { actions.emit(action) }
    }

    /**
     * Flow that maps an [Action] to one or more [Change]s that impact viewmodel state.
     */
    private suspend fun changes(action: Action): Flow<Change> =
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
            is Action.ConditionStateChanged -> flowOf(Change.UpdateConditionState(action.state))
            is Action.UpdateContactChannel -> flowOf(
                Change.UpdateContactChannel(action.channel, action.channelState)
            )
            else -> emptyFlow()
        }

    /**
     * Helper to do basic formatting and validation of email address.
     */
    private fun formatAndValidateEmail(email: String?): Boolean {
        val formattedEmail = (email ?: "").trim().lowercase()
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".toRegex()
        return emailRegex.matches(formattedEmail)
    }

    /**
     * Flow that maps an [Action] to one or more side [Effect]s that do not impact viewmodel state.
     */
    private suspend fun effects(action: Action): Flow<Effect> =
        when (action) {
            // Airship Actions
            is Action.ButtonActions -> emptyFlow<Effect>().also {
                actionRunner.run(action.actions)
            }

            // Contact Management
            is Action.RequestAddChannel -> flowOf(
                Effect.ShowContactManagementAddDialog(action.item)
            )
            is Action.ValidateEmailChannel -> flowOf(
                if (formatAndValidateEmail(action.address)) {
                    Effect.DismissContactManagementAddDialog.also {
                        handle(
                            Action.RegisterChannel.Email(action.item, action.address)
                        )
                    }
                } else {
                    val message = action.item.platform.errorMessages.invalidMessage
                    Effect.ShowContactManagementAddDialogError(message)
                }
            )
            is Action.ValidateSmsChannel -> flowOf(
                if (contact.validateSms(action.address, action.senderId)) {
                    Effect.DismissContactManagementAddDialog.also {
                        handle(
                            Action.RegisterChannel.Sms(action.item, action.address, action.senderId)
                        )
                    }
                } else {
                    val message = action.item.platform.errorMessages.invalidMessage
                    Effect.ShowContactManagementAddDialogError(message)
                }
            )
            is Action.ConfirmAddChannel -> flowOf(
                Effect.ShowContactManagementAddConfirmDialog(action.item)
            )
            is Action.RequestRemoveChannel -> flowOf(
                Effect.ShowContactManagementRemoveDialog(action.item, action.channel)
            )
            is Action.RegisterChannel.Sms -> {
                contact.registerSms(action.address, SmsRegistrationOptions.options(action.senderId))

                // Show the onSubmit dialog if we have one
                action.item.addPrompt.prompt.onSubmit?.let {
                    flowOf(Effect.ShowContactManagementAddConfirmDialog(action.item))
                } ?: emptyFlow()
            }
            is Action.RegisterChannel.Email -> {
                val emailPlatform = action.item.platform as? Item.ContactManagement.Platform.Email

                contact.registerEmail(
                    action.address,
                    EmailRegistrationOptions.options(
                        transactionalOptedIn = null,
                        doubleOptIn = true,
                        properties = emailPlatform?.registrationOptions?.properties
                    )
                )

                // Show the onSubmit dialog if we have one
                action.item.addPrompt.prompt.onSubmit?.let {
                    flowOf(Effect.ShowContactManagementAddConfirmDialog(action.item))
                } ?: emptyFlow()
            }
            is Action.UnregisterChannel -> emptyFlow<Effect>().also {
                contact.disassociateChannel(action.channel)
            }
            is Action.ResendChannelVerification -> {
                viewModelScope.launch(dispatcher) {
                    handle(Action.UpdateContactChannel(
                        action.channel,
                        ContactChannelState(showPendingButton = true, showResendButton = false)
                    ))

                    val resendInterval = action.item.platform.resendOptions.interval.seconds
                    val resendDelay = resendInterval.coerceAtLeast(defaultResendLabelHideDelay)
                    delay(resendDelay)

                    handle(Action.UpdateContactChannel(
                        action.channel,
                        ContactChannelState(showPendingButton = true, showResendButton = true)
                    ))
                }

                contact.resendDoubleOptIn(action.channel)

                flowOf(Effect.ShowChannelVerificationResentDialog(action.item))
            }

            else -> emptyFlow()
        }

    /** Flow that reduces the current [State] and incoming [Change] to a new [State]. */
    private suspend fun states(state: State, change: Change): Flow<State> =
        when (change) {
            is Change.ShowLoading -> State.Loading
            is Change.ShowContent -> when (state) {
                is State.Content -> state.merge(
                    change.state,
                    onNewChannel = { channel ->
                        val isPending = !channel.isOptedIn
                        if (isPending) {
                            schedulePendingResendVisibilityChanges(channel)
                        } else {
                            cancelPendingResendVisibilityChanges(channel)
                        }
                        ContactChannelState(
                            showResendButton = false, showPendingButton = isPending
                        )
                    },
                    onExistingChannel = { channel, channelState ->
                        val isPending = !channel.isOptedIn
                        if (isPending) {
                            schedulePendingResendVisibilityChanges(channel, onlyHide = true)
                        } else {
                            cancelPendingResendVisibilityChanges(channel)
                        }
                        channelState
                    }
                )
                else -> change.state
            }
            is Change.ShowError -> State.Error(error = change.error)

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
            is Change.UpdateContactChannel -> when (state) {
                is State.Content -> state.copy(
                    contactChannelState = state.contactChannelState.map { (channel, state) ->
                        val updated = change.state

                        channel to if (channel == change.channel) {
                            state.copy(
                                showResendButton = updated.showResendButton,
                                showPendingButton = updated.showPendingButton
                            )
                        } else {
                            state
                        }
                    }.toMap()
                )

                else -> state
            }
        }.let { flowOf(it) }

    private fun refresh(): Flow<Change> = flow {
        emit(Change.ShowLoading)

        emitAll(
            enrichedConfig().map { (config, channelSubscriptions, contactSubscriptions, contactChannels) ->
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
                        contactChannelState = contactChannels.associateWith {
                            ContactChannelState(
                                showResendButton = !it.isOptedIn,
                                showPendingButton = !it.isOptedIn
                            )
                        },
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

    private var showResendButtonJobs: MutableMap<ContactChannel, Job> = mutableMapOf()
    private var hidePendingLabelJobs: MutableMap<ContactChannel, Job> = mutableMapOf()

    fun cancelPendingResendVisibilityChanges(channel: ContactChannel) {
        cancelPendingVisibilityChanges(channel)
        cancelResendVisibilityChanges(channel)
    }

    fun cancelPendingVisibilityChanges(channel: ContactChannel) {
        hidePendingLabelJobs[channel]?.cancel()
        hidePendingLabelJobs.remove(channel)
    }

    fun cancelResendVisibilityChanges(channel: ContactChannel) {
        showResendButtonJobs[channel]?.cancel()
        showResendButtonJobs.remove(channel)
    }

    fun schedulePendingResendVisibilityChanges(channel: ContactChannel, onlyHide: Boolean = false) {
        if (!onlyHide) {
            cancelResendVisibilityChanges(channel)
            showResendButtonJobs[channel] = viewModelScope.launch(dispatcher) {
                delay(defaultResendLabelHideDelay)
                handle(Action.UpdateContactChannel(
                    channel, ContactChannelState(showResendButton = true, showPendingButton = true)
                ))
            }
        }

        cancelPendingVisibilityChanges(channel)
        hidePendingLabelJobs[channel] = viewModelScope.launch(dispatcher) {
            delay(defaultPendingLabelHideDelay)
            handle(Action.UpdateContactChannel(
                channel, ContactChannelState(showPendingButton = false, showResendButton = false)
            ))
        }
    }

    private data class EnrichedConfig(
        val config: PreferenceCenterConfig,
        val channelSubscriptions: Set<String> = emptySet(),
        val contactSubscriptions: Map<String, Set<Scope>> = emptyMap(),
        val contactChannels: Set<ContactChannel> = emptySet()
    )

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
                    if (fetchChannelSubscriptions) getChannelSubscriptions() else flowOf(Result.success(emptySet())),
                    if (fetchContactSubscriptions) getContactSubscriptions() else flowOf(Result.success(emptyMap())),
                    if (fetchContactChannels) getAssociatedChannels() else flowOf(emptySet())
                ) { channelSubs, contactSubs, contactChannels ->
                    enrichedConfig.copy(
                        channelSubscriptions = getChannelSubscriptionsAsSet(channelSubs),
                        contactSubscriptions = if (mergeChannelDataToContact) mergeSubscriptions(getChannelSubscriptionsAsSet(channelSubs), getContactSubscriptionsAsMap(contactSubs)) else getContactSubscriptionsAsMap(contactSubs),
                        contactChannels = contactChannels.toSet()
                    )
                }
            }
            .distinctUntilChanged()

    private fun getConfig(preferenceCenterId: String): Flow<PreferenceCenterConfig> = flow {
        emit(preferenceCenter.getConfig(preferenceCenterId) ?: throw IllegalStateException("Null preference center for id: $preferenceCenterId"))
    }

    private fun getChannelSubscriptions(): Flow<Result<Set<String>>> = channel.subscriptions

    private fun getChannelSubscriptionsAsSet(subscriptionsResult: Result<Set<String>>): Set<String> {
        return subscriptionsResult.getOrNull()?.let { it } ?: emptySet()
    }

    private fun getContactSubscriptions(): Flow<Result<Map<String, Set<Scope>>>> = contact.subscriptions

    private fun getContactSubscriptionsAsMap(subscriptionsResult: Result<Map<String, Set<Scope>>>): Map<String, Set<Scope>> {
        return subscriptionsResult.getOrNull()?.let { it } ?: emptyMap()
    }

    private fun getAssociatedChannels(): Flow<Set<ContactChannel>> = contact.channelContacts.mapNotNull {
        it.getOrThrow().toSet()
    }

    @Parcelize
    internal sealed class State : Parcelable {
        @Parcelize
        data object Loading : State()

        @Parcelize
        data class Error(val message: String? = null, val error: Throwable? = null) : State()

        @Parcelize
        @TypeParceler<PreferenceCenterConfig, PreferenceCenterConfigParceler>
        @TypeParceler<ContactChannel, ContactChannelParceler>
        data class Content(
            val config: PreferenceCenterConfig,
            val conditionState: Condition.State,
            @IgnoredOnParcel
            val listItems: List<PrefCenterItem> = config.filterByConditions(conditionState).asPrefCenterItems(),
            val title: String?,
            val subtitle: String?,
            val channelSubscriptions: Set<String>,
            val contactSubscriptions: Map<String, Set<Scope>>,
            val contactChannels: Set<ContactChannel>,
            val contactChannelState: @RawValue Map<ContactChannel, ContactChannelState>,
        ) : State() {

            fun merge(
                update: Content,
                onNewChannel: (ContactChannel) -> ContactChannelState,
                onExistingChannel: (ContactChannel, ContactChannelState) -> ContactChannelState
            ): Content {
                return copy(
                    config = update.config,
                    listItems = update.listItems,
                    title = update.title,
                    subtitle = update.subtitle,
                    channelSubscriptions = update.channelSubscriptions,
                    contactSubscriptions = update.contactSubscriptions,
                    contactChannels = update.contactChannels,
                    contactChannelState = contactChannelState.filter {
                        // Drop any state that doesn't match the updated channels
                        it.key in update.contactChannels
                    }.mapValues {
                        onExistingChannel(it.key, it.value)
                    } + (
                        update.contactChannelState.filter {
                            // Add any new channels that weren't in the existing state
                            it.key !in contactChannelState
                        }.map { it.key to onNewChannel(it.key) }
                    )
                )
            }

            @Parcelize
            data class ContactChannelState(
                val showResendButton: Boolean = false,
                val showPendingButton: Boolean = false
            ) : Parcelable
        }
    }

    internal sealed class Action {
        data object Refresh : Action()
        data class PreferenceItemChanged(val item: Item, val isEnabled: Boolean) : Action()
        data class ScopedPreferenceItemChanged(
            val item: Item,
            val scopes: Set<Scope>,
            val isEnabled: Boolean
        ) : Action()

        data class ButtonActions(val actions: Map<String, JsonValue>) : Action()
        data class ConditionStateChanged(val state: Condition.State) : Action()

        // Contact Management
        data class RequestAddChannel(val item: Item.ContactManagement) : Action()
        data class RequestRemoveChannel(val item: Item.ContactManagement, val channel: ContactChannel) : Action()

        data class ConfirmAddChannel(
            val item: Item.ContactManagement,
            val result: ContactChannelDialogInputView.DialogResult
        ): Action()

        sealed class RegisterChannel : Action() {
            abstract val address: String
            abstract val item: Item.ContactManagement

            data class Email(
                override val item: Item.ContactManagement,
                override val address: String
            ) : RegisterChannel()

            data class Sms(
                override val item: Item.ContactManagement,
                override val address: String,
                val senderId: String
            ) : RegisterChannel()
        }

        data class UnregisterChannel(val channel: ContactChannel) : Action()

        data class ResendChannelVerification(val item: Item.ContactManagement, val channel: ContactChannel) : Action()

        data class ValidateSmsChannel(
            val item: Item.ContactManagement,
            val address: String,
            val senderId: String
        ) : Action()

        data class ValidateEmailChannel(
            val item: Item.ContactManagement,
            val address: String,
        ) : Action()

        data class UpdateContactChannel(
            val channel: ContactChannel,
            val channelState: ContactChannelState
        ) : Action()
    }

    internal sealed class Change {
        data object ShowLoading : Change()
        data class ShowError(val message: String? = null, val error: Throwable? = null) : Change()
        data class ShowContent(val state: State.Content) : Change()
        data class UpdateSubscriptions(val subscriptionId: String, val isSubscribed: Boolean) : Change()
        data class UpdateScopedSubscriptions(val subscriptionId: String, val scopes: Set<Scope>, val isSubscribed: Boolean) : Change()
        data class UpdateConditionState(val state: Condition.State) : Change()

        // Contact Management
        data class UpdateContactChannel(val channel: ContactChannel, val state: ContactChannelState) : Change()
    }

    internal sealed class Effect {
        data class ShowContactManagementAddDialog(val item: Item.ContactManagement) : Effect()
        data class ShowContactManagementAddConfirmDialog(val item: Item.ContactManagement) : Effect()
        data class ShowContactManagementRemoveDialog(val item: Item.ContactManagement, val channel: ContactChannel) : Effect()
        data class ShowChannelVerificationResentDialog(val item: Item.ContactManagement) : Effect()
        data class ShowContactManagementAddDialogError(val message: String) : Effect()
        data object DismissContactManagementAddDialog : Effect()
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

/**
 * Helper extension for determining if a [ContactChannel] is opted in.
 *
 * @hide
 */
@VisibleForTesting
internal val ContactChannel.isOptedIn: Boolean
    get() {
        return when (this) {
            is ContactChannel.Email -> when (registrationInfo) {
                is ContactChannel.Email.RegistrationInfo.Pending -> false
                is ContactChannel.Email.RegistrationInfo.Registered -> {
                    val info = registrationInfo as ContactChannel.Email.RegistrationInfo.Registered
                    when {
                        // If opted out is null, check for the presence of an opted in date
                        info.commercialOptedOut == null -> info.commercialOptedIn != null
                        // If opted in and out are both non-null, check to see if opted in is more recent
                        info.commercialOptedIn != null && info.commercialOptedOut != null ->
                            (info.commercialOptedIn ?: 0) > (info.commercialOptedOut ?: 0)
                        // Not opted in
                        else -> false
                    }
                }
            }
            is ContactChannel.Sms -> {
                when (registrationInfo) {
                    is ContactChannel.Sms.RegistrationInfo.Pending -> false
                    is ContactChannel.Sms.RegistrationInfo.Registered -> {
                        val info = registrationInfo as ContactChannel.Sms.RegistrationInfo.Registered
                        info.isOptIn
                    }
                }
            }
        }
    }

internal object ContactChannelParceler : Parceler<ContactChannel> {

    @Throws(JsonException::class)
    override fun create(parcel: Parcel): ContactChannel {
        return ContactChannel.fromJson(JsonValue.parseString(parcel.readString()))
    }

    @Throws(JsonException::class)
    override fun ContactChannel.write(parcel: Parcel, flags: Int) {
        parcel.writeString(this.toJsonValue().toString())
    }
}
