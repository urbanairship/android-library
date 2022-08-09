/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.event.CheckboxEvent
import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.event.Event.ViewInit
import com.urbanairship.android.layout.event.EventType
import com.urbanairship.android.layout.model.Accessible.Companion.contentDescriptionFromJson
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ToggleStyle
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.testing.OpenForTesting
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

/**
 * Checkbox input for use within a `CheckboxController`.
 */
@OpenForTesting
internal class CheckboxModel(
    val reportingValue: JsonValue,
    style: ToggleStyle,
    contentDescription: String?,
    backgroundColor: Color?,
    border: Border?
) : CheckableModel(ViewType.CHECKBOX, style, contentDescription, backgroundColor, border) {

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

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): CheckboxModel =
            CheckboxModel(
                reportingValue = json.opt("reporting_value").toJsonValue(),
                style = toggleStyleFromJson(json),
                contentDescription = contentDescriptionFromJson(json),
                backgroundColor = backgroundColorFromJson(json),
                border = borderFromJson(json)
            )
    }
}
