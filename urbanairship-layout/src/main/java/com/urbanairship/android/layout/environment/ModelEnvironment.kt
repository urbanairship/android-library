package com.urbanairship.android.layout.environment

import android.view.View
import androidx.annotation.MainThread
import com.urbanairship.UALog
import com.urbanairship.Airship
import com.urbanairship.android.layout.LayoutStateStorage
import com.urbanairship.android.layout.ai.DefaultThomasAIInference
import com.urbanairship.android.layout.ai.ThomasAIInference
import com.urbanairship.android.layout.ai.ThomasAIStatus
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.model.PagerNextFallback
import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.reporting.AttributeName
import com.urbanairship.android.layout.reporting.DisplayTimer
import com.urbanairship.android.layout.util.ThomasViewIdResolver
import com.urbanairship.channel.AttributeEditor
import com.urbanairship.util.combineStates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class ModelEnvironment(
    val layoutState: LayoutState,
    val reporter: Reporter,
    val actionsRunner: ThomasActionRunner,
    val displayTimer: DisplayTimer,
    val modelScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
    val attributeHandler: AttributeHandler = AttributeHandler(),
    val channelRegistrar: ThomasChannelRegistrar = ThomasChannelRegistrar(),
    val eventHandler: LayoutEventHandler = LayoutEventHandler(modelScope),
    val pagerTracker: PagersViewTracker = PagersViewTracker(),
    val viewIdResolver: ThomasViewIdResolver = ThomasViewIdResolver(),
    val stateStorage: LayoutStateStorage? = null,
    val aiInference: ThomasAIInference? = null,
    /** The layout's `$environment`, shared by every copy [withState] makes. */
    private val environmentPublisher: ThomasEnvironmentPublisher = ThomasEnvironmentPublisher(),
    /**
     * [ThomasCapabilities.ai] is null until the model reports in, and forever when there is no
     * model to report — a layout with no AI never sees a `$ai` state key at all.
     *
     * Subscribed here rather than per-model so every predicate reads the same snapshot, and so
     * [withState] shares it instead of opening a second subscription.
     */
    val capabilities: StateFlow<ThomasCapabilities> = combineStates(
        flow1 = aiInference?.statusUpdates
            ?.stateIn<ThomasAIStatus?>(modelScope, SharingStarted.Eagerly, null)
            ?: MutableStateFlow(null).asStateFlow(),
        flow2 = environmentPublisher.environment,
        transform = ::ThomasCapabilities
    )
) {
    val layoutEvents: Flow<LayoutEvent> = eventHandler.layoutEvents

    init {
        modelScope.launch {
            layoutEvents
                .filterIsInstance<LayoutEvent.Report>()
                .distinctUntilChanged()
                .collect { reporter.report(it.event) }
        }
    }

    fun withState(state: LayoutState) =
        ModelEnvironment(
            layoutState = state,
            reporter = this.reporter,
            actionsRunner = this.actionsRunner,
            displayTimer = this.displayTimer,
            modelScope = modelScope,
            attributeHandler = attributeHandler,
            eventHandler = this.eventHandler,
            pagerTracker = this.pagerTracker,
            channelRegistrar = this.channelRegistrar,
            viewIdResolver = this.viewIdResolver,
            aiInference = this.aiInference,
            environmentPublisher = this.environmentPublisher,
            capabilities = this.capabilities
        )

    /**
     * Publishes the platform, window and frame as `$environment`, re-evaluating every predicate
     * that reads it; see [ThomasEnvironmentPublisher] for when a reading goes out.
     *
     * The root view calls this after a layout that changed its frame or followed a configuration
     * change; see [EnvironmentFrameTracker].
     *
     * @param rootView the layout's root view, whose window is read.
     * @param frame the frame the root view last reported, or null before it has been laid out.
     * @param immediate publishes now rather than at the next frame; for a root view's first layout.
     */
    @MainThread
    fun publishEnvironment(rootView: View, frame: EnvironmentFrame?, immediate: Boolean = false) {
        environmentPublisher.publish(rootView, frame, immediate)
    }

    /** See [ThomasEnvironmentPublisher.seed]; the root view calls this when it's created. */
    @MainThread
    fun seedEnvironment(rootView: View) {
        environmentPublisher.seed(rootView)
    }
}

internal class LayoutEventHandler(coroutineScope: CoroutineScope) {
    private val eventsChannel = Channel<LayoutEvent>(UNLIMITED)

    val layoutEvents = eventsChannel.receiveAsFlow()
        .shareIn(coroutineScope, SharingStarted.Eagerly)

    suspend fun broadcast(event: LayoutEvent) = eventsChannel.send(event)
}

internal sealed class LayoutEvent {
    /** Submits [formIdentifier], the form enclosing the button. */
    data class SubmitForm(
        val buttonIdentifier: String,
        val formIdentifier: String,
        val onSubmitted: (suspend () -> Unit) = {}
    ) : LayoutEvent() {
        fun targets(identifier: String): Boolean = formIdentifier == identifier
    }

    /** Validates [formIdentifier], the form enclosing the button. */
    data class ValidateForm(
        val buttonIdentifier: String,
        val formIdentifier: String,
        val onValidated: (suspend () -> Unit) = {}
    ) : LayoutEvent() {
        fun targets(identifier: String): Boolean = formIdentifier == identifier
    }

    data class Report(
        val event: ReportingEvent
    ) : LayoutEvent()

    data class StateUpdate(
        val state: ThomasState
    ): LayoutEvent()

    data class AsyncViewReload(val identifier: String) : LayoutEvent()

    data class Finish(val cancel: Boolean = false) : LayoutEvent()

    /**
     * A command for one pager: the one whose controller encloses the view that sent it.
     *
     * The bus is shared by the whole layout, so a pager nested in another's page acts only on
     * events carrying its own [pager] state, and leaves the outer pager's navigation alone.
     */
    sealed class Pager : LayoutEvent() {
        /** The state of the pager controller this event drives, or null when none encloses the sender. */
        abstract val pager: SharedState<State.Pager>?

        data class Next(val fallback: PagerNextFallback, override val pager: SharedState<State.Pager>?) : Pager()
        data class Previous(override val pager: SharedState<State.Pager>?) : Pager()
        data class Start(override val pager: SharedState<State.Pager>?) : Pager()
        data class End(override val pager: SharedState<State.Pager>?) : Pager()
        data class Pause(override val pager: SharedState<State.Pager>?) : Pager()
        data class Resume(override val pager: SharedState<State.Pager>?) : Pager()
        data class PauseToggle(override val pager: SharedState<State.Pager>?) : Pager()
    }
}

internal class AttributeHandler(
    private val contactEditorFactory: () -> AttributeEditor =
        { Airship.contact.editAttributes() },
    private val channelEditorFactory: () -> AttributeEditor =
        { Airship.channel.editAttributes() }
) {

    fun update(attributes: Map<AttributeName, AttributeValue>) {
        val contactEditor = contactEditorFactory()
        val channelEditor = channelEditorFactory()

        for ((key, value) in attributes) {
            val attribute = (if (key.isContact) key.contact else key.channel) ?: continue
            if (value.isNull) continue

            UALog.v(
                "Setting ${if (key.isChannel) "channel" else "contact"} attribute: " +
                        "'$attribute' => '$value'"
            )

            val editor = if (key.isContact) contactEditor else channelEditor
            editor.setAttributeValue(attribute, value)
        }

        contactEditor.apply()
        channelEditor.apply()
    }

    private fun AttributeEditor.setAttributeValue(attribute: String, value: AttributeValue) {
        if (value.isString) {
            setAttribute(attribute, value.optString())
        } else if (value.isDouble) {
            setAttribute(attribute, value.getDouble(-1.0))
        } else if (value.isFloat) {
            setAttribute(attribute, value.getFloat(-1f))
        } else if (value.isInteger) {
            setAttribute(attribute, value.getInt(-1))
        } else if (value.isLong) {
            setAttribute(attribute, value.getLong(-1))
        }
    }
}
