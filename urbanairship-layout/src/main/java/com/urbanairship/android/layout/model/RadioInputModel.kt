/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.event.Event.ViewInit
import com.urbanairship.android.layout.event.EventType
import com.urbanairship.android.layout.event.RadioEvent
import com.urbanairship.android.layout.model.Accessible.Companion.contentDescriptionFromJson
import com.urbanairship.android.layout.model.BaseModel.Companion.backgroundColorFromJson
import com.urbanairship.android.layout.model.BaseModel.Companion.borderFromJson
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ToggleStyle
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

internal class RadioInputModel(
    style: ToggleStyle,
    /** Value for reports.  */
    val reportingValue: JsonValue,
    val attributeValue: JsonValue,
    contentDescription: String?,
    backgroundColor: Color?,
    border: Border?
) : CheckableModel(ViewType.RADIO_INPUT, style, contentDescription, backgroundColor, border) {

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

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): RadioInputModel =
            RadioInputModel(
                style = toggleStyleFromJson(json),
                reportingValue = json.opt("reporting_value"),
                attributeValue = json.opt("attribute_value"),
                contentDescription = contentDescriptionFromJson(json),
                backgroundColor = backgroundColorFromJson(json),
                border = borderFromJson(json)
            )
    }
}
