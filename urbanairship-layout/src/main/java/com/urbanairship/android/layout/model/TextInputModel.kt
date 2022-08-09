/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.event.Event.ViewAttachedToWindow
import com.urbanairship.android.layout.event.FormEvent.DataChange
import com.urbanairship.android.layout.event.TextInputEvent
import com.urbanairship.android.layout.model.Accessible.Companion.contentDescriptionFromJson
import com.urbanairship.android.layout.model.Identifiable.Companion.identifierFromJson
import com.urbanairship.android.layout.model.Validatable.Companion.requiredFromJson
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.FormInputType
import com.urbanairship.android.layout.property.TextInputTextAppearance
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.FormData.TextInput
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap

internal class TextInputModel(
    override val identifier: String,
    val inputType: FormInputType,
    val textAppearance: TextInputTextAppearance,
    val hintText: String?,
    override val contentDescription: String?,
    override val isRequired: Boolean,
    backgroundColor: Color?,
    border: Border?
) : BaseModel(ViewType.TEXT_INPUT, backgroundColor, border), Identifiable, Accessible, Validatable {

    final var value: String? = null
        private set

    override val isValid: Boolean
        get() = !isRequired || value.isNullOrEmpty().not()

    fun onConfigured() {
        bubbleEvent(TextInputEvent.Init(identifier, isValid), LayoutData.empty())
    }

    fun onAttachedToWindow() {
        bubbleEvent(ViewAttachedToWindow(this), LayoutData.empty())
    }

    fun onInputChange(value: String) {
        this.value = value
        bubbleEvent(DataChange(TextInput(identifier, value), isValid), LayoutData.empty())
    }

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): TextInputModel {
            val inputTypeString = json.opt("input_type").optString()
            val hintText = json.opt("place_holder").string
            val textAppearanceJson = json.opt("text_appearance").optMap()
            return TextInputModel(
                identifier = identifierFromJson(json),
                inputType = FormInputType.from(inputTypeString),
                textAppearance = TextInputTextAppearance.fromJson(textAppearanceJson),
                hintText = hintText,
                contentDescription = contentDescriptionFromJson(json),
                isRequired = requiredFromJson(json),
                backgroundColor = backgroundColorFromJson(json),
                border = borderFromJson(json)
            )
        }
    }
}
