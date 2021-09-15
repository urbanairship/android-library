package com.urbanairship.preferencecenter.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.urbanairship.PendingResult
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.channel.SubscriptionListEditor
import com.urbanairship.preferencecenter.PreferenceCenter
import com.urbanairship.preferencecenter.data.CommonDisplay
import com.urbanairship.preferencecenter.data.Item
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
import org.mockito.kotlin.whenever

@OptIn(
    ExperimentalCoroutinesApi::class,
    ExperimentalTime::class
)
@RunWith(AndroidJUnit4::class)
class PreferenceCenterViewModelTest {
    companion object {
        private const val PREF_CENTER_ID = "pref-center-id"
        private const val PREF_CENTER_TITLE = "Preference Center Title"
        private const val PREF_CENTER_SUBTITLE = "Preference Center Subtitle"

        private const val SUBSCRIPTION_ID_1 = "item-1-subscription-id"
        private const val SUBSCRIPTION_ID_2 = "item-2-subscription-id"
        private const val SUBSCRIPTION_ID_3 = "item-3-subscription-id"
        private const val SUBSCRIPTION_ID_4 = "item-4-subscription-id"

        private val CONFIG = PreferenceCenterConfig(
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
    }

    private lateinit var testDispatcher: TestCoroutineDispatcher
    private lateinit var preferenceCenter: PreferenceCenter
    private lateinit var channel: AirshipChannel

    @Before
    fun setUp() {
        testDispatcher = TestCoroutineDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }

    @Test
    fun emitsInitialLoadingState() = runBlocking {
        viewModel(mockPreferenceCenter = null, mockChannel = null).run {
            states.test {
                assertThat(expectItem()).isEqualTo(State.Loading)
                cancel()
            }
        }
    }

    @Test
    fun handlesRefreshAction() = runBlocking {
        viewModel().run {
            states.test {
                handle(Action.Refresh)

                assertThat(expectItem()).isEqualTo(State.Loading)
                assertThat(expectItem()).isInstanceOf(State.Content::class.java)
                cancel()
            }
        }
    }

    @Test
    fun handlesPreferenceItemChangedActionSubscribe() = runBlocking {
        val editor = mock<SubscriptionListEditor> {
            on { mutate(any(), any()) } doReturn this.mock
        }

        viewModel(
            mockChannel = { whenever(editSubscriptionLists()) doReturn editor }
        ).run {
            val item = Item.ChannelSubscription("id", SUBSCRIPTION_ID_1, CommonDisplay.EMPTY)

            states.test {
                assertThat(expectItem()).isEqualTo(State.Loading)

                handle(Action.Refresh)
                assertThat(expectItem()).isInstanceOf(State.Content::class.java)

                handle(Action.PreferenceItemChanged(item, isEnabled = true))
                val state = expectItem()
                assertThat(state).isInstanceOf(State.Content::class.java)

                state as State.Content
                assertThat(state.subscriptions).containsExactly(SUBSCRIPTION_ID_1)
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
    fun handlesPreferenceItemChangedActionUnsubscribe() = runBlocking {
        val editor = mock<SubscriptionListEditor> {
            on { mutate(any(), any()) } doReturn this.mock
        }

        viewModel(
            subscriptions = setOf(SUBSCRIPTION_ID_1, SUBSCRIPTION_ID_2),
            mockChannel = { whenever(editSubscriptionLists()) doReturn editor }
        ).run {
            val item = Item.ChannelSubscription("id", SUBSCRIPTION_ID_2, CommonDisplay.EMPTY)

            states.test {
                assertThat(expectItem()).isEqualTo(State.Loading)

                handle(Action.Refresh)
                val initialState = expectItem()
                assertThat(initialState).isInstanceOf(State.Content::class.java)
                initialState as State.Content
                assertThat(initialState.subscriptions).containsExactly(SUBSCRIPTION_ID_1, SUBSCRIPTION_ID_2)

                handle(Action.PreferenceItemChanged(item, isEnabled = false))
                val state = expectItem()
                assertThat(state).isInstanceOf(State.Content::class.java)

                state as State.Content
                assertThat(state.subscriptions).containsExactly(SUBSCRIPTION_ID_1)
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

    private fun viewModel(
        config: PreferenceCenterConfig = CONFIG,
        subscriptions: Set<String> = emptySet(),
        preferenceCenterId: String = config.id,
        ioDispatcher: CoroutineDispatcher = testDispatcher,
        mockPreferenceCenter: (PreferenceCenter.() -> Unit)? = {},
        mockChannel: (AirshipChannel.() -> Unit)? = {}
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
                on { getSubscriptionLists(true) } doReturn pendingResultOf(subscriptions)
            }.also(mockChannel::invoke)
        }

        return PreferenceCenterViewModel(
            preferenceCenterId = preferenceCenterId,
            preferenceCenter = preferenceCenter,
            channel = channel,
            ioDispatcher = ioDispatcher
        )
    }
}

private fun <T : Any> pendingResultOf(result: T): PendingResult<T> =
    PendingResult<T>().apply { this.result = result }
