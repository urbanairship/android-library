/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.event.FormEvent.DataChange
import com.urbanairship.android.layout.event.ToggleEvent
import com.urbanairship.android.layout.info.ToggleInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ToggleStyle
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.AttributeName
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.json.JsonValue

/**
 * Toggle input for use within a `FormController` or `NpsFormController`.
 */
internal class ToggleModel(
    override val identifier: String,
    toggleStyle: ToggleStyle,
    override val isRequired: Boolean = false,
    private val attributeName: AttributeName? = null,
    private val attributeValue: JsonValue? = null,
    contentDescription: String? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    environment: ModelEnvironment
) : CheckableModel(
    viewType = ViewType.TOGGLE,
    style = toggleStyle,
    toggleType = toggleStyle.type,
    contentDescription = contentDescription,
    backgroundColor = backgroundColor,
    border = border,
    environment = environment
), Identifiable, Validatable {

    constructor(info: ToggleInfo, env: ModelEnvironment) : this(
        identifier = info.identifier,
        toggleStyle = info.style,
        isRequired = info.isRequired,
        attributeName = info.attributeName,
        attributeValue = info.attributeValue,
        contentDescription = info.contentDescription,
        backgroundColor = info.backgroundColor,
        border = info.border,
        environment = env
    )

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
}
