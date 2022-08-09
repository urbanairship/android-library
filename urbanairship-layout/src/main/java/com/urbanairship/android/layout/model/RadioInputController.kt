/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import androidx.annotation.VisibleForTesting
import com.urbanairship.android.layout.Thomas
import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.event.Event.ViewAttachedToWindow
import com.urbanairship.android.layout.event.Event.ViewInit
import com.urbanairship.android.layout.event.EventType
import com.urbanairship.android.layout.event.FormEvent.DataChange
import com.urbanairship.android.layout.event.RadioEvent
import com.urbanairship.android.layout.model.Accessible.Companion.contentDescriptionFromJson
import com.urbanairship.android.layout.model.Identifiable.Companion.identifierFromJson
import com.urbanairship.android.layout.model.Validatable.Companion.requiredFromJson
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.AttributeName
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.testing.OpenForTesting
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

/** Controller for radio inputs. */
@OpenForTesting
internal class RadioInputController(
    override val identifier: String,
    val view: BaseModel,
    private val attributeName: AttributeName?,
    override val isRequired: Boolean,
    override val contentDescription: String?
) : LayoutModel(ViewType.RADIO_INPUT_CONTROLLER), Identifiable, Accessible, Validatable {

    override val children: List<BaseModel> = listOf(view)

    override val isValid: Boolean
        get() = selectedValue != null || !isRequired

    @get:VisibleForTesting
    final var selectedValue: JsonValue? = null
        private set

    @get:VisibleForTesting
    val radioInputs: MutableList<RadioInputModel> = ArrayList()

    init {
        view.addListener(this)
    }

    override fun onEvent(event: Event, layoutData: LayoutData): Boolean =
        when (event.type) {
            EventType.VIEW_INIT ->
                onViewInit(event as ViewInit, layoutData)
            EventType.RADIO_INPUT_CHANGE ->
                onInputChange(event as RadioEvent.InputChange, layoutData)
            EventType.VIEW_ATTACHED ->
                onViewAttached(event as ViewAttachedToWindow, layoutData)
            // Pass along any other events
            else -> super.onEvent(event, layoutData)
        }

    private fun onViewInit(event: ViewInit, layoutData: LayoutData): Boolean =
        if (event.viewType == ViewType.RADIO_INPUT) {
            if (radioInputs.isEmpty()) {
                bubbleEvent(RadioEvent.ControllerInit(identifier, isValid), layoutData)
            }
            val model = event.model as RadioInputModel
            if (!radioInputs.contains(model)) {
                // This is the first time we've seen this radio input; Add it to our list.
                radioInputs.add(model)
            }
            true
        } else {
            false
        }

    private fun onInputChange(event: RadioEvent.InputChange, layoutData: LayoutData): Boolean {
        if (event.isChecked && event.value != selectedValue) {
            selectedValue = event.value
            trickleEvent(RadioEvent.ViewUpdate(event.value, event.isChecked), layoutData)
            bubbleEvent(
                DataChange(
                    FormData.RadioInputController(identifier, event.value),
                    isValid,
                    attributeName,
                    event.attributeValue
                ),
                layoutData
            )
        }
        return true
    }

    private fun onViewAttached(event: ViewAttachedToWindow, layoutData: LayoutData): Boolean {
        if (event.viewType == ViewType.RADIO_INPUT &&
            event.model is RadioInputModel &&
            selectedValue != null) {

            // Restore radio state.
            val value = (event.model as RadioInputModel).reportingValue
            if (selectedValue == value) {
                trickleEvent(RadioEvent.ViewUpdate(value, true), layoutData)
            }
        }
        // Always pass the event on.
        return super.onEvent(event, layoutData)
    }

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): RadioInputController {
            val viewJson = json.opt("view").optMap()
            return RadioInputController(
                identifier = identifierFromJson(json),
                view = Thomas.model(viewJson),
                attributeName = AttributeName.attributeNameFromJson(json),
                isRequired = requiredFromJson(json),
                contentDescription = contentDescriptionFromJson(json)
            )
        }
    }
}
