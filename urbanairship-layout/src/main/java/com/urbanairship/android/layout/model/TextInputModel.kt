/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.event.Event.ViewAttachedToWindow
import com.urbanairship.android.layout.event.FormEvent.DataChange
import com.urbanairship.android.layout.event.TextInputEvent
import com.urbanairship.android.layout.info.TextInputInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.FormInputType
import com.urbanairship.android.layout.property.TextInputTextAppearance
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.FormData.TextInput
import com.urbanairship.android.layout.reporting.LayoutData

internal class TextInputModel(
    val inputType: FormInputType,
    val textAppearance: TextInputTextAppearance,
    val hintText: String? = null,
    override val identifier: String,
    override val contentDescription: String? = null,
    override val isRequired: Boolean = false,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    environment: ModelEnvironment
) : BaseModel(
    viewType = ViewType.TEXT_INPUT,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment
), Identifiable, Accessible, Validatable {

    constructor(info: TextInputInfo, env: ModelEnvironment) : this(
        inputType = info.inputType,
        textAppearance = info.textAppearance,
        hintText = info.hintText,
        identifier = info.identifier,
        contentDescription = info.contentDescription,
        isRequired = info.isRequired,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        environment = env
    )

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
}
