package com.urbanairship.preferencecenter.ui

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.actions.ActionRunner
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.channel.SubscriptionListEditor
import com.urbanairship.contacts.Contact
import com.urbanairship.contacts.ContactChannel
import com.urbanairship.contacts.EmailRegistrationOptions
import com.urbanairship.contacts.Scope
import com.urbanairship.contacts.ScopedSubscriptionListEditor
import com.urbanairship.contacts.SmsRegistrationOptions
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.preferencecenter.ConditionStateMonitor
import com.urbanairship.preferencecenter.PreferenceCenter
import com.urbanairship.preferencecenter.data.Button
import com.urbanairship.preferencecenter.data.CommonDisplay
import com.urbanairship.preferencecenter.data.Condition
import com.urbanairship.preferencecenter.data.Condition.OptInStatus.Status
import com.urbanairship.preferencecenter.data.IconDisplay
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.data.Item.ContactManagement.RegistrationOptions
import com.urbanairship.preferencecenter.data.Item.ContactSubscriptionGroup.Component
import com.urbanairship.preferencecenter.data.Options
import com.urbanairship.preferencecenter.data.PreferenceCenterConfig
import com.urbanairship.preferencecenter.data.Section
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.Action
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.Effect
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.State
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.State.Content.ContactChannelState
import com.urbanairship.preferencecenter.widget.ContactChannelDialogInputView
import kotlin.time.Duration.Companion.seconds
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.coVerifyAll
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class PreferenceCenterViewModelTest {
    public companion object {
        private const val PREF_CENTER_ID = "pref-center-id"
        private const val PREF_CENTER_TITLE = "Preference Center Title"
        private const val PREF_CENTER_SUBTITLE = "Preference Center Subtitle"

        private const val SUBSCRIPTION_ID_1 = "item-1-subscription-id"
        private const val SUBSCRIPTION_ID_2 = "item-2-subscription-id"
        private const val SUBSCRIPTION_ID_3 = "item-3-subscription-id"
        private const val SUBSCRIPTION_ID_4 = "item-4-subscription-id"

        private val CHANNEL_SUBSCRIPTION_CONFIG = PreferenceCenterConfig(
            id = PREF_CENTER_ID,
            display = CommonDisplay(PREF_CENTER_TITLE, PREF_CENTER_SUBTITLE),
            sections = listOf(
                Section.Common(
                    id = "section-1-id",
                    display = CommonDisplay("section-1-title", "section-1-subtitle"),
                    items = listOf(
                        Item.ChannelSubscription(
                            id = "item-1-id",
                            subscriptionId = SUBSCRIPTION_ID_1,
                            display = CommonDisplay("item-1-title", "item-1-subtitle"),
                            conditions = emptyList()
                        ),
                        Item.ChannelSubscription(
                            id = "item-2-id",
                            subscriptionId = SUBSCRIPTION_ID_2,
                            display = CommonDisplay("item-2-title"),
                            conditions = emptyList()
                        )
                    ),
                    conditions = emptyList()
                ),
                Section.Common(
                    id = "section-2-id",
                    display = CommonDisplay("section-2-title", "section-2-subtitle"),
                    items = listOf(
                        Item.ChannelSubscription(
                            id = "item-3-id",
                            subscriptionId = SUBSCRIPTION_ID_3,
                            display = CommonDisplay("item-3-title", "item-3-subtitle"),
                            conditions = emptyList()
                        ),
                        Item.ChannelSubscription(
                            id = "item-4-id",
                            subscriptionId = SUBSCRIPTION_ID_4,
                            display = CommonDisplay("item-4-title", "item-4-subtitle"),
                            conditions = emptyList()
                        )
                    ),
                    conditions = emptyList()
                )
            )
        )

        private val CONTACT_SUBSCRIPTION_ITEM_1 = Item.ContactSubscription(
            id = "item-1-id",
            subscriptionId = SUBSCRIPTION_ID_1,
            display = CommonDisplay("item-1-title", "item-1-subtitle"),
            scopes = setOf(Scope.APP, Scope.EMAIL),
            conditions = emptyList()
        )
        private val CONTACT_SUBSCRIPTION_ITEM_2 = Item.ContactSubscription(
            id = "item-2-id",
            subscriptionId = SUBSCRIPTION_ID_2,
            display = CommonDisplay("item-2-title"),
            scopes = setOf(Scope.SMS),
            conditions = emptyList()
        )
        private val CONTACT_SUBSCRIPTION_GROUP_ITEM_3 = Item.ContactSubscriptionGroup(
            id = "item-3-id",
            subscriptionId = SUBSCRIPTION_ID_3,
            display = CommonDisplay("item-3-title", "item-3-subtitle"),
            components = listOf(
                Component(scopes = setOf(Scope.APP), display = CommonDisplay("APP")),
                Component(scopes = setOf(Scope.EMAIL), display = CommonDisplay("EMAIL")),
                Component(scopes = setOf(Scope.SMS), display = CommonDisplay("SMS")),
            ),
            conditions = emptyList()
        )
        private val CONTACT_SUBSCRIPTION_GROUP_ITEM_4 = Item.ContactSubscriptionGroup(
            id = "item-4-id",
            subscriptionId = SUBSCRIPTION_ID_4,
            display = CommonDisplay("item-4-title", "item-4-subtitle"),
            components = listOf(
                Component(scopes = setOf(Scope.APP), display = CommonDisplay("APP")),
                Component(scopes = setOf(Scope.EMAIL), display = CommonDisplay("EMAIL")),
                Component(scopes = setOf(Scope.SMS), display = CommonDisplay("SMS")),
            ),
            conditions = emptyList()
        )

        private val CONTACT_SUBSCRIPTION_CONFIG = PreferenceCenterConfig(
            id = PREF_CENTER_ID,
            display = CommonDisplay(PREF_CENTER_TITLE, PREF_CENTER_SUBTITLE),
            sections = listOf(
                Section.SectionBreak(
                    id = "section-break-1-id",
                    display = CommonDisplay("section-break-1-label"),
                    conditions = emptyList()
                ),
                Section.Common(
                    id = "section-1-id",
                    display = CommonDisplay("section-1-title", "section-1-subtitle"),
                    items = listOf(CONTACT_SUBSCRIPTION_ITEM_1, CONTACT_SUBSCRIPTION_ITEM_2),
                    conditions = emptyList()
                ),
                Section.Common(
                    id = "section-2-id",
                    display = CommonDisplay("section-2-title", "section-2-subtitle"),
                    items = listOf(CONTACT_SUBSCRIPTION_GROUP_ITEM_3, CONTACT_SUBSCRIPTION_GROUP_ITEM_4),
                    conditions = emptyList()
                ),
            )
        )

        private val ALERT_CONDITIONS_CONFIG = PreferenceCenterConfig(
            id = PREF_CENTER_ID,
            display = CommonDisplay(PREF_CENTER_TITLE, PREF_CENTER_SUBTITLE),
            sections = listOf(
                Section.Common(
                    id = "section-1",
                    display = CommonDisplay.EMPTY,
                    items = listOf(
                        Item.Alert(
                            id = "alert",
                            iconDisplay = IconDisplay("icon-uri", "name", "description"),
                            button = Button("button", null, mapOf("foo" to JsonValue.wrap("bar"))),
                            conditions = emptyList()
                        )
                    ),
                    conditions = listOf(Condition.OptInStatus(status = Status.OPT_OUT))
                ),
                Section.Common(
                    id = "section-2",
                    display = CommonDisplay("section-2-title", "section-2-subtitle"),
                    items = listOf(CONTACT_SUBSCRIPTION_ITEM_1, CONTACT_SUBSCRIPTION_ITEM_2),
                    conditions = listOf(Condition.OptInStatus(status = Status.OPT_IN))
                ),
            )
        )
    }

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var preferenceCenter: PreferenceCenter
    private lateinit var channel: AirshipChannel
    private lateinit var contact: Contact
    private lateinit var conditionMonitor: ConditionStateMonitor
    private lateinit var savedStateHandle: SavedStateHandle

    private val actionRunner: ActionRunner = mockk {
        coJustRun { run(any(), any(), any(), any(), any()) }
    }

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun emitsInitialLoadingState(): TestResult = runTest {
        viewModel(mockPreferenceCenter = null, mockChannel = null).run {
            states.test {
                assertThat(awaitItem()).isEqualTo(State.Loading)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    public fun handlesRefreshActionWithMergeChannelData(): TestResult = runTest {
        val config = spyk(CHANNEL_SUBSCRIPTION_CONFIG)
        every { config.hasChannelSubscriptions } returns true
        every { config.hasContactSubscriptions } returns true
        every { config.options } returns Options(true)

        val expectedContact = mapOf(
            SUBSCRIPTION_ID_1 to setOf(Scope.APP),
            SUBSCRIPTION_ID_2 to setOf(Scope.APP),
            SUBSCRIPTION_ID_3 to setOf(Scope.WEB, Scope.APP),
            SUBSCRIPTION_ID_4 to setOf(Scope.WEB)
        )

        viewModel(config = config,
            channelSubscriptions = setOf(SUBSCRIPTION_ID_1, SUBSCRIPTION_ID_2, SUBSCRIPTION_ID_3),
            contactSubscriptions = mapOf(
                SUBSCRIPTION_ID_1 to setOf(Scope.APP),
                SUBSCRIPTION_ID_3 to setOf(Scope.WEB),
                SUBSCRIPTION_ID_4 to setOf(Scope.WEB)
            )
        ).run {
            states.test {
                assertThat(awaitItem()).isEqualTo(State.Loading)
                handle(Action.Refresh)

                val initialState = awaitItem()
                assertThat(initialState).isInstanceOf(State.Content::class.java)
                initialState as State.Content

                assertThat(initialState.contactSubscriptions).containsExactlyEntriesIn(expectedContact)
                assertThat(initialState.channelSubscriptions).containsExactly(SUBSCRIPTION_ID_1, SUBSCRIPTION_ID_2, SUBSCRIPTION_ID_3)

                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    public fun handlesRefreshAction(): TestResult = runTest {
        val config = spyk(CHANNEL_SUBSCRIPTION_CONFIG)
        every { config.hasChannelSubscriptions } returns true
        every { config.hasContactSubscriptions } returns true
        every { config.hasContactManagement } returns true

        viewModel(config = config).run {
            states.test {
                handle(Action.Refresh)
                assertThat(awaitItem()).isEqualTo(State.Loading)
                assertThat(awaitItem()).isInstanceOf(State.Content::class.java)

                cancelAndIgnoreRemainingEvents()
            }
            coVerify {
                contact.namedUserIdFlow
                channel.fetchSubscriptionLists()
                contact.fetchSubscriptionLists()
                contact.channelContacts
            }
            confirmVerified(channel, contact)
        }
    }

    @Test
    public fun handleNamedUserIdChange(): TestResult = runTest {
        val namedUserIdFlow = MutableStateFlow("")
        viewModel(namedUserIdFlow = namedUserIdFlow).run {
            states.test {
                handle(Action.Refresh)
                assertThat(awaitItem()).isEqualTo(State.Loading)
                assertThat(awaitItem()).isInstanceOf(State.Content::class.java)

                namedUserIdFlow.emit("some other user")

                assertThat(awaitItem()).isEqualTo(State.Loading)
                assertThat(awaitItem()).isInstanceOf(State.Content::class.java)

                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    public fun handlesRefreshActionWithoutChannelSubscriptions(): TestResult = runTest {
        val config = spyk(CHANNEL_SUBSCRIPTION_CONFIG)
        every { config.hasChannelSubscriptions } returns false
        every { config.hasContactSubscriptions } returns true
        every { config.hasContactManagement } returns true

        viewModel(config = config).run {
            states.test {
                handle(Action.Refresh)
                assertThat(awaitItem()).isEqualTo(State.Loading)
                assertThat(awaitItem()).isInstanceOf(State.Content::class.java)

                cancelAndIgnoreRemainingEvents()
            }
            coVerify {
                contact.namedUserIdFlow
                contact.fetchSubscriptionLists()
                contact.channelContacts
            }
            coVerify(exactly = 0) { channel.fetchSubscriptionLists() }
            confirmVerified(channel, contact)
        }
    }

    @Test
    public fun handlesRefreshActionWithoutContactSubscriptions(): TestResult = runTest {
        val config = spyk(CHANNEL_SUBSCRIPTION_CONFIG)
        every { config.hasChannelSubscriptions } returns true
        every { config.hasContactSubscriptions } returns false
        every { config.hasContactManagement } returns true

        viewModel(config = config).run {
            states.test {
                handle(Action.Refresh)
                assertThat(awaitItem()).isEqualTo(State.Loading)
                assertThat(awaitItem()).isInstanceOf(State.Content::class.java)

                cancelAndIgnoreRemainingEvents()
            }
            coVerify {
                contact.namedUserIdFlow
                channel.fetchSubscriptionLists()
                contact.channelContacts
            }
            coVerify(exactly = 0) { contact.fetchSubscriptionLists() }
            confirmVerified(channel, contact)
        }
    }

    @Test
    public fun handlesRefreshActionWithoutContactChannels(): TestResult = runTest {
        val config = spyk(CHANNEL_SUBSCRIPTION_CONFIG)
        every { config.hasChannelSubscriptions } returns true
        every { config.hasContactSubscriptions } returns true
        every { config.hasContactManagement } returns false

        viewModel(config = config).run {
            states.test {
                handle(Action.Refresh)
                assertThat(awaitItem()).isEqualTo(State.Loading)
                assertThat(awaitItem()).isInstanceOf(State.Content::class.java)

                cancelAndIgnoreRemainingEvents()
            }
            coVerify {
                contact.namedUserIdFlow
                channel.fetchSubscriptionLists()
                contact.fetchSubscriptionLists()
            }
            verify(exactly = 0) { contact.channelContacts }
            confirmVerified(channel, contact)
        }
    }

    //
    // Channel Subscriptions
    //

    @Test
    public fun handlesChannelPreferenceItemChangedActionSubscribe(): TestResult = runTest {
        val editor: SubscriptionListEditor = mockk {
            every { mutate(any(), any()) } returns this
            justRun { apply() }
        }

        viewModel(
            mockChannel = { every { editSubscriptionLists() } returns editor }
        ).run {
            val item = Item.ChannelSubscription("id", SUBSCRIPTION_ID_1, CommonDisplay.EMPTY, emptyList())

            states.test {
                assertThat(awaitItem()).isEqualTo(State.Loading)

                handle(Action.Refresh)
                assertThat(awaitItem()).isInstanceOf(State.Content::class.java)

                handle(Action.PreferenceItemChanged(item, isEnabled = true))
                val state = awaitItem()
                assertThat(state).isInstanceOf(State.Content::class.java)

                state as State.Content
                assertThat(state.channelSubscriptions).containsExactly(SUBSCRIPTION_ID_1)
                cancelAndIgnoreRemainingEvents()
            }

            coVerifyOrder {
                contact.namedUserIdFlow
                channel.fetchSubscriptionLists()

                channel.editSubscriptionLists()
                editor.mutate(item.subscriptionId, true)
                editor.apply()
            }
            confirmVerified(channel, editor)
        }
    }

    @Test
    public fun handlesChannelPreferenceItemChangedActionUnsubscribe(): TestResult = runTest {
        val editor = mockk<SubscriptionListEditor> {
            every { mutate(any(), any()) } returns this
            justRun { apply() }
        }

        viewModel(
            channelSubscriptions = setOf(SUBSCRIPTION_ID_1, SUBSCRIPTION_ID_2),
            mockChannel = { every { editSubscriptionLists() } returns editor }
        ).run {
            val item = Item.ChannelSubscription("id", SUBSCRIPTION_ID_2, CommonDisplay.EMPTY, emptyList())

            states.test {
                assertThat(awaitItem()).isEqualTo(State.Loading)

                handle(Action.Refresh)
                val initialState = awaitItem()
                assertThat(initialState).isInstanceOf(State.Content::class.java)
                initialState as State.Content
                assertThat(initialState.channelSubscriptions).containsExactly(SUBSCRIPTION_ID_1, SUBSCRIPTION_ID_2)

                handle(Action.PreferenceItemChanged(item, isEnabled = false))
                val state = awaitItem()
                assertThat(state).isInstanceOf(State.Content::class.java)

                state as State.Content
                assertThat(state.channelSubscriptions).containsExactly(SUBSCRIPTION_ID_1)
                cancel()
            }

            coVerifyOrder {
                channel.fetchSubscriptionLists()
                channel.editSubscriptionLists()
                editor.mutate(item.subscriptionId, false)
                editor.apply()
            }
            confirmVerified(channel, editor)
        }
    }

    //
    // Contact Subscriptions
    //

    @Test
    public fun handlesContactPreferenceItemChangedActionSubscribe(): TestResult = runTest {
        val editor = mockk<ScopedSubscriptionListEditor> {
            every { mutate(any(), any(), any()) } returns this
            justRun { apply() }
        }

        val expectedSubscriptions = mapOf(
            SUBSCRIPTION_ID_1 to CONTACT_SUBSCRIPTION_ITEM_1.scopes
        )

        viewModel(
            config = CONTACT_SUBSCRIPTION_CONFIG,
            mockContact = { every { editSubscriptionLists() } returns editor }
        ).run {
            val item = CONTACT_SUBSCRIPTION_ITEM_1

            states.test {
                assertThat(awaitItem()).isEqualTo(State.Loading)

                handle(Action.Refresh)
                assertThat(awaitItem()).isInstanceOf(State.Content::class.java)

                handle(Action.ScopedPreferenceItemChanged(item, item.scopes, isEnabled = true))
                val state = awaitItem()
                assertThat(state).isInstanceOf(State.Content::class.java)

                state as State.Content
                assertThat(state.contactSubscriptions).containsExactlyEntriesIn(expectedSubscriptions)
                cancelAndIgnoreRemainingEvents()
            }

            coVerifyOrder {
                contact.namedUserIdFlow
                contact.fetchSubscriptionLists()

                contact.editSubscriptionLists()
                editor.mutate(item.subscriptionId, item.scopes, true)
                editor.apply()
            }
            coVerify(exactly = 0) {
                channel.fetchSubscriptionLists()
                channel.editSubscriptionLists()
            }
            confirmVerified(channel, contact, editor)
        }
    }

    @Test
    public fun handlesContactPreferenceItemChangedActionUnsubscribe(): TestResult = runTest {
        val editor = mockk<ScopedSubscriptionListEditor> {
            every { mutate(any(), any(), any()) } returns this
            justRun { apply() }
        }

        val initialSubscriptions = mapOf(
            SUBSCRIPTION_ID_1 to CONTACT_SUBSCRIPTION_ITEM_1.scopes,
            SUBSCRIPTION_ID_2 to CONTACT_SUBSCRIPTION_ITEM_2.scopes
        )

        val expectedSubscriptions = mapOf(
            SUBSCRIPTION_ID_1 to emptySet(),
            SUBSCRIPTION_ID_2 to CONTACT_SUBSCRIPTION_ITEM_2.scopes
        )

        viewModel(
            config = CONTACT_SUBSCRIPTION_CONFIG,
            contactSubscriptions = initialSubscriptions,
            mockContact = { every { editSubscriptionLists() } returns editor }
        ).run {
            val item = CONTACT_SUBSCRIPTION_ITEM_1

            states.test {
                assertThat(awaitItem()).isEqualTo(State.Loading)

                handle(Action.Refresh)
                val initialState = awaitItem()
                assertThat(initialState).isInstanceOf(State.Content::class.java)
                initialState as State.Content
                assertThat(initialState.contactSubscriptions).containsExactlyEntriesIn(initialSubscriptions)

                handle(Action.ScopedPreferenceItemChanged(item, item.scopes, isEnabled = false))
                val state = awaitItem()
                assertThat(state).isInstanceOf(State.Content::class.java)

                state as State.Content
                assertThat(state.contactSubscriptions).containsExactlyEntriesIn(expectedSubscriptions)

                cancelAndIgnoreRemainingEvents()
            }

            coVerifyOrder {
                contact.namedUserIdFlow
                contact.fetchSubscriptionLists()

                contact.editSubscriptionLists()
                editor.mutate(item.subscriptionId, item.scopes, false)
                editor.apply()
            }
            coVerify(exactly = 0) {
                channel.fetchSubscriptionLists()
                channel.editSubscriptionLists()
            }
            confirmVerified(channel, contact, editor)
        }
    }

    //
    // Channel Subscription Groups
    //

    @Test
    public fun handlesContactPreferenceGroupChangedActionSubscribe(): TestResult = runTest {
        val editor = mockk<ScopedSubscriptionListEditor> {
            every { mutate(any(), any(), any()) } returns this
            justRun { apply() }
        }

        val item = CONTACT_SUBSCRIPTION_GROUP_ITEM_3
        val component = item.components.first()

        val expectedSubscriptions = mapOf(
            item.subscriptionId to component.scopes
        )

        viewModel(
            config = CONTACT_SUBSCRIPTION_CONFIG,
            mockContact = { every { editSubscriptionLists() } returns editor }
        ).run {
            states.test {
                assertThat(awaitItem()).isEqualTo(State.Loading)

                handle(Action.Refresh)
                assertThat(awaitItem()).isInstanceOf(State.Content::class.java)

                handle(Action.ScopedPreferenceItemChanged(item, component.scopes, isEnabled = true))
                val state = awaitItem()
                assertThat(state).isInstanceOf(State.Content::class.java)

                state as State.Content
                assertThat(state.contactSubscriptions).containsExactlyEntriesIn(expectedSubscriptions)
                cancel()
            }

            coVerifyOrder {
                contact.namedUserIdFlow
                contact.fetchSubscriptionLists()

                contact.editSubscriptionLists()
                editor.mutate(item.subscriptionId, component.scopes, true)
                editor.apply()
            }
            coVerify(exactly = 0) {
                channel.fetchSubscriptionLists()
                channel.editSubscriptionLists()
            }
            confirmVerified(channel, contact, editor)
        }
    }

    @Test
    public fun handlesContactPreferenceGroupChangedActionUnsubscribe(): TestResult = runTest {
        val editor = mockk<ScopedSubscriptionListEditor> {
            every { mutate(any(), any(), any()) } returns this
            justRun { apply() }
        }

        val itemThreeScopes = CONTACT_SUBSCRIPTION_GROUP_ITEM_3.components.flatMap { it.scopes }.toSet()
        val itemFourScopes = CONTACT_SUBSCRIPTION_GROUP_ITEM_4.components.flatMap { it.scopes }.toSet()
        val unsubscribeScopes = setOf(Scope.EMAIL, Scope.SMS)

        val initialSubscriptions = mapOf(
            SUBSCRIPTION_ID_3 to itemThreeScopes,
            SUBSCRIPTION_ID_4 to itemFourScopes
        )

        val expectedSubscriptions = mapOf(
            SUBSCRIPTION_ID_3 to itemThreeScopes,
            SUBSCRIPTION_ID_4 to (itemFourScopes - unsubscribeScopes)
        )

        viewModel(
            config = CONTACT_SUBSCRIPTION_CONFIG,
            contactSubscriptions = initialSubscriptions,
            mockContact = { every { editSubscriptionLists() } returns editor }
        ).run {
            val item = CONTACT_SUBSCRIPTION_GROUP_ITEM_4

            states.test {
                assertThat(awaitItem()).isEqualTo(State.Loading)

                handle(Action.Refresh)
                val initialState = awaitItem()
                assertThat(initialState).isInstanceOf(State.Content::class.java)
                initialState as State.Content
                assertThat(initialState.contactSubscriptions).containsExactlyEntriesIn(initialSubscriptions)

                handle(Action.ScopedPreferenceItemChanged(item, unsubscribeScopes, isEnabled = false))
                val state = awaitItem()
                assertThat(state).isInstanceOf(State.Content::class.java)

                state as State.Content
                assertThat(state.contactSubscriptions).containsExactlyEntriesIn(expectedSubscriptions)

                cancel()
            }

            coVerifyOrder {
                contact.namedUserIdFlow
                contact.fetchSubscriptionLists()

                contact.editSubscriptionLists()
                editor.mutate(item.subscriptionId, unsubscribeScopes, false)
                editor.apply()
            }
            coVerify(exactly = 0) {
                channel.fetchSubscriptionLists()
                channel.editSubscriptionLists()
            }
            confirmVerified(channel, contact, editor)
        }
    }

    //
    // Condition State Updates
    //

    public fun handlesConditionStateUpdate(): TestResult = runTest {
        val initialConditions = Condition.State(isOptedIn = false)
        val updatedConditions = Condition.State(isOptedIn = true)

        val stateFlow = MutableStateFlow(initialConditions)

        viewModel(
            config = ALERT_CONDITIONS_CONFIG,
            mockConditionStateMonitor = {
                every { currentState } returns stateFlow.value
                every { states } returns stateFlow
            },
            conditionState = initialConditions
        ).run {
            states.test {
                assertThat(awaitItem()).isEqualTo(State.Loading)

                handle(Action.Refresh)

                val initialContent = awaitItem()
                // Ensure we received an initial content state
                assertThat(initialContent).isInstanceOf(State.Content::class.java)
                initialContent as State.Content
                assertThat(initialContent.conditionState).isEqualTo(initialConditions)
                // Config should contain the entire unfiltered config
                assertThat(initialContent.config).isEqualTo(ALERT_CONDITIONS_CONFIG)

                // List items should only contain the alert item when notifications are opted out
                assertThat(initialContent.listItems).hasSize(1)
                assertThat(initialContent.listItems.first()).isInstanceOf(Item.Alert::class.java)

                // Update condition state to fake a notification opt-in
                stateFlow.value = updatedConditions

                val updatedContent = awaitItem()
                // Sanity check
                assertThat(updatedContent).isInstanceOf(State.Content::class.java)
                updatedContent as State.Content
                assertThat(updatedContent.conditionState).isEqualTo(updatedConditions)

                // List items should contain a section with two subscription pref items
                assertThat(updatedContent.listItems).hasSize(3)
                assertThat(updatedContent.listItems[0]).isInstanceOf(Section.Common::class.java)
                assertThat(updatedContent.listItems[1]).isInstanceOf(Item.ContactSubscription::class.java)
                assertThat(updatedContent.listItems[2]).isInstanceOf(Item.ContactSubscription::class.java)
            }
        }
    }

    //
    // Alert Item
    //

    @Test
    public fun handlesButtonActions(): TestResult = runTest {
        val actions = mapOf(
            "deeplink" to JsonValue.wrap("foo"),
            "rate-app" to JsonValue.wrap("bar"),
            "app-store" to JsonValue.wrap("baz")
        )

        viewModel().run {
            handle(Action.ButtonActions(actions))
            advanceUntilIdle()
        }

        actions.forEach {
            verify(exactly = 1) { actionRunner.run(name = it.key, value = it.value) }
        }
        confirmVerified(actionRunner)
    }

    //
    // List Filtering
    //

    @Test
    public fun testFiltersEmptySection(): TestResult = runTest {
        val conf = ALERT_CONDITIONS_CONFIG
        val configItemCount = conf.sections.count() + conf.sections.sumOf { it.items.count() }
        // Sanity check that we have 2 sections with 1 alert item in first and 2 subscription
        // preference in the second
        assertEquals(5, configItemCount)

        val conditions = Condition.State(isOptedIn = false)
        val filteredItems = conf.filterByConditions(conditions).asPrefCenterItems().count()
        // Verify that the empty section wasn't converted into a PrefCenterItem
        assertEquals(1, filteredItems)
    }

    @Test
    public fun testFiltersNotificationOptIn(): TestResult = runTest {
        val conf = ALERT_CONDITIONS_CONFIG
        val configItemCount = conf.sections.count() + conf.sections.sumOf { it.items.count() }
        // Sanity check that we have 2 sections with 1 alert item in first and 2 subscription
        // preference in the second
        assertEquals(5, configItemCount)

        val conditions = Condition.State(isOptedIn = true)
        val filteredItems = conf.filterByConditions(conditions).asPrefCenterItems().count()
        // Verify that we ended up with 1 section items + 2 preference items
        assertEquals(3, filteredItems)
    }

    //
    // Contact Channel Management
    //

    @Test
    public fun testAddContactChannel(): TestResult = runTest {
        val mockItem: Item.ContactManagement = mockk {}
        viewModel().run {
            states.test {
                assertThat(awaitItem()).isEqualTo(State.Loading)
                handle(Action.Refresh)
                assertThat(awaitItem()).isInstanceOf(State.Content::class.java)
                ensureAllEventsConsumed()
            }

            effects.test {
                handle(Action.RequestAddChannel(mockItem))
                assertThat(awaitItem()).isEqualTo(Effect.ShowContactManagementAddDialog(mockItem))
                ensureAllEventsConsumed()
            }
        }
    }

    @Test
    public fun testConfirmAddContactChannel(): TestResult = runTest {
        val mockItem: Item.ContactManagement = mockk {}
        val dialogResult: ContactChannelDialogInputView.DialogResult = mockk {}

        viewModel().run {
            states.test {
                assertThat(awaitItem()).isEqualTo(State.Loading)
                handle(Action.Refresh)
                assertThat(awaitItem()).isInstanceOf(State.Content::class.java)
                ensureAllEventsConsumed()
            }

            effects.test {
                handle(Action.ConfirmAddChannel(mockItem, dialogResult))
                assertThat(awaitItem()).isEqualTo(Effect.ShowContactManagementAddConfirmDialog(mockItem))
                ensureAllEventsConsumed()
            }
        }
    }

    @Test
    public fun testRemoveContactChannel(): TestResult = runTest {
        val msisdn = "15038675309"
        val senderId = "123456"

        val contactChannel = ContactChannel.Sms(
            ContactChannel.Sms.RegistrationInfo.Pending(
                address = msisdn,
                registrationOptions = SmsRegistrationOptions.options(senderId)
            )
        )

        val mockItem: Item.ContactManagement = mockk {}

        viewModel().run {
            states.test {
                assertThat(awaitItem()).isEqualTo(State.Loading)
                handle(Action.Refresh)
                assertThat(awaitItem()).isInstanceOf(State.Content::class.java)
                ensureAllEventsConsumed()
            }

            effects.test {
                handle(Action.RequestRemoveChannel(mockItem, contactChannel))
                assertThat(awaitItem()).isEqualTo(Effect.ShowContactManagementRemoveDialog(mockItem, contactChannel))
                ensureAllEventsConsumed()
            }
        }
    }

    @Test
    public fun testRegisterUnregisterSmsChannel(): TestResult = runTest {
        val msisdn = "15038675309"
        val senderId = "123456"
        val registrationInfo = ContactChannel.Sms.RegistrationInfo.Pending(
            address = msisdn,
            registrationOptions = SmsRegistrationOptions.options(senderId)
        )
        val contactChannel = ContactChannel.Sms(registrationInfo = registrationInfo)

        val mockItem: Item.ContactManagement = mockk {
            every { addPrompt } returns mockk(relaxed = true)
        }

        viewModel().run {
            states.test {
                assertThat(awaitItem()).isEqualTo(State.Loading)
                handle(Action.Refresh)
                val initialState = awaitItem() as State.Content
                assertThat(initialState.contactChannels).isEmpty()
                ensureAllEventsConsumed()
            }

            effects.test {
                handle(Action.RegisterChannel.Sms(mockItem, msisdn, senderId))
                advanceUntilIdle()
                assertThat(awaitItem())
                    .isInstanceOf(Effect.ShowContactManagementAddConfirmDialog::class.java)

                handle(Action.UnregisterChannel(contactChannel))
                advanceUntilIdle()
                ensureAllEventsConsumed()
            }
        }
        verifyOrder {
            contact.namedUserIdFlow
            contact.registerSms(msisdn, registrationInfo.registrationOptions)
            contact.disassociateChannel(contactChannel)
        }
        confirmVerified(contact)
    }

    @Test
    public fun testRegisterUnregisterEmailChannel(): TestResult = runTest {
        val address = "someone@example.com"
        val optInProperties = jsonMapOf("foo" to "bar")
        val registrationInfo = ContactChannel.Email.RegistrationInfo.Pending(
            address = address,
            registrationOptions = EmailRegistrationOptions.options(
                doubleOptIn = true,
                properties = optInProperties
            )
        )
        val contactChannel = ContactChannel.Email(registrationInfo = registrationInfo)

        val mockItem: Item.ContactManagement = mockk {
            every { addPrompt } returns mockk(relaxed = true)
            every { platform } returns Item.ContactManagement.Platform.Email(
                registrationOptions = mockk {
                    every { properties } returns optInProperties
                }
            )
        }

        viewModel().run {
            states.test {
                assertThat(awaitItem()).isEqualTo(State.Loading)
                handle(Action.Refresh)
                val initialState = awaitItem() as State.Content
                assertThat(initialState.contactChannels).isEmpty()
                ensureAllEventsConsumed()
            }

            effects.test {
                handle(Action.RegisterChannel.Email(mockItem, address))
                advanceUntilIdle()
                assertThat(awaitItem())
                    .isInstanceOf(Effect.ShowContactManagementAddConfirmDialog::class.java)

                handle(Action.UnregisterChannel(contactChannel))
                advanceUntilIdle()

                ensureAllEventsConsumed()
            }
        }
        verifyOrder {
            contact.namedUserIdFlow
            contact.registerEmail(address, registrationInfo.registrationOptions)
            contact.disassociateChannel(contactChannel)
        }
        confirmVerified(contact)
    }

    @Test
    public fun testValidateSmsChannelValid(): TestResult = runTest {
        val item: Item.ContactManagement = mockk {
            every { addPrompt } returns mockk(relaxed = true)
            every { platform } returns Item.ContactManagement.Platform.Sms(
                registrationOptions = mockk {
                    every { errorMessages } returns mockk(relaxed = true)
                }
            )
        }
        val address = "15031112222"
        val senderId = "123456"

        viewModel(
            mockContact = { coEvery { validateSms(address, senderId) } returns true }
        ).run {
            states.test {
                assertThat(awaitItem()).isEqualTo(State.Loading)
                handle(Action.Refresh)
                assertThat(awaitItem()).isInstanceOf(State.Content::class.java)
                ensureAllEventsConsumed()
            }

            effects.test {
                handle(Action.ValidateSmsChannel(item, address, senderId))
                assertThat(awaitItem()).isEqualTo(Effect.DismissContactManagementAddDialog)
                ensureAllEventsConsumed()
            }

            coVerify { contact.validateSms(address, senderId) }
        }
    }

    @Test
    public fun testValidateSmsChannelInvalid(): TestResult = runTest {
        val invalidMessage = "Invalid message"
        val item: Item.ContactManagement = mockk {
            every { addPrompt } returns mockk(relaxed = true)
            every { platform } returns Item.ContactManagement.Platform.Sms(
                registrationOptions = mockk {
                    every { errorMessages } returns Item.ContactManagement.ErrorMessages(
                        invalidMessage = invalidMessage, defaultMessage = "Default message"
                    )
                }
            )
        }
        val address = "15031112222"
        val senderId = "123456"

        viewModel(
            mockContact = { coEvery { validateSms(address, senderId) } returns false }
        ).run {
            states.test {
                assertThat(awaitItem()).isEqualTo(State.Loading)
                handle(Action.Refresh)
                assertThat(awaitItem()).isInstanceOf(State.Content::class.java)
                ensureAllEventsConsumed()
            }

            effects.test {
                handle(Action.ValidateSmsChannel(item, address, senderId))
                assertThat(awaitItem()).isEqualTo(Effect.ShowContactManagementAddDialogError(invalidMessage))
                ensureAllEventsConsumed()
            }
        }

        coVerify { contact.validateSms(address, senderId) }
    }

    @Test
    public fun testResendChannelVerification(): TestResult = runTest {
        val item: Item.ContactManagement = mockk {
            every { platform } returns Item.ContactManagement.Platform.Email(
                registrationOptions = mockk {
                    every { properties } returns null
                    every { resendOptions } returns mockk {
                        every { interval } returns 3
                    }
                }
            )
        }
        val contactChannel: ContactChannel = mockk { }

        viewModel(
            mockContact = { coJustRun { resendDoubleOptIn(any()) } }
        ).run {
            states.test {
                assertThat(awaitItem()).isEqualTo(State.Loading)
                handle(Action.Refresh)
                assertThat(awaitItem()).isInstanceOf(State.Content::class.java)
                ensureAllEventsConsumed()
            }

            effects.test {
                handle(Action.ResendChannelVerification(item, contactChannel))
                assertThat(awaitItem()).isEqualTo(Effect.ShowChannelVerificationResentDialog(item))
                ensureAllEventsConsumed()
            }
        }

        coVerify { contact.resendDoubleOptIn(contactChannel) }
    }

    @Test
    public fun testUpdateContactChannels(): TestResult = runTest {
        val config = spyk(CHANNEL_SUBSCRIPTION_CONFIG)
        every { config.hasChannelSubscriptions } returns true
        every { config.hasContactSubscriptions } returns true
        every { config.hasContactManagement } returns true

        val channel1 = ContactChannel.Email(
            registrationInfo = ContactChannel.Email.RegistrationInfo.Pending(
                address = "someone@example.com",
                registrationOptions = EmailRegistrationOptions.options(doubleOptIn = true)
            )
        )

        val channel1Registered = ContactChannel.Email(
            registrationInfo = ContactChannel.Email.RegistrationInfo.Registered(
                channelId = "channel-id",
                maskedAddress = "t**t@example.com",
                transactionalOptedIn = 100L,
                commercialOptedIn = 200L
            )
        )

        val channel2 = ContactChannel.Sms(
            registrationInfo = ContactChannel.Sms.RegistrationInfo.Pending(
                address = "15038675309",
                registrationOptions = SmsRegistrationOptions.options("123456")
            )
        )

        val initialChannels = listOf(channel1)
        val updatedChannels1 = listOf(channel1, channel2)
        val updatedChannels2 = listOf(channel1Registered, channel2)

        val contactChannelsFlow = MutableSharedFlow<Result<List<ContactChannel>>>(replay = 1)

        viewModel(
            config = config,
            mockContact = { every { channelContacts } returns contactChannelsFlow },
            dispatcher = testDispatcher
        ).run {
            states.test {
                assertThat(awaitItem()).isEqualTo(State.Loading)
                contactChannelsFlow.emit(Result.success(initialChannels))

                handle(Action.Refresh)
                val initialState = awaitItem() as State.Content
                assertThat(initialState.contactChannels).containsExactlyElementsIn(initialChannels)

                contactChannelsFlow.emit(Result.success(updatedChannels1))
                val updatedState1 = awaitItem() as State.Content
                assertThat(updatedState1.contactChannels).containsExactlyElementsIn(updatedChannels1)

                contactChannelsFlow.emit(Result.success(updatedChannels2))
                val updatedState2 = awaitItem() as State.Content
                assertThat(updatedState2.contactChannels).containsExactlyElementsIn(updatedChannels2)

                assertThat(cancelAndConsumeRemainingEvents()).isEmpty()
            }
        }
    }

    @Test
    public fun testUpdateContactChannelsState(): TestResult = runTest {
        val config = spyk(CHANNEL_SUBSCRIPTION_CONFIG)
        every { config.hasChannelSubscriptions } returns true
        every { config.hasContactSubscriptions } returns true
        every { config.hasContactManagement } returns true

        val address = "someone@example.com"
        val registrationInfo = ContactChannel.Email.RegistrationInfo.Pending(
            address = address,
            registrationOptions = EmailRegistrationOptions.options(doubleOptIn = true)
        )
        val contactChannel = ContactChannel.Email(registrationInfo = registrationInfo)
        val initialChannelState = ContactChannelState(showResendButton = true, showPendingButton = true)

        val updatedChannelState = ContactChannelState(showResendButton = false, showPendingButton = false)

        viewModel(
            config = config,
            contactChannels = listOf(contactChannel),
            dispatcher = testDispatcher
        ).run {
            states.test {
                assertThat(awaitItem()).isEqualTo(State.Loading)
                handle(Action.Refresh)

                val initialState = awaitItem() as State.Content
                assertThat(initialState.contactChannels).containsExactly(contactChannel)
                assertThat(initialState.contactChannelState).containsExactly(contactChannel, initialChannelState)

                handle(Action.UpdateContactChannel(contactChannel, updatedChannelState))

                val updatedState = awaitItem() as State.Content
                assertThat(initialState.contactChannels).containsExactly(contactChannel)
                assertThat(updatedState.contactChannelState).containsExactly(contactChannel, updatedChannelState)

                ensureAllEventsConsumed()
            }
        }
        coVerifyAll {
            contact.namedUserIdFlow
            contact.fetchSubscriptionLists()
            channel.fetchSubscriptionLists()
            contact.channelContacts
        }
        confirmVerified(contact, channel)
    }

    private fun TestScope.viewModel(
        config: PreferenceCenterConfig = CHANNEL_SUBSCRIPTION_CONFIG,
        channelSubscriptions: Set<String> = emptySet(),
        contactSubscriptions: Map<String, Set<Scope>> = emptyMap(),
        contactChannels: List<ContactChannel> = emptyList(),
        preferenceCenterId: String = config.id,
        ioDispatcher: CoroutineDispatcher = testDispatcher,
        dispatcher: CoroutineDispatcher = testDispatcher,
        mockPreferenceCenter: (PreferenceCenter.() -> Unit)? = {},
        mockChannel: (AirshipChannel.() -> Unit)? = {},
        mockContact: (Contact.() -> Unit)? = {},
        mockConditionStateMonitor: (ConditionStateMonitor.() -> Unit)? = {},
        conditionState: Condition.State = Condition.State(isOptedIn = true),
        namedUserIdFlow: StateFlow<String?> = MutableStateFlow(null),
        mockSavedStateHandle: (SavedStateHandle.() -> Unit)? = {}
    ): PreferenceCenterViewModel {
        preferenceCenter = if (mockPreferenceCenter == null) {
            mockk(relaxUnitFun = true)
        } else {
            mockk<PreferenceCenter> {
                coEvery { getConfig(preferenceCenterId) } returns config
            }.also(mockPreferenceCenter::invoke)
        }
        channel = if (mockChannel == null) {
            mockk(relaxUnitFun = true)
        } else {
            mockk<AirshipChannel> {
                coEvery { fetchSubscriptionLists() } returns Result.success(channelSubscriptions)
            }.also(mockChannel::invoke)
        }
        contact = if (mockContact == null) {
            mockk(relaxUnitFun = true)
        } else {
            mockk<Contact>(relaxed = true) {
                coEvery { fetchSubscriptionLists() } returns Result.success(contactSubscriptions)
                every { this@mockk.namedUserIdFlow } returns namedUserIdFlow
                every { channelContacts } answers {
                    flowOf(Result.success(contactChannels))
                }
            }.also(mockContact::invoke)
        }
        conditionMonitor = if (mockConditionStateMonitor == null) {
            mockk {
                every { currentState } returns conditionState
                every { states } returns MutableStateFlow(conditionState).asStateFlow()
            }
        } else {
            mockk<ConditionStateMonitor> {
                every { currentState } returns conditionState
                every { states } returns MutableStateFlow(conditionState).asStateFlow()
            }.also(mockConditionStateMonitor::invoke)
        }
        savedStateHandle = if (mockSavedStateHandle == null) {
            mockk(relaxed = true)
        } else {
            mockk<SavedStateHandle> {
                every { get<String>(any()) } returns null
                every { set(any(), any<String>()) } just Runs
            }.also(mockSavedStateHandle::invoke)
        }

        return PreferenceCenterViewModel(
            preferenceCenterId = preferenceCenterId,
            preferenceCenter = preferenceCenter,
            channel = channel,
            contact = contact,
            ioDispatcher = ioDispatcher,
            actionRunner = actionRunner,
            dispatcher = dispatcher,
            conditionMonitor = conditionMonitor,
            savedStateHandle = savedStateHandle
        ).also {
            advanceUntilIdle()
        }
    }
}
