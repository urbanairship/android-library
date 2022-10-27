/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.event.Event.ViewInit
import com.urbanairship.android.layout.event.EventType
import com.urbanairship.android.layout.event.RadioEvent
import com.urbanairship.android.layout.info.RadioInputInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.ToggleStyle
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.json.JsonValue

internal class RadioInputModel(
    toggleStyle: ToggleStyle,
    val reportingValue: JsonValue,
    private val attributeValue: JsonValue? = null,
    contentDescription: String? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    environment: ModelEnvironment
) : CheckableModel(
    viewType = ViewType.RADIO_INPUT,
    style = toggleStyle,
    toggleType = toggleStyle.type,
    contentDescription = contentDescription,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment
) {
    constructor(info: RadioInputInfo, env: ModelEnvironment) : this(
        toggleStyle = info.style,
        reportingValue = info.reportingValue,
        attributeValue = info.attributeValue,
        contentDescription = info.contentDescription,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        environment = env
    )

    override fun buildInitEvent(): Event = ViewInit(this)

    override fun buildInputChangeEvent(isChecked: Boolean): Event =
        RadioEvent.InputChange(reportingValue, attributeValue, isChecked)

    override fun onEvent(event: Event, layoutData: LayoutData): Boolean {
        return when (event.type) {
            EventType.RADIO_VIEW_UPDATE -> {
                val update = event as RadioEvent.ViewUpdate
                setChecked(reportingValue == update.value)

                // Don't consume the event so it can be handled by siblings.
                false
            }
            else -> super.onEvent(event, layoutData)
        }
    }
}
