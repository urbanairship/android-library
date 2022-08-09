/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.event.FormEvent.DataChange
import com.urbanairship.android.layout.event.ToggleEvent
import com.urbanairship.android.layout.model.Accessible.Companion.contentDescriptionFromJson
import com.urbanairship.android.layout.model.Identifiable.Companion.identifierFromJson
import com.urbanairship.android.layout.model.Validatable.Companion.requiredFromJson
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ToggleStyle
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.AttributeName
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

/**
 * Toggle input for use within a `FormController` or `NpsFormController`.
 */
internal class ToggleModel(
    override val identifier: String,
    style: ToggleStyle,
    private val attributeName: AttributeName?,
    private val attributeValue: JsonValue?,
    contentDescription: String?,
    override val isRequired: Boolean,
    backgroundColor: Color?,
    border: Border?
) : CheckableModel(
    ViewType.TOGGLE, style, contentDescription, backgroundColor, border
), Identifiable, Validatable {

    private var value: Boolean? = null

    override val isValid: Boolean
        get() = value == true || !isRequired

    override fun buildInputChangeEvent(isChecked: Boolean): Event =
        DataChange(
            FormData.Toggle(identifier, isChecked),
            isValid,
            attributeName,
            attributeValue
        )

    override fun buildInitEvent(): Event = ToggleEvent.Init(identifier, isValid)

    override fun onCheckedChange(isChecked: Boolean) {
        value = isChecked
        super.onCheckedChange(isChecked)
    }

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): ToggleModel =
            ToggleModel(
                identifier = identifierFromJson(json),
                style = toggleStyleFromJson(json),
                attributeName = AttributeName.attributeNameFromJson(json),
                attributeValue = json.opt("attribute_value"),
                contentDescription = contentDescriptionFromJson(json),
                isRequired = requiredFromJson(json),
                backgroundColor = backgroundColorFromJson(json),
                border = borderFromJson(json)
            )
    }
}
