package com.urbanairship.preferencecenter.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.urbanairship.PendingResult
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.channel.SubscriptionListEditor
import com.urbanairship.contacts.Contact
import com.urbanairship.contacts.Scope
import com.urbanairship.contacts.ScopedSubscriptionListEditor
import com.urbanairship.preferencecenter.PreferenceCenter
import com.urbanairship.preferencecenter.data.CommonDisplay
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.data.Item.ContactSubscriptionGroup.Component
import com.urbanairship.preferencecenter.data.PreferenceCenterConfig
import com.urbanairship.preferencecenter.data.Section
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.Action
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.State
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.LooperMode

@OptIn(
    ExperimentalCoroutinesApi::class,
    ExperimentalTime::class
)
@RunWith(AndroidJUnit4::class)
@LooperMode(LooperMode.Mode.LEGACY)
class PreferenceCenterViewModelTest {
    companion object {
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
                            display = CommonDisplay("item-1-title", "item-1-subtitle")
                        ),
                        Item.ChannelSubscription(
                            id = "item-2-id",
                            subscriptionId = SUBSCRIPTION_ID_2,
                            display = CommonDisplay("item-2-title")
                        )
                    )
                ),
                Section.Common(
                    id = "section-2-id",
                    display = CommonDisplay("section-2-title", "section-2-subtitle"),
                    items = listOf(
                        Item.ChannelSubscription(
                            id = "item-3-id",
                            subscriptionId = SUBSCRIPTION_ID_3,
                            display = CommonDisplay("item-3-title", "item-3-subtitle")
                        ),
                        Item.ChannelSubscription(
                            id = "item-4-id",
                            subscriptionId = SUBSCRIPTION_ID_4,
                            display = CommonDisplay("item-4-title", "item-4-subtitle")
                        )
                    )
                )
            )
        )

        private val CONTACT_SUBSCRIPTION_ITEM_1 = Item.ContactSubscription(
            id = "item-1-id",
            subscriptionId = SUBSCRIPTION_ID_1,
            display = CommonDisplay("item-1-title", "item-1-subtitle"),
            scopes = setOf(Scope.APP, Scope.EMAIL)
        )
        private val CONTACT_SUBSCRIPTION_ITEM_2 = Item.ContactSubscription(
            id = "item-2-id",
            subscriptionId = SUBSCRIPTION_ID_2,
            display = CommonDisplay("item-2-title"),
            scopes = setOf(Scope.SMS)
        )
        private val CONTACT_SUBSCRIPTION_GROUP_ITEM_3 = Item.ContactSubscriptionGroup(
            id = "item-3-id",
            subscriptionId = SUBSCRIPTION_ID_3,
            display = CommonDisplay("item-3-title", "item-3-subtitle"),
            components = listOf(
                Component(scopes = setOf(Scope.APP), display = CommonDisplay("APP")),
                Component(scopes = setOf(Scope.EMAIL), display = CommonDisplay("EMAIL")),
                Component(scopes = setOf(Scope.SMS), display = CommonDisplay("SMS")),
            )
        )
        private val CONTACT_SUBSCRIPTION_GROUP_ITEM_4 = Item.ContactSubscriptionGroup(
            id = "item-4-id",
            subscriptionId = SUBSCRIPTION_ID_4,
            display = CommonDisplay("item-4-title", "item-4-subtitle"),
            components = listOf(
                Component(scopes = setOf(Scope.APP), display = CommonDisplay("APP")),
                Component(scopes = setOf(Scope.EMAIL), display = CommonDisplay("EMAIL")),
                Component(scopes = setOf(Scope.SMS), display = CommonDisplay("SMS")),
            )
        )

        private val CONTACT_SUBSCRIPTION_CONFIG = PreferenceCenterConfig(
            id = PREF_CENTER_ID,
            display = CommonDisplay(PREF_CENTER_TITLE, PREF_CENTER_SUBTITLE),
            sections = listOf(
                Section.SectionBreak(
                    id = "section-break-1-id",
                    display = CommonDisplay("section-break-1-label")
                ),
                Section.Common(
                    id = "section-1-id",
                    display = CommonDisplay("section-1-title", "section-1-subtitle"),
                    items = listOf(CONTACT_SUBSCRIPTION_ITEM_1, CONTACT_SUBSCRIPTION_ITEM_2)
                ),
                Section.Common(
                    id = "section-2-id",
                    display = CommonDisplay("section-2-title", "section-2-subtitle"),
                    items = listOf(CONTACT_SUBSCRIPTION_GROUP_ITEM_3, CONTACT_SUBSCRIPTION_GROUP_ITEM_4)
                )
            )
        )
    }

    private lateinit var testDispatcher: TestCoroutineDispatcher
    private lateinit var preferenceCenter: PreferenceCenter
    private lateinit var channel: AirshipChannel
    private lateinit var contact: Contact

    @Before
    fun setUp() {
        testDispatcher = TestCoroutineDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        testDispatcher.cleanupTestCoroutines()
        Dispatchers.resetMain()
    }

    @Test
    fun emitsInitialLoadingState() = runBlocking {
        viewModel(mockPreferenceCenter = null, mockChannel = null).run {
            states.test {
                assertThat(awaitItem()).isEqualTo(State.Loading)
                cancel()
            }
        }
    }

    @Test
    fun handlesRefreshAction() = runBlocking {
        viewModel(
            config = mockConfig(
                hasChannelSubscriptions = true,
                hasContactSubscriptions = true
            )
        ).run {
            states.test {
                handle(Action.Refresh)
                assertThat(awaitItem()).isEqualTo(State.Loading)
                assertThat(awaitItem()).isInstanceOf(State.Content::class.java)

                verify(channel).getSubscriptionLists(true)
                verify(contact).getSubscriptionLists(true)

                cancel()
            }
        }
    }

    @Test
    fun handlesRefreshActionWithoutChannelSubscriptions() = runBlocking {
        viewModel(
            config = mockConfig(
                hasChannelSubscriptions = false,
                hasContactSubscriptions = true
            )
        ).run {
            states.test {
                handle(Action.Refresh)
                assertThat(awaitItem()).isEqualTo(State.Loading)
                assertThat(awaitItem()).isInstanceOf(State.Content::class.java)

                verify(channel, never()).getSubscriptionLists(any())
                verify(contact).getSubscriptionLists(true)

                cancel()
            }
        }
    }

    @Test
    fun handlesRefreshActionWithoutContactSubscriptions() = runBlocking {
        viewModel(
            config = mockConfig(
                hasChannelSubscriptions = true,
                hasContactSubscriptions = false
            )
        ).run {
            states.test {
                handle(Action.Refresh)
                assertThat(awaitItem()).isEqualTo(State.Loading)
                assertThat(awaitItem()).isInstanceOf(State.Content::class.java)

                verify(channel).getSubscriptionLists(true)
                verify(contact, never()).getSubscriptionLists(any())

                cancel()
            }
        }
    }

    //
    // Channel Subscriptions
    //

    @Test
    fun handlesChannelPreferenceItemChangedActionSubscribe() = runBlocking {
        val editor = mock<SubscriptionListEditor> {
            on { mutate(any(), any()) } doReturn this.mock
        }

        viewModel(
            mockChannel = { whenever(editSubscriptionLists()) doReturn editor }
        ).run {
            val item = Item.ChannelSubscription("id", SUBSCRIPTION_ID_1, CommonDisplay.EMPTY)

            states.test {
                assertThat(awaitItem()).isEqualTo(State.Loading)

                handle(Action.Refresh)
                assertThat(awaitItem()).isInstanceOf(State.Content::class.java)

                handle(Action.PreferenceItemChanged(item, isEnabled = true))
                val state = awaitItem()
                assertThat(state).isInstanceOf(State.Content::class.java)

                state as State.Content
                assertThat(state.channelSubscriptions).containsExactly(SUBSCRIPTION_ID_1)
                cancel()
            }

            inOrder(channel, editor) {
                verify(channel).getSubscriptionLists(true)
                verify(channel).editSubscriptionLists()
                verify(editor).mutate(item.subscriptionId, true)
                verify(editor).apply()

                Mockito.verifyNoMoreInteractions(channel, editor)
            }
        }
    }

    @Test
    fun handlesChannelPreferenceItemChangedActionUnsubscribe() = runBlocking {
        val editor = mock<SubscriptionListEditor> {
            on { mutate(any(), any()) } doReturn this.mock
        }

        viewModel(
            channelSubscriptions = setOf(SUBSCRIPTION_ID_1, SUBSCRIPTION_ID_2),
            mockChannel = { whenever(editSubscriptionLists()) doReturn editor }
        ).run {
            val item = Item.ChannelSubscription("id", SUBSCRIPTION_ID_2, CommonDisplay.EMPTY)

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

            inOrder(channel, editor) {
                verify(channel).getSubscriptionLists(true)
                verify(channel).editSubscriptionLists()
                verify(editor).mutate(item.subscriptionId, false)
                verify(editor).apply()

                Mockito.verifyNoMoreInteractions(channel, editor)
            }
        }
    }

    //
    // Contact Subscriptions
    //

    @Test
    fun handlesContactPreferenceItemChangedActionSubscribe() = runBlocking {
        val editor = mock<ScopedSubscriptionListEditor> {
            on { mutate(any(), any(), any()) } doReturn this.mock
        }

        val expectedSubscriptions = mapOf(
            SUBSCRIPTION_ID_1 to CONTACT_SUBSCRIPTION_ITEM_1.scopes
        )

        viewModel(
            config = CONTACT_SUBSCRIPTION_CONFIG,
            mockContact = { whenever(editSubscriptionLists()) doReturn editor }
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
                cancel()
            }

            inOrder(channel, contact, editor) {
                verify(contact).getSubscriptionLists(true)
                verify(contact).editSubscriptionLists()
                verify(editor).mutate(item.subscriptionId, item.scopes, true)
                verify(editor).apply()

                Mockito.verifyNoMoreInteractions(channel, contact, editor)
                verify(channel, never()).getSubscriptionLists(any())
                verify(channel, never()).editSubscriptionLists()
            }
        }
    }

    @Test
    fun handlesContactPreferenceItemChangedActionUnsubscribe() = runBlocking {
        val editor = mock<ScopedSubscriptionListEditor> {
            on { mutate(any(), any(), any()) } doReturn this.mock
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
            mockContact = {
                whenever(editSubscriptionLists()) doReturn editor
            }
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

                cancel()
            }

            inOrder(channel, contact, editor) {
                verify(contact).getSubscriptionLists(true)
                verify(contact).editSubscriptionLists()
                verify(editor).mutate(item.subscriptionId, item.scopes, false)
                verify(editor).apply()

                verify(channel, never()).editSubscriptionLists()
                verify(channel, never()).getSubscriptionLists(any())

                Mockito.verifyNoMoreInteractions(channel, contact, editor)
            }
        }
    }

    //
    // Channel Subscription Groups
    //

    @Test
    fun handlesContactPreferenceGroupChangedActionSubscribe() = runBlocking {
        val editor = mock<ScopedSubscriptionListEditor> {
            on { mutate(any(), any(), any()) } doReturn this.mock
        }

        val item = CONTACT_SUBSCRIPTION_GROUP_ITEM_3
        val component = item.components.first()

        val expectedSubscriptions = mapOf(
            item.subscriptionId to component.scopes
        )

        viewModel(
            config = CONTACT_SUBSCRIPTION_CONFIG,
            mockContact = { whenever(editSubscriptionLists()) doReturn editor }
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

            inOrder(channel, contact, editor) {
                verify(contact).getSubscriptionLists(true)
                verify(contact).editSubscriptionLists()
                verify(editor).mutate(item.subscriptionId, component.scopes, true)
                verify(editor).apply()

                Mockito.verifyNoMoreInteractions(channel, contact, editor)
                verify(channel, never()).getSubscriptionLists(any())
                verify(channel, never()).editSubscriptionLists()
            }
        }
    }

    @Test
    fun handlesContactPreferenceGroupChangedActionUnsubscribe() = runBlocking {
        val editor = mock<ScopedSubscriptionListEditor> {
            on { mutate(any(), any(), any()) } doReturn this.mock
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
            mockContact = {
                whenever(editSubscriptionLists()) doReturn editor
            }
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

            inOrder(channel, contact, editor) {
                verify(contact).getSubscriptionLists(true)
                verify(contact).editSubscriptionLists()
                verify(editor).mutate(item.subscriptionId, unsubscribeScopes, false)
                verify(editor).apply()

                verify(channel, never()).getSubscriptionLists(any())
                verify(channel, never()).editSubscriptionLists()

                Mockito.verifyNoMoreInteractions(channel, contact, editor)
            }
        }
    }

    private fun mockConfig(
        hasChannelSubscriptions: Boolean = false,
        hasContactSubscriptions: Boolean = false
    ) = mock<PreferenceCenterConfig> {
        on { this.id } doReturn PREF_CENTER_ID
        on { this.display } doReturn CommonDisplay("name", "description")
        on { this.hasChannelSubscriptions } doReturn hasChannelSubscriptions
        on { this.hasContactSubscriptions } doReturn hasContactSubscriptions
        on { this.asPrefCenterItems() } doReturn emptyList()
    }

    private fun viewModel(
        config: PreferenceCenterConfig = CHANNEL_SUBSCRIPTION_CONFIG,
        channelSubscriptions: Set<String> = emptySet(),
        contactSubscriptions: Map<String, Set<Scope>> = emptyMap(),
        preferenceCenterId: String = config.id,
        ioDispatcher: CoroutineDispatcher = testDispatcher,
        mockPreferenceCenter: (PreferenceCenter.() -> Unit)? = {},
        mockChannel: (AirshipChannel.() -> Unit)? = {},
        mockContact: (Contact.() -> Unit)? = {}
    ): PreferenceCenterViewModel {
        preferenceCenter = if (mockPreferenceCenter == null) {
            mock {}
        } else {
            mock<PreferenceCenter> {
                on { getConfig(preferenceCenterId) } doReturn pendingResultOf(config)
            }.also(mockPreferenceCenter::invoke)
        }
        channel = if (mockChannel == null) {
            mock {}
        } else {
            mock<AirshipChannel> {
                on { getSubscriptionLists(true) } doReturn pendingResultOf(channelSubscriptions)
            }.also(mockChannel::invoke)
        }
        contact = if (mockContact == null) {
            mock {}
        } else {
            mock<Contact> {
                on { getSubscriptionLists(true) } doReturn pendingResultOf(contactSubscriptions)
            }.also(mockContact::invoke)
        }

        return PreferenceCenterViewModel(
            preferenceCenterId = preferenceCenterId,
            preferenceCenter = preferenceCenter,
            channel = channel,
            contact = contact,
            ioDispatcher = ioDispatcher
        )
    }
}

private fun <T : Any> pendingResultOf(result: T): PendingResult<T> =
    PendingResult<T>().apply { this.result = result }
