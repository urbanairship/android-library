/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.event.CheckboxEvent
import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.event.Event.ViewInit
import com.urbanairship.android.layout.event.EventType
import com.urbanairship.android.layout.info.CheckboxInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ToggleStyle
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.json.JsonValue

/**
 * Checkbox input for use within a `CheckboxController`.
 */
internal class CheckboxModel(
    toggleStyle: ToggleStyle,
    val reportingValue: JsonValue,
    contentDescription: String? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    environment: ModelEnvironment
) : CheckableModel(
    viewType = ViewType.CHECKBOX,
    style = toggleStyle,
    toggleType = toggleStyle.type,
    contentDescription = contentDescription,
    backgroundColor = backgroundColor,
    border = border,
    environment = environment
) {
    constructor(info: CheckboxInfo, env: ModelEnvironment) : this(
        toggleStyle = info.style,
        reportingValue = info.reportingValue,
        contentDescription = info.contentDescription,
        backgroundColor = info.backgroundColor,
        border = info.border,
        environment = env
    )

    override fun buildInitEvent(): Event = ViewInit(this)

    override fun buildInputChangeEvent(isChecked: Boolean): Event =
        CheckboxEvent.InputChange(reportingValue, isChecked)

    override fun onEvent(event: Event, layoutData: LayoutData): Boolean {
        return when (event.type) {
            EventType.CHECKBOX_VIEW_UPDATE -> {
                val update = event as CheckboxEvent.ViewUpdate
                if (reportingValue == update.value) {
                    setChecked(update.isChecked)
                }
                // Don't consume the event so it can be handled by siblings.
                false
            }
            else -> super.onEvent(event, layoutData)
        }
    }
}
