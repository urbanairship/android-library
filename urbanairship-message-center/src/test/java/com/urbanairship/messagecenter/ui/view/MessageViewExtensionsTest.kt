/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter.ui.view

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.ui.ThomasLayoutViewFactory
import com.urbanairship.iam.content.AirshipLayout
import com.urbanairship.json.JsonValue
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
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
public class MessageViewExtensionsTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.UrbanAirship_MessageCenter_Base
    )

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    public fun bindWiresAnalyticsAndStorageFactories(): TestResult = runTest {
        val messageView = MessageView(context)
        val viewModel = mockk<MessageViewModel>(relaxed = true) {
            every { states } returns MutableStateFlow(MessageViewState.Empty)
        }

        assertThat(messageView.analyticsFactory).isNull()
        assertThat(messageView.storageFactory).isNull()

        messageView.bind(viewModel, CoroutineScope(testDispatcher + SupervisorJob()))

        assertThat(messageView.analyticsFactory).isNotNull()
        assertThat(messageView.storageFactory).isNotNull()
    }

    @Test
    public fun rawScopeBindCollectsRenderStates(): TestResult = runTest {
        val states = MutableStateFlow<MessageViewState>(MessageViewState.Empty)
        val viewModel = mockk<MessageViewModel>(relaxed = true) {
            every { this@mockk.states } returns states
        }
        val messageView = spyk(MessageView(context))

        messageView.bind(viewModel, CoroutineScope(testDispatcher + SupervisorJob()))
        advanceUntilIdle()
        verify { messageView.render(MessageViewState.Empty) }

        states.value = MessageViewState.Loading
        advanceUntilIdle()
        verify { messageView.render(MessageViewState.Loading) }
    }

    @Test
    public fun rawScopeCloseCancelsCollection(): TestResult = runTest {
        val states = MutableStateFlow<MessageViewState>(MessageViewState.Empty)
        val viewModel = mockk<MessageViewModel>(relaxed = true) {
            every { this@mockk.states } returns states
        }
        val messageView = spyk(MessageView(context))

        val binding = messageView.bind(viewModel, CoroutineScope(testDispatcher + SupervisorJob()))
        advanceUntilIdle()
        verify(exactly = 1) { messageView.render(MessageViewState.Empty) }

        binding.close()

        // No further render calls after close, even if the state changes.
        states.value = MessageViewState.Loading
        advanceUntilIdle()
        verify(exactly = 0) { messageView.render(MessageViewState.Loading) }
    }

    @Test
    public fun rawScopeCloseReportsDismissalForActiveNativeMessage(): TestResult = runTest {
        mockkObject(ThomasLayoutViewFactory)
        every { ThomasLayoutViewFactory.createView(any(), any(), any()) } answers { android.view.View(context) }
        every { ThomasLayoutViewFactory.clear() } returns Unit
        every { ThomasLayoutViewFactory.calculateDisplayTime(any()) } returns kotlin.time.Duration.ZERO

        val message = createNativeMessage("native-id")
        val layout = createLayout()
        val analyticsListener = mockk<ThomasListenerInterface>(relaxed = true)
        val states = MutableStateFlow<MessageViewState>(
            MessageViewState.MessageContent(message, MessageViewState.MessageContent.Content.Native(layout))
        )
        val viewModel = mockk<MessageViewModel>(relaxed = true) {
            every { this@mockk.states } returns states
            every { currentMessage } returns message
            every { makeAnalytics(message, any()) } returns analyticsListener
        }
        val messageView = MessageView(context)

        val binding = messageView.bind(viewModel, CoroutineScope(testDispatcher + SupervisorJob()))
        advanceUntilIdle()

        binding.close()
        advanceUntilIdle() // teardown runs once the collector job actually completes

        verify(exactly = 1) {
            analyticsListener.onReportingEvent(
                match { it is ReportingEvent.Dismiss && it.data == ReportingEvent.DismissData.UserDismissed }
            )
        }
    }

    @Test
    public fun cancellingScopeDirectlyAlsoReportsDismissalForActiveNativeMessage(): TestResult = runTest {
        mockkObject(ThomasLayoutViewFactory)
        every { ThomasLayoutViewFactory.createView(any(), any(), any()) } answers { android.view.View(context) }
        every { ThomasLayoutViewFactory.clear() } returns Unit
        every { ThomasLayoutViewFactory.calculateDisplayTime(any()) } returns kotlin.time.Duration.ZERO

        val message = createNativeMessage("native-id")
        val layout = createLayout()
        val analyticsListener = mockk<ThomasListenerInterface>(relaxed = true)
        val states = MutableStateFlow<MessageViewState>(
            MessageViewState.MessageContent(message, MessageViewState.MessageContent.Content.Native(layout))
        )
        val viewModel = mockk<MessageViewModel>(relaxed = true) {
            every { this@mockk.states } returns states
            every { currentMessage } returns message
            every { makeAnalytics(message, any()) } returns analyticsListener
        }
        val messageView = MessageView(context)
        val scope = CoroutineScope(testDispatcher + SupervisorJob())

        messageView.bind(viewModel, scope)
        advanceUntilIdle()

        // Cancelling the caller's own scope (instead of calling the returned AutoCloseable's
        // close()) must be an equally valid way to unbind and report dismissal, per bind()'s doc.
        scope.cancel()
        advanceUntilIdle()

        verify(exactly = 1) {
            analyticsListener.onReportingEvent(
                match { it is ReportingEvent.Dismiss && it.data == ReportingEvent.DismissData.UserDismissed }
            )
        }
        assertThat(messageView.analyticsFactory).isNull()
        assertThat(messageView.storageFactory).isNull()
    }

    @Test
    public fun onDismissedIsIdempotentAcrossRepeatedCalls(): TestResult = runTest {
        mockkObject(ThomasLayoutViewFactory)
        every { ThomasLayoutViewFactory.createView(any(), any(), any()) } answers { android.view.View(context) }
        every { ThomasLayoutViewFactory.clear() } returns Unit
        every { ThomasLayoutViewFactory.calculateDisplayTime(any()) } returns kotlin.time.Duration.ZERO

        val message = createNativeMessage("native-id")
        val layout = createLayout()
        val analyticsListener = mockk<ThomasListenerInterface>(relaxed = true)
        val states = MutableStateFlow<MessageViewState>(
            MessageViewState.MessageContent(message, MessageViewState.MessageContent.Content.Native(layout))
        )
        val viewModel = mockk<MessageViewModel>(relaxed = true) {
            every { this@mockk.states } returns states
            every { currentMessage } returns message
            every { makeAnalytics(message, any()) } returns analyticsListener
        }
        val messageView = MessageView(context)

        messageView.bind(viewModel, CoroutineScope(testDispatcher + SupervisorJob()))
        advanceUntilIdle()

        // Calling onDismissed() directly (not via bind()'s AutoCloseable) more than once must
        // not re-fire the dismiss report -- this is the invariant bind()'s own guard also
        // depends on.
        messageView.onDismissed()
        messageView.onDismissed()

        verify(exactly = 1) {
            analyticsListener.onReportingEvent(
                match { it is ReportingEvent.Dismiss && it.data == ReportingEvent.DismissData.UserDismissed }
            )
        }
    }

    @Test
    public fun layoutReportedDismissalStillClearsNativeContainer(): TestResult = runTest {
        mockkObject(ThomasLayoutViewFactory)
        every { ThomasLayoutViewFactory.createView(any(), any(), any()) } answers { android.view.View(context) }
        every { ThomasLayoutViewFactory.clear() } returns Unit
        every { ThomasLayoutViewFactory.calculateDisplayTime(any()) } returns kotlin.time.Duration.ZERO

        val message = createNativeMessage("native-id")
        val layout = createLayout()
        val analyticsListener = mockk<ThomasListenerInterface>(relaxed = true)
        val onDismissSlot = slot<() -> Unit>()
        val states = MutableStateFlow<MessageViewState>(
            MessageViewState.MessageContent(message, MessageViewState.MessageContent.Content.Native(layout))
        )
        val viewModel = mockk<MessageViewModel>(relaxed = true) {
            every { this@mockk.states } returns states
            every { currentMessage } returns message
            every { makeAnalytics(message, capture(onDismissSlot)) } returns analyticsListener
        }
        val messageView = MessageView(context)

        messageView.bind(viewModel, CoroutineScope(testDispatcher + SupervisorJob()))
        advanceUntilIdle()

        val nativeContainer = messageView.findViewById<FrameLayout>(R.id.native_container)
        assertThat(nativeContainer.childCount).isEqualTo(1)

        // The layout dismisses itself (e.g. its own close action) -- its own listener chain
        // already reported this, so onDismissed() must not report it again...
        onDismissSlot.captured.invoke()
        messageView.onDismissed()

        verify(exactly = 0) { analyticsListener.onReportingEvent(any()) }
        // ...but it must still tear down the native view, same as the host-forced path.
        assertThat(nativeContainer.childCount).isEqualTo(0)
    }

    @Test
    public fun dismissReportedFlagDoesNotLeakAcrossMessages(): TestResult = runTest {
        mockkObject(ThomasLayoutViewFactory)
        every { ThomasLayoutViewFactory.createView(any(), any(), any()) } answers { android.view.View(context) }
        every { ThomasLayoutViewFactory.clear() } returns Unit
        every { ThomasLayoutViewFactory.calculateDisplayTime(any()) } returns kotlin.time.Duration.ZERO

        val messageA = createNativeMessage("native-a")
        val messageB = createNativeMessage("native-b")
        val layout = createLayout()
        val listenerA = mockk<ThomasListenerInterface>(relaxed = true)
        val listenerB = mockk<ThomasListenerInterface>(relaxed = true)
        val onDismissSlotA = slot<() -> Unit>()

        var activeMessage = messageA
        val states = MutableStateFlow<MessageViewState>(
            MessageViewState.MessageContent(messageA, MessageViewState.MessageContent.Content.Native(layout))
        )
        val viewModel = mockk<MessageViewModel>(relaxed = true) {
            every { this@mockk.states } returns states
            every { currentMessage } answers { activeMessage }
            every { makeAnalytics(messageA, capture(onDismissSlotA)) } returns listenerA
            every { makeAnalytics(messageB, any()) } returns listenerB
        }
        val messageView = MessageView(context)

        messageView.bind(viewModel, CoroutineScope(testDispatcher + SupervisorJob()))
        advanceUntilIdle()

        // Message A's native layout dismisses itself, but onDismissed() is never called for it
        // (e.g. the host tears down without an explicit close) -- isDismissReported is left
        // "true" internally.
        onDismissSlotA.captured.invoke()

        // A different native message loads next.
        activeMessage = messageB
        states.value = MessageViewState.MessageContent(messageB, MessageViewState.MessageContent.Content.Native(layout))
        advanceUntilIdle()

        // Host-forced dismiss for message B must still report it -- message A's stale
        // isDismissReported flag must not have leaked into message B's display.
        messageView.onDismissed()

        verify(exactly = 1) {
            listenerB.onReportingEvent(
                match { it is ReportingEvent.Dismiss && it.data == ReportingEvent.DismissData.UserDismissed }
            )
        }
        verify(exactly = 0) { listenerA.onReportingEvent(any()) }
    }

    @Test
    public fun staleDismissCallbackForPreviousMessageDoesNotCorruptNewMessage(): TestResult = runTest {
        mockkObject(ThomasLayoutViewFactory)
        every { ThomasLayoutViewFactory.createView(any(), any(), any()) } answers { android.view.View(context) }
        every { ThomasLayoutViewFactory.clear() } returns Unit
        every { ThomasLayoutViewFactory.calculateDisplayTime(any()) } returns kotlin.time.Duration.ZERO

        val messageA = createNativeMessage("native-a")
        val messageB = createNativeMessage("native-b")
        val layout = createLayout()
        val listenerA = mockk<ThomasListenerInterface>(relaxed = true)
        val listenerB = mockk<ThomasListenerInterface>(relaxed = true)
        val onDismissSlotA = slot<() -> Unit>()

        var activeMessage = messageA
        val states = MutableStateFlow<MessageViewState>(
            MessageViewState.MessageContent(messageA, MessageViewState.MessageContent.Content.Native(layout))
        )
        val viewModel = mockk<MessageViewModel>(relaxed = true) {
            every { this@mockk.states } returns states
            every { currentMessage } answers { activeMessage }
            every { makeAnalytics(messageA, capture(onDismissSlotA)) } returns listenerA
            every { makeAnalytics(messageB, any()) } returns listenerB
        }
        val messageView = MessageView(context)

        messageView.bind(viewModel, CoroutineScope(testDispatcher + SupervisorJob()))
        advanceUntilIdle()

        // Message B loads BEFORE message A's debounced self-dismiss callback fires -- simulating
        // the real ~100ms window between a native close-button tap and its report.
        activeMessage = messageB
        states.value = MessageViewState.MessageContent(messageB, MessageViewState.MessageContent.Content.Native(layout))
        advanceUntilIdle()

        // A's stale callback fires late, after B is already current.
        onDismissSlotA.captured.invoke()

        // A host-forced dismiss of B must still report it -- A's late callback must not have
        // been mistaken for B's own self-dismissal.
        messageView.onDismissed()

        verify(exactly = 1) {
            listenerB.onReportingEvent(
                match { it is ReportingEvent.Dismiss && it.data == ReportingEvent.DismissData.UserDismissed }
            )
        }
        verify(exactly = 0) { listenerA.onReportingEvent(any()) }
    }

    @Test
    public fun failedNativeViewCreationLeavesNoMismatchedDisplayState(): TestResult = runTest {
        mockkObject(ThomasLayoutViewFactory)
        every { ThomasLayoutViewFactory.clear() } returns Unit
        every { ThomasLayoutViewFactory.calculateDisplayTime(any()) } returns kotlin.time.Duration.ZERO

        val messageA = createNativeMessage("native-a")
        val messageB = createNativeMessage("native-b")
        val layout = createLayout()
        val listenerA = mockk<ThomasListenerInterface>(relaxed = true)
        val listenerB = mockk<ThomasListenerInterface>(relaxed = true)

        // A renders successfully; B's native view creation fails.
        every { ThomasLayoutViewFactory.createView(any(), any(), "native-a") } answers { android.view.View(context) }
        every { ThomasLayoutViewFactory.createView(any(), any(), "native-b") } returns null

        var activeMessage = messageA
        val states = MutableStateFlow<MessageViewState>(
            MessageViewState.MessageContent(messageA, MessageViewState.MessageContent.Content.Native(layout))
        )
        val viewModel = mockk<MessageViewModel>(relaxed = true) {
            every { this@mockk.states } returns states
            every { currentMessage } answers { activeMessage }
            every { makeAnalytics(messageA, any()) } returns listenerA
            every { makeAnalytics(messageB, any()) } returns listenerB
        }
        val messageView = MessageView(context)

        messageView.bind(viewModel, CoroutineScope(testDispatcher + SupervisorJob()))
        advanceUntilIdle()

        // B's native view creation fails.
        activeMessage = messageB
        states.value = MessageViewState.MessageContent(messageB, MessageViewState.MessageContent.Content.Native(layout))
        advanceUntilIdle()

        // A host-forced dismiss now must not fire a mismatched report combining A's stale
        // listener with B's identity -- the failed transition must have left no display state
        // to report on at all.
        messageView.onDismissed()

        verify(exactly = 0) { listenerA.onReportingEvent(any()) }
        verify(exactly = 0) { listenerB.onReportingEvent(any()) }
    }

    @Test
    public fun bindCancelsPreviousBindingOnRebind(): TestResult = runTest {
        val states1 = MutableStateFlow<MessageViewState>(MessageViewState.Empty)
        val states2 = MutableStateFlow<MessageViewState>(MessageViewState.Empty)
        val viewModel1 = mockk<MessageViewModel>(relaxed = true) { every { this@mockk.states } returns states1 }
        val viewModel2 = mockk<MessageViewModel>(relaxed = true) { every { this@mockk.states } returns states2 }
        val messageView = spyk(MessageView(context))

        messageView.bind(viewModel1, CoroutineScope(testDispatcher + SupervisorJob()))
        advanceUntilIdle()
        verify(exactly = 1) { messageView.render(MessageViewState.Empty) }

        // Rebind without closing the first -- the first binding's job must be cancelled.
        messageView.bind(viewModel2, CoroutineScope(testDispatcher + SupervisorJob()))
        advanceUntilIdle()

        states1.value = MessageViewState.Loading
        advanceUntilIdle()
        verify(exactly = 0) { messageView.render(MessageViewState.Loading) }

        states2.value = MessageViewState.Loading
        advanceUntilIdle()
        verify(exactly = 1) { messageView.render(MessageViewState.Loading) }
    }

    @Test
    public fun rebindWhileNativeMessageActiveReportsDismissalForAbandonedMessage(): TestResult = runTest {
        mockkObject(ThomasLayoutViewFactory)
        every { ThomasLayoutViewFactory.createView(any(), any(), any()) } answers { android.view.View(context) }
        every { ThomasLayoutViewFactory.clear() } returns Unit
        every { ThomasLayoutViewFactory.calculateDisplayTime(any()) } returns kotlin.time.Duration.ZERO

        val message1 = createNativeMessage("native-1")
        val layout = createLayout()
        val listener1 = mockk<ThomasListenerInterface>(relaxed = true)
        val states1 = MutableStateFlow<MessageViewState>(
            MessageViewState.MessageContent(message1, MessageViewState.MessageContent.Content.Native(layout))
        )
        val viewModel1 = mockk<MessageViewModel>(relaxed = true) {
            every { this@mockk.states } returns states1
            every { currentMessage } returns message1
            every { makeAnalytics(message1, any()) } returns listener1
        }
        val viewModel2 = mockk<MessageViewModel>(relaxed = true) {
            every { states } returns MutableStateFlow(MessageViewState.Empty)
        }
        val messageView = MessageView(context)

        messageView.bind(viewModel1, CoroutineScope(testDispatcher + SupervisorJob()))
        advanceUntilIdle()

        // Rebind to a different viewModel without closing the first -- message1's native
        // display is being abandoned and must still be reported dismissed.
        messageView.bind(viewModel2, CoroutineScope(testDispatcher + SupervisorJob()))
        advanceUntilIdle()

        verify(exactly = 1) {
            listener1.onReportingEvent(
                match { it is ReportingEvent.Dismiss && it.data == ReportingEvent.DismissData.UserDismissed }
            )
        }
    }

    @Test
    public fun closeClearsAnalyticsAndStorageFactories(): TestResult = runTest {
        val viewModel = mockk<MessageViewModel>(relaxed = true) {
            every { states } returns MutableStateFlow(MessageViewState.Empty)
        }
        val messageView = MessageView(context)

        val binding = messageView.bind(viewModel, CoroutineScope(testDispatcher + SupervisorJob()))
        assertThat(messageView.analyticsFactory).isNotNull()
        assertThat(messageView.storageFactory).isNotNull()

        binding.close()
        advanceUntilIdle() // teardown runs once the collector job actually completes

        assertThat(messageView.analyticsFactory).isNull()
        assertThat(messageView.storageFactory).isNull()
    }

    @Test
    public fun lifecycleBindDoesNotReportDismissalWhenLifecycleEnds(): TestResult = runTest {
        val owner = FakeLifecycleOwner()
        owner.registry.currentState = Lifecycle.State.RESUMED

        val states = MutableStateFlow<MessageViewState>(MessageViewState.Empty)
        val viewModel = mockk<MessageViewModel>(relaxed = true) {
            every { this@mockk.states } returns states
        }
        val messageView = spyk(MessageView(context))

        // This overload returns nothing to close -- its collector job is scoped to the
        // LifecycleOwner itself, so teardown is driven entirely by the Lifecycle ending.
        messageView.bind(viewModel, owner)
        advanceUntilIdle()
        verify(exactly = 1) { messageView.render(MessageViewState.Empty) }

        owner.registry.currentState = Lifecycle.State.DESTROYED
        advanceUntilIdle()
        verify(exactly = 0) { messageView.onDismissed() }
    }

    @Test
    public fun lifecycleBindOnlyCollectsWhileAtLeastMinActiveState(): TestResult = runTest {
        val owner = FakeLifecycleOwner()
        owner.registry.currentState = Lifecycle.State.CREATED // below STARTED

        val states = MutableStateFlow<MessageViewState>(MessageViewState.Empty)
        val viewModel = mockk<MessageViewModel>(relaxed = true) {
            every { this@mockk.states } returns states
        }
        val messageView = spyk(MessageView(context))

        messageView.bind(viewModel, owner, minActiveState = Lifecycle.State.STARTED)
        advanceUntilIdle()
        verify(exactly = 0) { messageView.render(any()) }

        owner.registry.currentState = Lifecycle.State.STARTED
        advanceUntilIdle()
        verify(exactly = 1) { messageView.render(MessageViewState.Empty) }

        owner.registry.currentState = Lifecycle.State.CREATED
        advanceUntilIdle()
        states.value = MessageViewState.Loading
        advanceUntilIdle()
        verify(exactly = 0) { messageView.render(MessageViewState.Loading) }
    }

    private fun createNativeMessage(id: String): Message {
        return Message(
            id = id,
            title = "$id title",
            bodyUrl = "https://example.com/$id/body/",
            sentDate = Date(),
            expirationDate = null,
            isUnread = true,
            extras = null,
            contentType = Message.ContentType.Native(1),
            messageUrl = "https://example.com/$id",
            reporting = null,
            rawMessageJson = JsonValue.NULL,
            isDeletedClient = false,
        )
    }

    private fun createLayout(): AirshipLayout {
        val json = """
            {
              "version": 1,
              "presentation": {
                "type": "embedded",
                "embedded_id": "home_banner",
                "default_placement": { "size": { "width": "50%", "height": "50%" } }
              },
              "view": { "type": "container", "items": [] }
            }
        """.trimIndent()

        return AirshipLayout(JsonValue.parseString(json))
    }

    private class FakeLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
}
