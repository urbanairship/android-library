package com.urbanairship.preferencecenter.compose.ui

import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.urbanairship.Airship
import com.urbanairship.UALog
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
import com.urbanairship.inputvalidation.AirshipInputValidation
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.preferencecenter.ConditionStateMonitor
import com.urbanairship.preferencecenter.PreferenceCenter
import com.urbanairship.preferencecenter.compose.ui.item.AlertItem
import com.urbanairship.preferencecenter.compose.ui.item.BasePrefCenterItem
import com.urbanairship.preferencecenter.compose.ui.item.ChannelSubscriptionItem
import com.urbanairship.preferencecenter.compose.ui.item.ContactManagementItem
import com.urbanairship.preferencecenter.compose.ui.item.ContactSubscriptionGroupItem
import com.urbanairship.preferencecenter.compose.ui.item.ContactSubscriptionItem
import com.urbanairship.preferencecenter.compose.ui.item.SectionBreakItem
import com.urbanairship.preferencecenter.compose.ui.item.SectionItem
import com.urbanairship.preferencecenter.data.Condition
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.data.PreferenceCenterConfig
import com.urbanairship.preferencecenter.data.PreferenceCenterConfigParceler
import com.urbanairship.preferencecenter.data.Section
import com.urbanairship.preferencecenter.data.evaluate
import com.urbanairship.preferencecenter.util.airshipScanConcat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import kotlinx.parcelize.TypeParceler

internal interface PreferenceCenterViewModel {
    val identifier: String
    val states: StateFlow<ViewState>

    val scope: CoroutineScope
    val displayDialog: StateFlow<ContactManagerDialog?>
    val errors: Flow<String?>
    fun handle(action: Action)

    companion object {
        internal fun forPreview(
            state: ViewState = ViewState.Loading
        ): PreferenceCenterViewModel = object : PreferenceCenterViewModel {
            override val identifier: String = "preview"
            override val states: StateFlow<ViewState> = MutableStateFlow(state)
            override val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
            override fun handle(action: Action) {}
            override val displayDialog: StateFlow<ContactManagerDialog?> = MutableStateFlow(null)
            override val errors: Flow<String?> = emptyFlow()
        }
    }
}

internal sealed class ContactManagerDialog {
    data class Add(val item: Item.ContactManagement) : ContactManagerDialog()
    data class ConfirmAdd(val message: Item.ContactManagement.ActionableMessage) : ContactManagerDialog()
    data class ResendConfirmation(val message: Item.ContactManagement.ActionableMessage) : ContactManagerDialog()
    data class Remove(val item: Item.ContactManagement, val channel: ContactChannel) : ContactManagerDialog()
}

@OpenForTesting
internal class DefaultPreferenceCenterViewModel(
    override val identifier: String,
    private val preferenceCenter: PreferenceCenter = PreferenceCenter.shared(),
    private val channel: AirshipChannel = Airship.channel,
    private val contact: Contact = Airship.contact,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val actionRunner: ActionRunner = DefaultActionRunner,
    private val conditionMonitor: ConditionStateMonitor = ConditionStateMonitor(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : PreferenceCenterViewModel, ViewModel() {
    internal companion object {
        private val defaultPendingLabelHideDelay = 30.seconds
        private val defaultResendLabelHideDelay = 15.seconds

        internal val IDENTIFIER_KEY = object : CreationExtras.Key<String> {}

        internal val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val identifier = this[IDENTIFIER_KEY]
                    ?: throw IllegalArgumentException("Missing Preference Center identifier")
                DefaultPreferenceCenterViewModel(identifier = identifier)
            }
        }
    }

    private val stateFlow: MutableStateFlow<ViewState> = MutableStateFlow(ViewState.Loading)
    private val actions: MutableSharedFlow<Action> = MutableSharedFlow()
    override val states: StateFlow<ViewState> = stateFlow.asStateFlow()

    private val dialogFlow: MutableStateFlow<ContactManagerDialog?> = MutableStateFlow(null)
    override val displayDialog: StateFlow<ContactManagerDialog?> = dialogFlow.asStateFlow()

    private val errorsFlow: MutableStateFlow<String?> = MutableStateFlow(null)
    override val errors: Flow<String?> = errorsFlow.asStateFlow()

    override val scope: CoroutineScope
        get() = viewModelScope

    init {
        viewModelScope.launch(dispatcher) {
            actions.collect { action ->
                UALog.v("< $action")

                launch {
                    changes(action)
                        .airshipScanConcat(states.value, ::states)
                        .collect { state ->
                            stateFlow.value = state
                        }
                }

                launch {
                    perform(action)
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

    override fun handle(action: Action) {
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
                Change.UpdateContactChannel(
                    channel = action.channel,
                    action.channelState
                )
            )
            else -> emptyFlow()
        }

    private suspend fun perform(action: Action) {
        when (action) {
            // Airship Actions
            is Action.ButtonActions -> actionRunner.run(action.actions)

            // Contact Management
            is Action.RequestAddChannel -> dialogFlow.update { ContactManagerDialog.Add(action.item) }

            is Action.ValidateEmailChannel -> {
                errorsFlow.emit(null)

                when(val result = validateEmailAction(action)) {
                    is AirshipInputValidation.Result.Valid -> {
                        dialogFlow.emit(null)

                        handle(
                            Action.RegisterChannel.Email(action.item, result.address)
                        )
                    }
                    AirshipInputValidation.Result.Invalid -> {
                        val message = action.item.platform.errorMessages.invalidMessage
                        errorsFlow.emit(message)
                    }
                }
            }

            is Action.ValidateSmsChannel -> {
                errorsFlow.emit(null)

                when(val result = validateSmsAction(action)) {
                    is AirshipInputValidation.Result.Valid -> {
                        dialogFlow.update { null }

                        handle(
                            Action.RegisterChannel.Sms(action.item, result.address, action.senderId)
                        )
                    }
                    AirshipInputValidation.Result.Invalid -> {
                        val message = action.item.platform.errorMessages.invalidMessage
                        errorsFlow.emit(message)
                    }
                }
            }

            is Action.ConfirmAddChannel -> {
                action.item.addPrompt.prompt.onSubmit?.let { message ->
                    dialogFlow.update { ContactManagerDialog.ConfirmAdd(message) }
                }
            }

            is Action.RequestRemoveChannel -> {
                dialogFlow.emit(ContactManagerDialog.Remove(action.item, action.channel))
            }

            is Action.RegisterChannel.Sms -> {
                contact.registerSms(action.address, SmsRegistrationOptions.options(action.senderId))

                // Show the onSubmit dialog if we have one
                action.item.addPrompt.prompt.onSubmit?.let { message ->
                    dialogFlow.update { ContactManagerDialog.ConfirmAdd(message) }
                }
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
                action.item.addPrompt.prompt.onSubmit?.let { message ->
                    dialogFlow.update { ContactManagerDialog.ConfirmAdd(message) }
                }
            }

            is Action.UnregisterChannel -> {
                contact.disassociateChannel(action.channel)
                dialogFlow.update { null }
            }

            is Action.ResendChannelVerification -> {
                viewModelScope.launch(dispatcher) {
                    handle(Action.UpdateContactChannel(
                        action.channel,
                        ViewState.Content.ContactChannelState(
                            showPendingButton = true,
                            showResendButton = false
                        )
                    ))

                    val resendInterval = action.item.platform.resendOptions.interval.seconds
                    val resendDelay = resendInterval.coerceAtLeast(defaultResendLabelHideDelay)
                    delay(resendDelay)

                    handle(Action.UpdateContactChannel(
                        action.channel,
                        ViewState.Content.ContactChannelState(
                            showPendingButton = true,
                            showResendButton = true
                        )
                    ))
                }

                contact.resendDoubleOptIn(action.channel)
                action.item.platform.resendOptions.onSuccess?.let { message ->
                    dialogFlow.update { ContactManagerDialog.ResendConfirmation(message) }
                }
            }

            is Action.DismissDialog -> {
                errorsFlow.emit(null)
                dialogFlow.emit(null)
            }

            else -> {}
        }
    }


    private suspend fun validateEmailAction(
        action: Action.ValidateEmailChannel
    ) = preferenceCenter.inputValidator.validate(
        request = AirshipInputValidation.Request.ValidateEmail(
            AirshipInputValidation.Request.Email(action.address)
        )
    )

    private suspend fun validateSmsAction(
        action: Action.ValidateSmsChannel
    ) = preferenceCenter.inputValidator.validate(
        request = AirshipInputValidation.Request.ValidateSms(
            sms = AirshipInputValidation.Request.Sms(
                rawInput = action.address,
                validationOptions = AirshipInputValidation.Request.Sms.ValidationOptions.Sender(
                    senderId = action.senderId,
                    prefix = action.prefix
                )
            )
        )
    )

    /** Flow that reduces the current [ViewState] and incoming [Change] to a new [ViewState]. */
    private fun states(state: ViewState, change: Change): Flow<ViewState> =
        when (change) {
            is Change.ShowLoading -> ViewState.Loading
            is Change.ShowContent -> when (state) {
                is ViewState.Content -> state.merge(
                    change.state,
                    onNewChannel = { channel ->
                        val isPending = !channel.isOptedIn
                        if (isPending) {
                            schedulePendingResendVisibilityChanges(channel)
                        } else {
                            cancelPendingResendVisibilityChanges(channel)
                        }
                        ViewState.Content.ContactChannelState(
                            showResendButton = false,
                            showPendingButton = isPending
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
            is Change.ShowError -> ViewState.Error(error = change.error)

            is Change.UpdateSubscriptions -> when (state) {
                is ViewState.Content -> {
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
                is ViewState.Content -> {
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
                is ViewState.Content -> {
                    val conditions = change.state
                    state.copy(
                        listItems = state.config.filterByConditions(conditions).asPrefCenterItems(),
                        conditionState = conditions
                    )
                }
                else -> state
            }
            is Change.UpdateContactChannel -> when (state) {
                is ViewState.Content -> state.copy(
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
                    ViewState.Content(
                        config = config,
                        listItems = filteredItems,
                        title = display.name,
                        subtitle = display.description,
                        channelSubscriptions = channelSubscriptions,
                        contactSubscriptions = contactSubscriptions,
                        contactChannels = contactChannels,
                        contactChannelState = contactChannels.associateWith {
                            ViewState.Content.ContactChannelState(
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

    private fun mergeSubscriptions(
        channelSubscriptions: Set<String>,
        contactSubscriptions: Map<String, Set<Scope>>
    ): Map<String, Set<Scope>> {
        val map = contactSubscriptions.toMutableMap()
        channelSubscriptions.forEach {
            val updated = map
                .getOrPut(it) { emptySet() }
                .toMutableSet()
                .apply { add(Scope.APP) }
            map[it] = updated
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
                channel.editSubscriptionLists {
                    mutate(subscriptionId, isEnabled)
                }
                emit(Change.UpdateSubscriptions(subscriptionId, isEnabled))
            }
            is Item.ContactSubscription -> with(item) {
                contact.editSubscriptionLists {
                    mutate(subscriptionId, scopes, isEnabled)
                }

                emit(Change.UpdateScopedSubscriptions(subscriptionId, scopes, isEnabled))
            }
            is Item.ContactSubscriptionGroup -> with(item) {
                contact.editSubscriptionLists {
                    mutate(subscriptionId, scopes, isEnabled)
                }

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
                    channel,
                    ViewState.Content.ContactChannelState(
                        showResendButton = true,
                        showPendingButton = true
                    )
                ))
            }
        }

        cancelPendingVisibilityChanges(channel)
        hidePendingLabelJobs[channel] = viewModelScope.launch(dispatcher) {
            delay(defaultPendingLabelHideDelay)
            handle(Action.UpdateContactChannel(
                channel,
                ViewState.Content.ContactChannelState(
                    showPendingButton = false,
                    showResendButton = false
                )
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
        getConfig(identifier)
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
        emit(preferenceCenter.getConfig(preferenceCenterId)
            ?: throw IllegalStateException("Null preference center for id: $preferenceCenterId"))
    }

    private fun getChannelSubscriptions(): Flow<Result<Set<String>>> = channel.subscriptions

    private fun getChannelSubscriptionsAsSet(subscriptionsResult: Result<Set<String>>): Set<String> {
        return subscriptionsResult.getOrNull() ?: emptySet()
    }

    private fun getContactSubscriptions(): Flow<Result<Map<String, Set<Scope>>>> = contact.subscriptions

    private fun getContactSubscriptionsAsMap(subscriptionsResult: Result<Map<String, Set<Scope>>>): Map<String, Set<Scope>> {
        return subscriptionsResult.getOrNull() ?: emptyMap()
    }

    private fun getAssociatedChannels(): Flow<Set<ContactChannel>> = contact.channelContacts.mapNotNull {
        it.getOrThrow().toSet()
    }

    internal sealed class Change {
        data object ShowLoading : Change()
        data class ShowError(val message: String? = null, val error: Throwable? = null) : Change()
        data class ShowContent(val state: ViewState.Content) : Change()
        data class UpdateSubscriptions(val subscriptionId: String, val isSubscribed: Boolean) : Change()
        data class UpdateScopedSubscriptions(val subscriptionId: String, val scopes: Set<Scope>, val isSubscribed: Boolean) : Change()
        data class UpdateConditionState(val state: Condition.State) : Change()

        // Contact Management
        data class UpdateContactChannel(
            val channel: ContactChannel,
            val state: ViewState.Content.ContactChannelState
        ) : Change()
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
internal fun PreferenceCenterConfig.asPrefCenterItems(): List<BasePrefCenterItem> =
    sections.flatMap { section ->
        when (section) {
            is Section.SectionBreak -> listOf(SectionBreakItem(section))
            is Section.Common -> {

                val sectionItems = section.items.mapNotNull { item ->
                    when (item) {
                        is Item.ChannelSubscription -> ChannelSubscriptionItem(item)
                        is Item.ContactSubscription -> ContactSubscriptionItem(item)
                        is Item.ContactSubscriptionGroup -> ContactSubscriptionGroupItem(item)
                        is Item.Alert -> AlertItem(item)
                        is Item.ContactManagement -> ContactManagementItem(item)
                    }
                }

                val base = if (section.display.isEmpty()) {
                    // Ignore sections with no title and subtitle to avoid unwanted whitespace in
                    // the list if a section has no title/description and is being used as a
                    // container for an alert.
                    emptyList<BasePrefCenterItem>()
                } else {
                    listOf(SectionItem(section))
                }

                base + sectionItems
            }
        }
    }

/**
 * Helper extension for determining if a [ContactChannel] is opted in.
 *
 * @hide
 */
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

/**
 * All possible states of the view.
 */
@Parcelize
internal sealed class ViewState : Parcelable {
    @Parcelize
    data object Loading : ViewState()

    @Parcelize
    data class Error(val message: String? = null, val error: Throwable? = null) : ViewState()

    @Parcelize
    @TypeParceler<PreferenceCenterConfig, PreferenceCenterConfigParceler>
    @TypeParceler<ContactChannel, ContactChannelParceler>
    data class Content(
        val config: PreferenceCenterConfig,
        val conditionState: Condition.State,
        @IgnoredOnParcel
        val listItems: List<BasePrefCenterItem> = config.filterByConditions(conditionState).asPrefCenterItems(),
        val title: String?,
        val subtitle: String?,
        val channelSubscriptions: Set<String>,
        val contactSubscriptions: Map<String, Set<Scope>>,
        val contactChannels: Set<ContactChannel>,
        val contactChannelState: @RawValue Map<ContactChannel, ContactChannelState>,
    ) : ViewState() {

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
    data object DismissDialog: Action()
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
        val result: DialogResult
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
        val senderId: String,
        val prefix: String? = null
    ) : Action()

    data class ValidateEmailChannel(
        val item: Item.ContactManagement,
        val address: String,
    ) : Action()

    data class UpdateContactChannel(
        val channel: ContactChannel,
        val channelState: ViewState.Content.ContactChannelState
    ) : Action()
}
