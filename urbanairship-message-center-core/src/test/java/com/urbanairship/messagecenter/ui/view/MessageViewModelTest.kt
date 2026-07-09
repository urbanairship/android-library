/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter.ui.view

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.urbanairship.android.layout.LayoutDataStorage
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.iam.content.AirshipLayout
import com.urbanairship.json.JsonValue
import com.urbanairship.messagecenter.Inbox
import com.urbanairship.messagecenter.MessageCenterTestUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val NATIVE_CONTENT_TYPE = "application/vnd.urbanairship.thomas+json; version=1"

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class MessageViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun initialStateIsEmpty(): TestResult = runTest {
        val viewModel = MessageViewModel(mockk(relaxUnitFun = true))

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(MessageViewState.Empty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun loadMessageWithHtmlContentEmitsMessageContent(): TestResult = runTest {
        val message = MessageCenterTestUtils.createMessage("message-id")
        val inbox = mockk<Inbox>(relaxUnitFun = true) {
            coEvery { getMessage("message-id") } returns message
        }
        val viewModel = MessageViewModel(inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(MessageViewState.Empty)

            viewModel.loadMessage("message-id")
            assertThat(awaitItem()).isEqualTo(MessageViewState.Loading)

            val content = awaitItem()
            assertThat(content).isInstanceOf(MessageViewState.MessageContent::class.java)
            content as MessageViewState.MessageContent
            assertThat(content.message).isEqualTo(message)
            assertThat(content.content).isEqualTo(MessageViewState.MessageContent.Content.Html)

            cancelAndIgnoreRemainingEvents()
        }
        assertThat(viewModel.currentMessage).isEqualTo(message)
    }

    @Test
    public fun loadMessageWithNativeContentAndStateRestorationPreparesStorage(): TestResult = runTest {
        val message = MessageCenterTestUtils.createMessage("native-id", contentType = NATIVE_CONTENT_TYPE)
        val layout = createLayout(stateRestorationId = "restore-1")
        val storage = mockk<LayoutDataStorage>(relaxed = true)
        val inbox = mockk<Inbox>(relaxUnitFun = true) {
            coEvery { getMessage("native-id") } returns message
            coEvery { loadMessageLayout(message) } returns layout
            every { makeViewStateStorage("native-id") } returns storage
        }
        val viewModel = MessageViewModel(inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(MessageViewState.Empty)

            viewModel.loadMessage("native-id")
            assertThat(awaitItem()).isEqualTo(MessageViewState.Loading)

            val content = awaitItem()
            assertThat(content).isInstanceOf(MessageViewState.MessageContent::class.java)
            content as MessageViewState.MessageContent
            assertThat(content.content).isEqualTo(MessageViewState.MessageContent.Content.Native(layout))

            cancelAndIgnoreRemainingEvents()
        }

        coVerify { storage.prepare("restore-1") }
        assertThat(viewModel.viewStateStorage).isEqualTo(storage)
    }

    @Test
    public fun loadMessageWithNativeContentWithoutStateRestorationClearsStorage(): TestResult = runTest {
        val message = MessageCenterTestUtils.createMessage("native-id", contentType = NATIVE_CONTENT_TYPE)
        val layout = createLayout(stateRestorationId = null)
        val storage = mockk<LayoutDataStorage>(relaxed = true)
        val inbox = mockk<Inbox>(relaxUnitFun = true) {
            coEvery { getMessage("native-id") } returns message
            coEvery { loadMessageLayout(message) } returns layout
            every { makeViewStateStorage("native-id") } returns storage
        }
        val viewModel = MessageViewModel(inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(MessageViewState.Empty)
            viewModel.loadMessage("native-id")
            assertThat(awaitItem()).isEqualTo(MessageViewState.Loading)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify { storage.clear() }
        assertThat(viewModel.viewStateStorage).isNull()
    }

    @Test
    public fun loadMessageWithUnavailableNativeLayoutEmitsError(): TestResult = runTest {
        val message = MessageCenterTestUtils.createMessage("native-id", contentType = NATIVE_CONTENT_TYPE)
        val inbox = mockk<Inbox>(relaxUnitFun = true) {
            coEvery { getMessage("native-id") } returns message
            coEvery { loadMessageLayout(message) } returns null
        }
        val viewModel = MessageViewModel(inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(MessageViewState.Empty)
            viewModel.loadMessage("native-id")
            assertThat(awaitItem()).isEqualTo(MessageViewState.Loading)
            assertThat(awaitItem()).isEqualTo(MessageViewState.Error(MessageViewState.Error.Type.UNAVAILABLE))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun loadMessageFetchesWhenNotFoundLocally(): TestResult = runTest {
        val message = MessageCenterTestUtils.createMessage("message-id")
        val inbox = mockk<Inbox>(relaxUnitFun = true) {
            coEvery { getMessage("message-id") } returnsMany listOf(null, message)
            coEvery { fetchMessages() } returns true
        }
        val viewModel = MessageViewModel(inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(MessageViewState.Empty)
            viewModel.loadMessage("message-id")
            assertThat(awaitItem()).isEqualTo(MessageViewState.Loading)

            val content = awaitItem()
            assertThat(content).isInstanceOf(MessageViewState.MessageContent::class.java)

            cancelAndIgnoreRemainingEvents()
        }
        coVerify { inbox.fetchMessages() }
    }

    @Test
    public fun loadMessageEmitsLoadFailedWhenFetchFails(): TestResult = runTest {
        val inbox = mockk<Inbox>(relaxUnitFun = true) {
            coEvery { getMessage("message-id") } returns null
            coEvery { fetchMessages() } returns false
        }
        val viewModel = MessageViewModel(inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(MessageViewState.Empty)
            viewModel.loadMessage("message-id")
            assertThat(awaitItem()).isEqualTo(MessageViewState.Loading)
            assertThat(awaitItem()).isEqualTo(MessageViewState.Error(MessageViewState.Error.Type.LOAD_FAILED))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun loadMessageEmitsUnavailableWhenStillMissingAfterFetch(): TestResult = runTest {
        val inbox = mockk<Inbox>(relaxUnitFun = true) {
            coEvery { getMessage("message-id") } returns null
            coEvery { fetchMessages() } returns true
        }
        val viewModel = MessageViewModel(inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(MessageViewState.Empty)
            viewModel.loadMessage("message-id")
            assertThat(awaitItem()).isEqualTo(MessageViewState.Loading)
            assertThat(awaitItem()).isEqualTo(MessageViewState.Error(MessageViewState.Error.Type.UNAVAILABLE))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun loadMessageEmitsUnavailableWhenExpired(): TestResult = runTest {
        val expired = MessageCenterTestUtils.createMessage("message-id", expirationDate = Date(0))
        val inbox = mockk<Inbox>(relaxUnitFun = true) {
            coEvery { getMessage("message-id") } returns expired
        }
        val viewModel = MessageViewModel(inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(MessageViewState.Empty)
            viewModel.loadMessage("message-id")
            assertThat(awaitItem()).isEqualTo(MessageViewState.Loading)
            assertThat(awaitItem()).isEqualTo(MessageViewState.Error(MessageViewState.Error.Type.UNAVAILABLE))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun loadMessageWithSameIdSkipsReload(): TestResult = runTest {
        val message = MessageCenterTestUtils.createMessage("message-id")
        val inbox = mockk<Inbox>(relaxUnitFun = true) {
            coEvery { getMessage("message-id") } returns message
        }
        val viewModel = MessageViewModel(inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(MessageViewState.Empty)
            viewModel.loadMessage("message-id")
            assertThat(awaitItem()).isEqualTo(MessageViewState.Loading)
            awaitItem()

            // Loading the same message again should be a no-op: no Loading re-emission.
            viewModel.loadMessage("message-id")
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { inbox.getMessage("message-id") }
    }

    @Test
    public fun clearMessageEmitsEmpty(): TestResult = runTest {
        val message = MessageCenterTestUtils.createMessage("message-id")
        val inbox = mockk<Inbox>(relaxUnitFun = true) {
            coEvery { getMessage("message-id") } returns message
        }
        val viewModel = MessageViewModel(inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(MessageViewState.Empty)
            viewModel.loadMessage("message-id")
            assertThat(awaitItem()).isEqualTo(MessageViewState.Loading)
            awaitItem()

            viewModel.clearMessage()
            assertThat(awaitItem()).isEqualTo(MessageViewState.Empty)

            cancelAndIgnoreRemainingEvents()
        }
        assertThat(viewModel.currentMessage).isNull()
    }

    @Test
    public fun markMessagesReadDelegatesToInbox(): TestResult = runTest {
        val message = MessageCenterTestUtils.createMessage("message-id")
        val inbox = mockk<Inbox>(relaxUnitFun = true)
        val viewModel = MessageViewModel(inbox)

        viewModel.markMessagesRead(message)

        verify { inbox.markMessagesRead(setOf("message-id")) }
    }

    @Test
    public fun deleteMessagesDelegatesToInbox(): TestResult = runTest {
        val message = MessageCenterTestUtils.createMessage("message-id")
        val inbox = mockk<Inbox>(relaxUnitFun = true)
        val viewModel = MessageViewModel(inbox)

        viewModel.deleteMessages(message)

        verify { inbox.deleteMessages(setOf("message-id")) }
    }

    @Test
    public fun makeAnalyticsDelegatesToInbox(): TestResult = runTest {
        val message = MessageCenterTestUtils.createMessage("message-id")
        val listener = mockk<ThomasListenerInterface>()
        val onDismiss = {}
        val inbox = mockk<Inbox>(relaxUnitFun = true) {
            every { makeNativeMessageAnalytics(message, onDismiss) } returns listener
        }
        val viewModel = MessageViewModel(inbox)

        val result = viewModel.makeAnalytics(message, onDismiss)

        assertThat(result).isEqualTo(listener)
        verify { inbox.makeNativeMessageAnalytics(message, onDismiss) }
    }

    @Test
    public fun subscribeForMessageUpdatesReloadsCurrentMessage(): TestResult = runTest {
        // inboxUpdated doesn't route through Loading, and a StateFlow won't re-emit an
        // equal value, so the refreshed message must differ from the original to observe
        // a second emission.
        val message = MessageCenterTestUtils.createMessage("message-id")
        val updatedMessage = MessageCenterTestUtils.createMessage("message-id", extras = mapOf("k" to "v"))
        val inboxUpdatedFlow = MutableSharedFlow<Unit>()
        val inbox = mockk<Inbox>(relaxUnitFun = true) {
            coEvery { getMessage("message-id") } returnsMany listOf(message, updatedMessage)
            every { inboxUpdated } returns inboxUpdatedFlow
        }
        val viewModel = MessageViewModel(inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(MessageViewState.Empty)
            viewModel.loadMessage("message-id")
            assertThat(awaitItem()).isEqualTo(MessageViewState.Loading)
            awaitItem()

            val cancellation = viewModel.subscribeForMessageUpdates()
            advanceUntilIdle() // let the collector start before emitting

            inboxUpdatedFlow.emit(Unit)
            val refreshed = awaitItem()
            assertThat(refreshed).isInstanceOf(MessageViewState.MessageContent::class.java)
            assertThat((refreshed as MessageViewState.MessageContent).message).isEqualTo(updatedMessage)

            cancellation.cancel()

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 2) { inbox.getMessage("message-id") }
    }

    private fun createLayout(stateRestorationId: String?): AirshipLayout {
        val options = stateRestorationId?.let {
            """, "options": { "state_restoration": { "scope": "instance", "restore_id": "$it" } }"""
        } ?: ""

        val json = """
            {
              "version": 1,
              "presentation": {
                "type": "embedded",
                "embedded_id": "home_banner",
                "default_placement": { "size": { "width": "50%", "height": "50%" } }
              },
              "view": { "type": "container", "items": [] }$options
            }
        """.trimIndent()

        return AirshipLayout(JsonValue.parseString(json))
    }
}
