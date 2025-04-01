package com.urbanairship.android.layout.environment

import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.reporting.AttributeName
import com.urbanairship.android.layout.reporting.DisplayTimer
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.channel.AttributeEditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
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
) {
    val layoutEvents: Flow<LayoutEvent> = eventHandler.layoutEvents

    init {
        modelScope.launch {
            layoutEvents
                .filterIsInstance<LayoutEvent.Report>()
                .distinctUntilChanged()
                .collect { (event, state) -> reporter.report(event, state) }
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
        )
}

internal class LayoutEventHandler(coroutineScope: CoroutineScope) {
    private val eventsChannel = Channel<LayoutEvent>(UNLIMITED)

    val layoutEvents = eventsChannel.receiveAsFlow()
        .shareIn(coroutineScope, SharingStarted.Eagerly)

    suspend fun broadcast(event: LayoutEvent) = eventsChannel.send(event)
}

internal sealed class LayoutEvent {
    data class SubmitForm(
        val buttonIdentifier: String,
        val onSubmitted: (suspend () -> Unit) = {}
    ) : LayoutEvent()

    data class ValidateForm(
        val buttonIdentifier: String,
        val onValidated: (suspend (isValid: Boolean) -> Unit) = {}
    ) : LayoutEvent()

    data class Report(
        val event: ReportingEvent,
        val context: LayoutData,
    ) : LayoutEvent()

    object Finish : LayoutEvent()
}

internal class AttributeHandler(
    private val contactEditorFactory: () -> AttributeEditor =
        { UAirship.shared().contact.editAttributes() },
    private val channelEditorFactory: () -> AttributeEditor =
        { UAirship.shared().channel.editAttributes() }
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
