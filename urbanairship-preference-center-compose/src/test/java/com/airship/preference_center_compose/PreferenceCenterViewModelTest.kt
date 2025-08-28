package com.airship.preference_center_compose

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
import com.urbanairship.inputvalidation.AirshipInputValidation
import com.urbanairship.inputvalidation.AirshipInputValidation.Request
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.preferencecenter.ConditionStateMonitor
import com.urbanairship.preferencecenter.PreferenceCenter
import com.urbanairship.preferencecenter.compose.ui.Action
import com.urbanairship.preferencecenter.compose.ui.ContactManagerDialog
import com.urbanairship.preferencecenter.compose.ui.DefaultPreferenceCenterViewModel
import com.urbanairship.preferencecenter.compose.ui.DialogResult
import com.urbanairship.preferencecenter.compose.ui.ViewState
import com.urbanairship.preferencecenter.compose.ui.asPrefCenterItems
import com.urbanairship.preferencecenter.compose.ui.filterByConditions
import com.urbanairship.preferencecenter.compose.ui.item.AlertItem
import com.urbanairship.preferencecenter.compose.ui.item.ContactSubscriptionItem
import com.urbanairship.preferencecenter.compose.ui.item.SectionItem
import com.urbanairship.preferencecenter.data.Button
import com.urbanairship.preferencecenter.data.CommonDisplay
import com.urbanairship.preferencecenter.data.Condition
import com.urbanairship.preferencecenter.data.Condition.OptInStatus.Status
import com.urbanairship.preferencecenter.data.IconDisplay
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.data.Item.ContactSubscriptionGroup.Component
import com.urbanairship.preferencecenter.data.Options
import com.urbanairship.preferencecenter.data.PreferenceCenterConfig
import com.urbanairship.preferencecenter.data.Section
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.coVerifyAll
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
                        ), Item.ChannelSubscription(
                            id = "item-2-id",
                            subscriptionId = SUBSCRIPTION_ID_2,
                            display = CommonDisplay("item-2-title"),
                            conditions = emptyList()
                        )
                    ),
                    conditions = emptyList()
                ), Section.Common(
                    id = "section-2-id",
                    display = CommonDisplay("section-2-title", "section-2-subtitle"),
                    items = listOf(
                        Item.ChannelSubscription(
                            id = "item-3-id",
                            subscriptionId = SUBSCRIPTION_ID_3,
                            display = CommonDisplay("item-3-title", "item-3-subtitle"),
                            conditions = emptyList()
                        ), Item.ChannelSubscription(
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
                    items = listOf(
                        CONTACT_SUBSCRIPTION_GROUP_ITEM_3,
                        CONTACT_SUBSCRIPTION_GROUP_ITEM_4
                    ),
                    conditions = emptyList()
                ),
            )
        )

        private val ALERT_CONDITIONS_CONFIG = PreferenceCenterConfig(
            id = PREF_CENTER_ID,
            display = CommonDisplay(PREF_CENTER_TITLE, PREF_CENTER_SUBTITLE),
            sections = listOf(
                Section.Common(
                    id = "section-1", display = CommonDisplay.EMPTY, items = listOf(
                        Item.Alert(
                            id = "alert",
                            iconDisplay = IconDisplay("icon-uri", "name", "description"),
                            button = Button(
                                "button",
                                null,
                                mapOf("foo" to JsonValue.wrap("bar"))
                            ),
                            conditions = emptyList()
                        )
                    ), conditions = listOf(Condition.OptInStatus(status = Status.OPT_OUT))
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
                assertEquals(awaitItem(), ViewState.Loading)
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
                assertEquals(awaitItem(), ViewState.Loading)
                handle(Action.Refresh)

                val initialState = awaitItem()
                assertTrue(initialState is ViewState.Content)
                initialState as ViewState.Content

                assertEquals(initialState.contactSubscriptions, expectedContact)
                assertEquals(initialState.channelSubscriptions, setOf(SUBSCRIPTION_ID_1, SUBSCRIPTION_ID_2, SUBSCRIPTION_ID_3))

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
                assertEquals(awaitItem(), ViewState.Loading)
                assertTrue(awaitItem() is ViewState.Content)

                cancelAndIgnoreRemainingEvents()
            }
            coVerify {
                contact.namedUserIdFlow
                channel.subscriptions
                contact.subscriptions
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
                assertEquals(awaitItem(), ViewState.Loading)
                assertTrue(awaitItem() is ViewState.Content)

                namedUserIdFlow.emit("some other user")

                assertEquals(awaitItem(), ViewState.Loading)
                assertTrue(awaitItem() is ViewState.Content)

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
                assertEquals(awaitItem(), ViewState.Loading)
                assertTrue(awaitItem() is ViewState.Content)

                cancelAndIgnoreRemainingEvents()
            }
            coVerify {
                contact.namedUserIdFlow
                contact.subscriptions
                contact.channelContacts
            }
            coVerify(exactly = 0) { channel.subscriptions }
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
                assertEquals(awaitItem(), ViewState.Loading)
                assertTrue(awaitItem() is ViewState.Content)

                cancelAndIgnoreRemainingEvents()
            }
            coVerify {
                contact.namedUserIdFlow
                channel.subscriptions
                contact.channelContacts
            }
            coVerify(exactly = 0) { contact.subscriptions }
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
                assertEquals(awaitItem(), ViewState.Loading)
                assertTrue(awaitItem() is ViewState.Content)

                cancelAndIgnoreRemainingEvents()
            }
            coVerify {
                contact.namedUserIdFlow
                channel.subscriptions
                contact.subscriptions
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
                assertEquals(awaitItem(), ViewState.Loading)

                handle(Action.Refresh)
                assertTrue(awaitItem() is ViewState.Content)

                handle(Action.PreferenceItemChanged(item, isEnabled = true))
                val state = awaitItem()
                assertTrue(state is ViewState.Content)

                state as ViewState.Content
                assertEquals(state.channelSubscriptions, setOf(SUBSCRIPTION_ID_1))
                cancelAndIgnoreRemainingEvents()
            }

            coVerifyOrder {
                contact.namedUserIdFlow
                channel.subscriptions

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
                assertEquals(awaitItem(), ViewState.Loading)

                handle(Action.Refresh)
                val initialState = awaitItem()
                assertTrue(initialState is ViewState.Content)
                initialState as ViewState.Content
                assertEquals(initialState.channelSubscriptions, setOf(SUBSCRIPTION_ID_1, SUBSCRIPTION_ID_2))

                handle(Action.PreferenceItemChanged(item, isEnabled = false))
                val state = awaitItem()
                assertTrue(state is ViewState.Content)

                state as ViewState.Content
                assertEquals(state.channelSubscriptions, setOf(SUBSCRIPTION_ID_1))
                cancel()
            }

            coVerifyOrder {
                channel.subscriptions
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
                assertEquals(awaitItem(), ViewState.Loading)

                handle(Action.Refresh)
                assertTrue(awaitItem() is ViewState.Content)

                handle(Action.ScopedPreferenceItemChanged(item, item.scopes, isEnabled = true))
                val state = awaitItem()
                assertTrue(state is ViewState.Content)

                state as ViewState.Content
                assertEquals(state.contactSubscriptions, expectedSubscriptions)
                cancelAndIgnoreRemainingEvents()
            }

            coVerifyOrder {
                contact.namedUserIdFlow
                contact.subscriptions

                contact.editSubscriptionLists()
                editor.mutate(item.subscriptionId, item.scopes, true)
                editor.apply()
            }
            coVerify(exactly = 0) {
                channel.subscriptions
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
                assertEquals(awaitItem(), ViewState.Loading)

                handle(Action.Refresh)
                val initialState = awaitItem()
                assertTrue(initialState is ViewState.Content)
                initialState as ViewState.Content
                assertEquals(initialState.contactSubscriptions, initialSubscriptions)

                handle(Action.ScopedPreferenceItemChanged(item, item.scopes, isEnabled = false))
                val state = awaitItem()
                assertTrue(state is ViewState.Content)

                state as ViewState.Content
                assertEquals(state.contactSubscriptions, expectedSubscriptions)

                cancelAndIgnoreRemainingEvents()
            }

            coVerifyOrder {
                contact.namedUserIdFlow
                contact.subscriptions

                contact.editSubscriptionLists()
                editor.mutate(item.subscriptionId, item.scopes, false)
                editor.apply()
            }
            coVerify(exactly = 0) {
                channel.subscriptions
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
                assertEquals(awaitItem(), ViewState.Loading)

                handle(Action.Refresh)
                assertTrue(awaitItem() is ViewState.Content)

                handle(Action.ScopedPreferenceItemChanged(item, component.scopes, isEnabled = true))
                val state = awaitItem()
                assertTrue(state is ViewState.Content)

                state as ViewState.Content
                assertEquals(state.contactSubscriptions, expectedSubscriptions)
                cancel()
            }

            coVerifyOrder {
                contact.namedUserIdFlow
                contact.subscriptions

                contact.editSubscriptionLists()
                editor.mutate(item.subscriptionId, component.scopes, true)
                editor.apply()
            }
            coVerify(exactly = 0) {
                channel.subscriptions
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
                assertEquals(awaitItem(), ViewState.Loading)

                handle(Action.Refresh)
                val initialState = awaitItem()
                assertTrue(initialState is ViewState.Content)
                initialState as ViewState.Content
                assertEquals(initialState.contactSubscriptions, initialSubscriptions)

                handle(Action.ScopedPreferenceItemChanged(item, unsubscribeScopes, isEnabled = false))
                val state = awaitItem()
                assertTrue(state is ViewState.Content)

                state as ViewState.Content
                assertEquals(state.contactSubscriptions, expectedSubscriptions)

                cancel()
            }

            coVerifyOrder {
                contact.namedUserIdFlow
                contact.subscriptions

                contact.editSubscriptionLists()
                editor.mutate(item.subscriptionId, unsubscribeScopes, false)
                editor.apply()
            }
            coVerify(exactly = 0) {
                channel.subscriptions
                channel.editSubscriptionLists()
            }
            confirmVerified(channel, contact, editor)
        }
    }

    //
    // Condition State Updates
    //
    @Test
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
                assertEquals(awaitItem(), ViewState.Loading)

                handle(Action.Refresh)

                val initialContent = awaitItem()
                // Ensure we received an initial content state
                assertTrue(initialContent is ViewState.Content)
                initialContent as ViewState.Content
                assertEquals(initialContent.conditionState, initialConditions)
                // Config should contain the entire unfiltered config
                assertEquals(initialContent.config, ALERT_CONDITIONS_CONFIG)

                // List items should only contain the alert item when notifications are opted out
                assertTrue(initialContent.listItems.size == 1)
                assertTrue(initialContent.listItems.first() is AlertItem)

                // Update condition state to fake a notification opt-in
                stateFlow.value = updatedConditions

                val updatedContent = awaitItem()
                // Sanity check
                assertTrue(updatedContent is ViewState.Content)
                updatedContent as ViewState.Content
                assertEquals(updatedContent.conditionState, updatedConditions)

                // List items should contain a section with two subscription pref items
                assertEquals(updatedContent.listItems.size, 3)
                assertTrue(updatedContent.listItems[0] is SectionItem)
                assertTrue(updatedContent.listItems[1] is ContactSubscriptionItem)
                assertTrue(updatedContent.listItems[2] is ContactSubscriptionItem)
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
                assertEquals(awaitItem(), ViewState.Loading)
                handle(Action.Refresh)
                assertTrue(awaitItem() is ViewState.Content)
                ensureAllEventsConsumed()
            }

            displayDialog.test {
                skipItems(1)
                handle(Action.RequestAddChannel(mockItem))
                assertEquals(awaitItem(), ContactManagerDialog.Add(mockItem))
                ensureAllEventsConsumed()
            }
        }
    }

    @Test
    public fun testConfirmAddContactChannel(): TestResult = runTest {
        val mockMessage: Item.ContactManagement.ActionableMessage = mockk()
        val mockItem: Item.ContactManagement = mockk {
            every { addPrompt } returns mockk {
                every { prompt } returns mockk {
                    every { onSubmit } returns mockMessage
                }
            }
        }
        val dialogResult: DialogResult = mockk()

        viewModel().run {
            states.test {
                assertEquals(awaitItem(), ViewState.Loading)
                handle(Action.Refresh)
                assertTrue(awaitItem() is ViewState.Content)
                ensureAllEventsConsumed()
            }

            displayDialog.test {
                skipItems(1)

                handle(Action.ConfirmAddChannel(mockItem, dialogResult))
                assertEquals(awaitItem(), ContactManagerDialog.ConfirmAdd(mockMessage))
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
                assertEquals(awaitItem(), ViewState.Loading)
                handle(Action.Refresh)
                assertTrue(awaitItem() is ViewState.Content)
                ensureAllEventsConsumed()
            }

            displayDialog.test {
                skipItems(1)

                handle(Action.RequestRemoveChannel(mockItem, contactChannel))
                assertEquals(awaitItem(), ContactManagerDialog.Remove(mockItem, contactChannel))
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
                assertEquals(awaitItem(), ViewState.Loading)
                handle(Action.Refresh)
                val initialState = awaitItem() as ViewState.Content
                assertTrue(initialState.contactChannels.isEmpty())
                ensureAllEventsConsumed()
            }

            displayDialog.test {
                skipItems(1)

                handle(Action.RegisterChannel.Sms(mockItem, msisdn, senderId))
                advanceUntilIdle()
                assertTrue(awaitItem() is ContactManagerDialog.ConfirmAdd)

                handle(Action.UnregisterChannel(contactChannel))
                assertNull(awaitItem())
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
                assertEquals(awaitItem(), ViewState.Loading)
                handle(Action.Refresh)
                val initialState = awaitItem() as ViewState.Content
                assertTrue(initialState.contactChannels.isEmpty())
                ensureAllEventsConsumed()
            }

            displayDialog.test {
                assertNull(awaitItem())

                handle(Action.RegisterChannel.Email(mockItem, address))
                advanceUntilIdle()
                assertTrue(awaitItem() is ContactManagerDialog.ConfirmAdd)

                handle(Action.UnregisterChannel(contactChannel))
                assertNull(awaitItem())
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
        val address = "5031112222"
        val prefix = "1"
        val senderId = "123456"

        val validator: AirshipInputValidation.Validator = mockk {
            coEvery { validate(any()) } answers {
                when(val request = firstArg<Request>()) {
                    is Request.ValidateEmail -> fail()
                    is Request.ValidateSms -> {
                        assertEquals(address, request.sms.rawInput)
                        when(val option = request.sms.validationOptions) {
                            is Request.Sms.ValidationOptions.Prefix -> fail()
                            is Request.Sms.ValidationOptions.Sender -> {
                                assertEquals(senderId, option.senderId)
                                assertEquals(prefix, option.prefix)
                            }
                        }
                    }
                }

                AirshipInputValidation.Result.Valid(address)
            }
        }

        viewModel().run {
            every { preferenceCenter.inputValidator } returns validator

            states.test {
                assertEquals(awaitItem(), ViewState.Loading)
                handle(Action.Refresh)
                assertTrue(awaitItem() is ViewState.Content)
                ensureAllEventsConsumed()
            }

            displayDialog.test {
                assertNull(awaitItem())

                handle(Action.ValidateSmsChannel(item, address, senderId, prefix))
                assertEquals(awaitItem(), ContactManagerDialog.ConfirmAdd(item.addPrompt.prompt.onSubmit!!))
                ensureAllEventsConsumed()
            }

            coVerify { validator.validate(any()) }
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

        val validator: AirshipInputValidation.Validator = mockk {
            coEvery { validate(any()) } answers {
                when(val request = firstArg<Request>()) {
                    is Request.ValidateEmail -> fail()
                    is Request.ValidateSms -> {
                        assertEquals(address, request.sms.rawInput)
                        when(val option = request.sms.validationOptions) {
                            is Request.Sms.ValidationOptions.Prefix -> fail()
                            is Request.Sms.ValidationOptions.Sender -> {
                                assertEquals(senderId, option.senderId)
                                assertNull(option.prefix)
                            }
                        }
                    }
                }

                AirshipInputValidation.Result.Invalid
            }
        }

        viewModel().run {

            every { preferenceCenter.inputValidator } returns validator

            states.test {
                assertEquals(awaitItem(), ViewState.Loading)
                handle(Action.Refresh)
                assertTrue(awaitItem() is ViewState.Content)
                ensureAllEventsConsumed()
            }

            displayDialog.test {
                assertNull(awaitItem())

                errors.test {
                    assertNull(awaitItem())
                    handle(Action.ValidateSmsChannel(item, address, senderId))
                    assertEquals(awaitItem(), invalidMessage)
                    ensureAllEventsConsumed()
                }

                ensureAllEventsConsumed()
            }
        }

        coVerify { validator.validate(any()) }
    }

    @Test
    public fun testResendChannelVerification(): TestResult = runTest {
        val item: Item.ContactManagement = mockk {
            every { platform } returns Item.ContactManagement.Platform.Email(
                registrationOptions = mockk {
                    every { properties } returns null
                    every { resendOptions } returns mockk {
                        every { interval } returns 3
                        every { onSuccess } returns mockk(relaxed = true)
                    }
                }
            )
        }
        val contactChannel: ContactChannel = mockk { }

        viewModel(
            mockContact = { coJustRun { resendDoubleOptIn(any()) } }
        ).run {
            states.test {
                assertEquals(awaitItem(), ViewState.Loading)
                handle(Action.Refresh)
                assertTrue(awaitItem() is ViewState.Content)
                ensureAllEventsConsumed()
            }

            displayDialog.test {
                assertNull(awaitItem())

                handle(Action.ResendChannelVerification(item, contactChannel))
                assertEquals(awaitItem(), ContactManagerDialog.ResendConfirmation(item.platform.resendOptions.onSuccess!!))
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
                assertEquals(awaitItem(), ViewState.Loading)
                contactChannelsFlow.emit(Result.success(initialChannels))

                handle(Action.Refresh)
                val initialState = awaitItem() as ViewState.Content
                assertEquals(initialState.contactChannels, initialChannels.toSet())

                contactChannelsFlow.emit(Result.success(updatedChannels1))
                val updatedState1 = awaitItem() as ViewState.Content
                assertEquals(updatedState1.contactChannels, updatedChannels1.toSet())

                contactChannelsFlow.emit(Result.success(updatedChannels2))
                val updatedState2 = awaitItem() as ViewState.Content
                assertEquals(updatedState2.contactChannels, updatedChannels2.toSet())

                assertTrue(cancelAndConsumeRemainingEvents().isEmpty())
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
        val initialChannelState = ViewState.Content.ContactChannelState(
            showResendButton = true,
            showPendingButton = true
        )

        val updatedChannelState = ViewState.Content.ContactChannelState(
            showResendButton = false,
            showPendingButton = false
        )

        viewModel(
            config = config,
            contactChannels = listOf(contactChannel),
            dispatcher = testDispatcher
        ).run {
            states.test {
                assertEquals(awaitItem(), ViewState.Loading)
                handle(Action.Refresh)

                val initialState = awaitItem() as ViewState.Content
                assertEquals(initialState.contactChannels, setOf(contactChannel))
                assertEquals(initialState.contactChannelState, mapOf(contactChannel to initialChannelState))

                handle(Action.UpdateContactChannel(contactChannel, updatedChannelState))

                val updatedState = awaitItem() as ViewState.Content
                assertEquals(updatedState.contactChannels, setOf(contactChannel))
                assertEquals(updatedState.contactChannelState, mapOf(contactChannel to updatedChannelState))

                ensureAllEventsConsumed()
            }
        }
        coVerifyAll {
            contact.namedUserIdFlow
            contact.subscriptions
            channel.subscriptions
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
    ): DefaultPreferenceCenterViewModel {
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
                coEvery { subscriptions } answers {
                    flowOf(Result.success(channelSubscriptions))
                }
            }.also(mockChannel::invoke)
        }
        contact = if (mockContact == null) {
            mockk(relaxUnitFun = true)
        } else {
            mockk<Contact>(relaxed = true) {
                coEvery { subscriptions } answers {
                    flowOf(Result.success(contactSubscriptions))
                }
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

        return DefaultPreferenceCenterViewModel(
            preferenceCenterId = preferenceCenterId,
            preferenceCenter = preferenceCenter,
            channel = channel,
            contact = contact,
            ioDispatcher = ioDispatcher,
            actionRunner = actionRunner,
            dispatcher = dispatcher,
            conditionMonitor = conditionMonitor,
        ).also {
            advanceUntilIdle()
        }
    }
}
