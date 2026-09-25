/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter.compose.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.urbanairship.android.layout.LayoutDataStorage
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.iam.content.AirshipLayout
import com.urbanairship.json.JsonValue
import com.urbanairship.messagecenter.Inbox
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.compose.createMessage
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.Action
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.State
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.State.MessageContent.Content
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.State.MessageContent.WebViewState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class DefaultMessageCenterMessageViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val inboxUpdates = MutableSharedFlow<Unit>()

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
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = mockInbox())

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(State.Empty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun constructorMessageIdLoadsMessage(): TestResult = runTest {
        val message = createMessage("message-id")
        val inbox = mockInbox { coEvery { getMessage("message-id") } returns message }
        val viewModel = DefaultMessageCenterMessageViewModel(messageId = "message-id", inbox = inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(State.Loading("message-id"))
            assertThat(awaitItem()).isEqualTo(State.MessageContent(message, Content.Html(WebViewState.INIT)))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun loadHtmlMessageEmitsHtmlContentInInitState(): TestResult = runTest {
        val message = createMessage("message-id")
        val inbox = mockInbox { coEvery { getMessage("message-id") } returns message }
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(State.Empty)

            viewModel.handle(Action.LoadMessage("message-id"))
            assertThat(awaitItem()).isEqualTo(State.Loading("message-id"))
            assertThat(awaitItem()).isEqualTo(State.MessageContent(message, Content.Html(WebViewState.INIT)))

            cancelAndIgnoreRemainingEvents()
        }
        assertThat(viewModel.currentMessage).isEqualTo(message)
    }

    @Test
    public fun loadPlainAndUnknownMessagesUseHtmlContent(): TestResult = runTest {
        val plain = createMessage("plain", contentType = Message.ContentType.Plain)
        val unknown = createMessage("unknown", contentType = Message.ContentType.Unknown("text/other"))
        val inbox = mockInbox {
            coEvery { getMessage("plain") } returns plain
            coEvery { getMessage("unknown") } returns unknown
        }
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(State.Empty)

            viewModel.handle(Action.LoadMessage("plain"))
            assertThat(awaitItem()).isEqualTo(State.Loading("plain"))
            assertThat(awaitItem()).isEqualTo(State.MessageContent(plain, Content.Html(WebViewState.INIT)))

            viewModel.handle(Action.LoadMessage("unknown"))
            assertThat(awaitItem()).isEqualTo(State.Loading("unknown"))
            assertThat(awaitItem()).isEqualTo(State.MessageContent(unknown, Content.Html(WebViewState.INIT)))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun loadNativeMessageWithStateRestorationPreparesStorage(): TestResult = runTest {
        val message = createMessage("native-id", contentType = Message.ContentType.Native(1))
        val layout = createLayout(stateRestorationId = "restore-1")
        val storage = mockk<LayoutDataStorage>(relaxed = true)
        val inbox = mockInbox {
            coEvery { getMessage("native-id") } returns message
            coEvery { loadMessageLayout(message) } returns layout
            every { makeViewStateStorage("native-id") } returns storage
        }
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(State.Empty)

            viewModel.handle(Action.LoadMessage("native-id"))
            assertThat(awaitItem()).isEqualTo(State.Loading("native-id"))
            assertThat(awaitItem()).isEqualTo(State.MessageContent(message, Content.Native(layout, storage)))

            cancelAndIgnoreRemainingEvents()
        }
        coVerify { storage.prepare("restore-1") }
    }

    @Test
    public fun loadNativeMessageWithoutStateRestorationClearsStorage(): TestResult = runTest {
        val message = createMessage("native-id", contentType = Message.ContentType.Native(1))
        val layout = createLayout(stateRestorationId = null)
        val storage = mockk<LayoutDataStorage>(relaxed = true)
        val inbox = mockInbox {
            coEvery { getMessage("native-id") } returns message
            coEvery { loadMessageLayout(message) } returns layout
            every { makeViewStateStorage("native-id") } returns storage
        }
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(State.Empty)

            viewModel.handle(Action.LoadMessage("native-id"))
            assertThat(awaitItem()).isEqualTo(State.Loading("native-id"))
            assertThat(awaitItem()).isEqualTo(State.MessageContent(message, Content.Native(layout, null)))

            cancelAndIgnoreRemainingEvents()
        }
        coVerify { storage.clear() }
    }

    @Test
    public fun loadNativeMessageWithMissingLayoutEmitsUnavailable(): TestResult = runTest {
        val message = createMessage("native-id", contentType = Message.ContentType.Native(1))
        val inbox = mockInbox {
            coEvery { getMessage("native-id") } returns message
            coEvery { loadMessageLayout(message) } returns null
        }
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(State.Empty)

            viewModel.handle(Action.LoadMessage("native-id"))
            assertThat(awaitItem()).isEqualTo(State.Loading("native-id"))
            assertThat(awaitItem()).isEqualTo(State.Error(State.Error.Type.UNAVAILABLE, "native-id"))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun loadMessageFetchesWhenNotFoundLocally(): TestResult = runTest {
        val message = createMessage("message-id")
        val inbox = mockInbox {
            coEvery { getMessage("message-id") } returnsMany listOf(null, message)
            coEvery { fetchMessages() } returns true
        }
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(State.Empty)

            viewModel.handle(Action.LoadMessage("message-id"))
            assertThat(awaitItem()).isEqualTo(State.Loading("message-id"))
            assertThat(awaitItem()).isEqualTo(State.MessageContent(message, Content.Html(WebViewState.INIT)))

            cancelAndIgnoreRemainingEvents()
        }
        coVerify { inbox.fetchMessages() }
    }

    @Test
    public fun loadMessageEmitsLoadFailedWhenFetchFails(): TestResult = runTest {
        val inbox = mockInbox {
            coEvery { getMessage("message-id") } returns null
            coEvery { fetchMessages() } returns false
        }
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(State.Empty)

            viewModel.handle(Action.LoadMessage("message-id"))
            assertThat(awaitItem()).isEqualTo(State.Loading("message-id"))
            assertThat(awaitItem()).isEqualTo(State.Error(State.Error.Type.LOAD_FAILED, "message-id"))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun loadMessageEmitsUnavailableWhenStillMissingAfterFetch(): TestResult = runTest {
        val inbox = mockInbox {
            coEvery { getMessage("message-id") } returns null
            coEvery { fetchMessages() } returns true
        }
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(State.Empty)

            viewModel.handle(Action.LoadMessage("message-id"))
            assertThat(awaitItem()).isEqualTo(State.Loading("message-id"))
            assertThat(awaitItem()).isEqualTo(State.Error(State.Error.Type.UNAVAILABLE, "message-id"))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun loadMessageEmitsUnavailableWhenExpired(): TestResult = runTest {
        val expired = createMessage("message-id", expirationDate = Instant.EPOCH)
        val inbox = mockInbox { coEvery { getMessage("message-id") } returns expired }
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(State.Empty)

            viewModel.handle(Action.LoadMessage("message-id"))
            assertThat(awaitItem()).isEqualTo(State.Loading("message-id"))
            assertThat(awaitItem()).isEqualTo(State.Error(State.Error.Type.UNAVAILABLE, "message-id"))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun loadMessageWithSameIdSkipsReload(): TestResult = runTest {
        val message = createMessage("message-id")
        val inbox = mockInbox { coEvery { getMessage("message-id") } returns message }
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(State.Empty)
            viewModel.handle(Action.LoadMessage("message-id"))
            assertThat(awaitItem()).isEqualTo(State.Loading("message-id"))
            awaitItem()

            viewModel.handle(Action.LoadMessage("message-id"))
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { inbox.getMessage("message-id") }
    }

    @Test
    public fun updateWebViewStateUpdatesHtmlContent(): TestResult = runTest {
        val message = createMessage("message-id")
        val inbox = mockInbox { coEvery { getMessage("message-id") } returns message }
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(State.Empty)
            viewModel.handle(Action.LoadMessage("message-id"))
            assertThat(awaitItem()).isEqualTo(State.Loading("message-id"))
            assertThat(awaitItem()).isEqualTo(State.MessageContent(message, Content.Html(WebViewState.INIT)))

            viewModel.handle(Action.UpdateWebViewState(WebViewState.LOADING))
            assertThat(awaitItem()).isEqualTo(State.MessageContent(message, Content.Html(WebViewState.LOADING)))

            viewModel.handle(Action.UpdateWebViewState(WebViewState.LOADED))
            assertThat(awaitItem()).isEqualTo(State.MessageContent(message, Content.Html(WebViewState.LOADED)))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun updateWebViewStateWithoutMessageIsIgnored(): TestResult = runTest {
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = mockInbox())

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(State.Empty)

            viewModel.handle(Action.UpdateWebViewState(WebViewState.LOADING))
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun updateWebViewStateForNativeContentIsIgnored(): TestResult = runTest {
        val message = createMessage("native-id", contentType = Message.ContentType.Native(1))
        val layout = createLayout(stateRestorationId = null)
        val inbox = mockInbox {
            coEvery { getMessage("native-id") } returns message
            coEvery { loadMessageLayout(message) } returns layout
            every { makeViewStateStorage("native-id") } returns mockk(relaxed = true)
        }
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(State.Empty)
            viewModel.handle(Action.LoadMessage("native-id"))
            assertThat(awaitItem()).isEqualTo(State.Loading("native-id"))
            awaitItem()

            viewModel.handle(Action.UpdateWebViewState(WebViewState.LOADING))
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun markCurrentMessageReadDelegatesToInbox(): TestResult = runTest {
        val message = createMessage("message-id")
        val inbox = mockInbox { coEvery { getMessage("message-id") } returns message }
        val viewModel = DefaultMessageCenterMessageViewModel(messageId = "message-id", inbox = inbox)
        advanceUntilIdle()

        viewModel.handle(Action.MarkCurrentMessageRead)

        verify { inbox.markMessagesRead("message-id") }
    }

    @Test
    public fun markCurrentMessageReadWithoutMessageDoesNothing(): TestResult = runTest {
        val inbox = mockInbox()
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = inbox)

        viewModel.handle(Action.MarkCurrentMessageRead)

        verify(exactly = 0) { inbox.markMessagesRead(*anyVararg<String>()) }
    }

    @Test
    public fun deleteCurrentMessageDelegatesToInboxAndEmitsEmpty(): TestResult = runTest {
        val message = createMessage("message-id")
        val inbox = mockInbox { coEvery { getMessage("message-id") } returns message }
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(State.Empty)
            viewModel.handle(Action.LoadMessage("message-id"))
            assertThat(awaitItem()).isEqualTo(State.Loading("message-id"))
            awaitItem()

            viewModel.handle(Action.DeleteCurrentMessage)
            assertThat(awaitItem()).isEqualTo(State.Empty)

            cancelAndIgnoreRemainingEvents()
        }
        verify { inbox.deleteMessages("message-id") }
        assertThat(viewModel.currentMessage).isNull()
    }

    @Test
    public fun clearMessageEmitsEmptyAndStopsInboxUpdates(): TestResult = runTest {
        val message = createMessage("message-id")
        val inbox = mockInbox { coEvery { getMessage("message-id") } returns message }
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(State.Empty)
            viewModel.handle(Action.LoadMessage("message-id"))
            assertThat(awaitItem()).isEqualTo(State.Loading("message-id"))
            awaitItem()

            viewModel.handle(Action.ClearMessage)
            assertThat(awaitItem()).isEqualTo(State.Empty)

            inboxUpdates.emit(Unit)
            advanceUntilIdle()
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { inbox.getMessage("message-id") }
    }

    @Test
    public fun inboxUpdateReloadsCurrentMessage(): TestResult = runTest {
        // A StateFlow won't re-emit an equal value, so the refreshed message has to differ.
        val message = createMessage("message-id")
        val updatedMessage = createMessage("message-id", extras = mapOf("k" to "v"))
        val inbox = mockInbox {
            coEvery { getMessage("message-id") } returnsMany listOf(message, updatedMessage)
        }
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(State.Empty)
            viewModel.handle(Action.LoadMessage("message-id"))
            assertThat(awaitItem()).isEqualTo(State.Loading("message-id"))
            assertThat(awaitItem()).isEqualTo(State.MessageContent(message, Content.Html(WebViewState.INIT)))
            advanceUntilIdle()

            inboxUpdates.emit(Unit)
            val refreshed = awaitItem() as State.MessageContent
            assertThat(refreshed.message.extras).isEqualTo(mapOf("k" to "v"))

            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 2) { inbox.getMessage("message-id") }
    }

    @Test
    public fun inboxUpdateKeepsLoadedWebViewState(): TestResult = runTest {
        val message = createMessage("message-id")
        val updatedMessage = createMessage("message-id", extras = mapOf("k" to "v"))
        val inbox = mockInbox {
            coEvery { getMessage("message-id") } returnsMany listOf(message, updatedMessage)
        }
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(State.Empty)
            viewModel.handle(Action.LoadMessage("message-id"))
            assertThat(awaitItem()).isEqualTo(State.Loading("message-id"))
            awaitItem()
            viewModel.handle(Action.UpdateWebViewState(WebViewState.LOADED))
            awaitItem()
            advanceUntilIdle()

            inboxUpdates.emit(Unit)
            assertThat(awaitItem()).isEqualTo(State.MessageContent(updatedMessage, Content.Html(WebViewState.LOADED)))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun inboxUpdateKeepsErrorWebViewState(): TestResult = runTest {
        val message = createMessage("message-id")
        val updatedMessage = createMessage("message-id", extras = mapOf("k" to "v"))
        val inbox = mockInbox {
            coEvery { getMessage("message-id") } returnsMany listOf(message, updatedMessage)
        }
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(State.Empty)
            viewModel.handle(Action.LoadMessage("message-id"))
            assertThat(awaitItem()).isEqualTo(State.Loading("message-id"))
            awaitItem()
            viewModel.handle(Action.UpdateWebViewState(WebViewState.ERROR))
            awaitItem()
            advanceUntilIdle()

            inboxUpdates.emit(Unit)
            assertThat(awaitItem()).isEqualTo(State.MessageContent(updatedMessage, Content.Html(WebViewState.ERROR)))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun inboxUpdateWithNewBodyUrlResetsWebViewState(): TestResult = runTest {
        val message = createMessage("message-id")
        val movedMessage = createMessage("message-id", bodyUrl = "https://example.com/new-body")
        val inbox = mockInbox {
            coEvery { getMessage("message-id") } returnsMany listOf(message, movedMessage)
        }
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(State.Empty)
            viewModel.handle(Action.LoadMessage("message-id"))
            assertThat(awaitItem()).isEqualTo(State.Loading("message-id"))
            awaitItem()
            viewModel.handle(Action.UpdateWebViewState(WebViewState.LOADED))
            awaitItem()
            advanceUntilIdle()

            inboxUpdates.emit(Unit)
            assertThat(awaitItem()).isEqualTo(State.MessageContent(movedMessage, Content.Html(WebViewState.INIT)))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun refreshAfterWebViewErrorReloadsMessage(): TestResult = runTest {
        val message = createMessage("message-id")
        val inbox = mockInbox { coEvery { getMessage("message-id") } returns message }
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(State.Empty)
            viewModel.handle(Action.LoadMessage("message-id"))
            assertThat(awaitItem()).isEqualTo(State.Loading("message-id"))
            awaitItem()
            viewModel.handle(Action.UpdateWebViewState(WebViewState.ERROR))
            awaitItem()

            viewModel.handle(Action.Refresh)
            assertThat(awaitItem()).isEqualTo(State.Loading("message-id"))
            assertThat(awaitItem()).isEqualTo(State.MessageContent(message, Content.Html(WebViewState.INIT)))

            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 2) { inbox.getMessage("message-id") }
    }

    @Test
    public fun refreshAfterLoadFailedReloadsMessage(): TestResult = runTest {
        val message = createMessage("message-id")
        val inbox = mockInbox {
            coEvery { getMessage("message-id") } returnsMany listOf(null, message)
            coEvery { fetchMessages() } returns false
        }
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = inbox)

        viewModel.states.test {
            assertThat(awaitItem()).isEqualTo(State.Empty)
            viewModel.handle(Action.LoadMessage("message-id"))
            assertThat(awaitItem()).isEqualTo(State.Loading("message-id"))
            assertThat(awaitItem()).isEqualTo(State.Error(State.Error.Type.LOAD_FAILED, "message-id"))

            viewModel.handle(Action.Refresh)
            assertThat(awaitItem()).isEqualTo(State.Loading("message-id"))
            assertThat(awaitItem()).isEqualTo(State.MessageContent(message, Content.Html(WebViewState.INIT)))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun makeAnalyticsDelegatesToInbox() {
        val message = createMessage("message-id")
        val listener = mockk<ThomasListenerInterface>()
        val onDismiss = {}
        val inbox = mockInbox { every { makeNativeMessageAnalytics(message, onDismiss) } returns listener }
        val viewModel = DefaultMessageCenterMessageViewModel(inbox = inbox)

        assertThat(viewModel.makeAnalytics(message, onDismiss)).isEqualTo(listener)
    }

    private fun mockInbox(block: Inbox.() -> Unit = {}): Inbox = mockk(relaxUnitFun = true) {
        every { inboxUpdated } returns inboxUpdates
        block()
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
