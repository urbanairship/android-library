/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import androidx.annotation.VisibleForTesting
import com.urbanairship.Logger
import com.urbanairship.android.layout.Thomas
import com.urbanairship.android.layout.event.CheckboxEvent
import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.event.Event.ViewAttachedToWindow
import com.urbanairship.android.layout.event.Event.ViewInit
import com.urbanairship.android.layout.event.EventType
import com.urbanairship.android.layout.event.FormEvent.DataChange
import com.urbanairship.android.layout.model.Accessible.Companion.contentDescriptionFromJson
import com.urbanairship.android.layout.model.Identifiable.Companion.identifierFromJson
import com.urbanairship.android.layout.model.Validatable.Companion.requiredFromJson
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

/**
 * Controller for checkbox inputs.
 *
 * Must be a descendant of `FormController` or `NpsFormController`.
 */
internal class CheckboxController(
    override val identifier: String,
    val view: BaseModel,
    private val minSelection: Int,
    private val maxSelection: Int,
    override val isRequired: Boolean,
    override val contentDescription: String?
) : LayoutModel(ViewType.CHECKBOX_CONTROLLER), Identifiable, Accessible, Validatable {

    private val checkboxes: MutableList<CheckboxModel> = mutableListOf()
    private val selectedValues: MutableSet<JsonValue> = mutableSetOf()

    init {
        view.addListener(this)
    }

    override val children: List<BaseModel> = listOf(view)

    override val isValid: Boolean
        get() {
            val count = selectedValues.size
            val isFilled = count in minSelection..maxSelection
            val isOptional = count == 0 && !isRequired
            return isFilled || isOptional
        }

    @VisibleForTesting
    fun getCheckboxes(): List<CheckboxModel> = checkboxes

    @VisibleForTesting
    fun getSelectedValues(): Set<JsonValue> = selectedValues

    override fun onEvent(event: Event, layoutData: LayoutData): Boolean =
        when (event.type) {
            EventType.VIEW_INIT ->
                onViewInit(event as ViewInit, layoutData)
            EventType.CHECKBOX_INPUT_CHANGE ->
                onCheckboxInputChange(event as CheckboxEvent.InputChange, layoutData)
            EventType.VIEW_ATTACHED ->
                onViewAttached(event as ViewAttachedToWindow, layoutData)
            // Pass along any other events
            else -> super.onEvent(event, layoutData)
        }

    private fun onViewInit(event: ViewInit, layoutData: LayoutData): Boolean =
        if (event.viewType == ViewType.CHECKBOX) {
            if (checkboxes.isEmpty()) {
                bubbleEvent(CheckboxEvent.ControllerInit(identifier, isValid), layoutData)
            }
            val model = event.model as CheckboxModel
            if (!checkboxes.contains(model)) {
                // This is the first time we've seen this checkbox; Add it to our list.
                checkboxes.add(model)
            }
            true
        } else {
            false
        }

    private fun onCheckboxInputChange(
        event: CheckboxEvent.InputChange,
        layoutData: LayoutData
    ): Boolean =
        if (event.isChecked && selectedValues.size + 1 > maxSelection) {
            // Can't check any more boxes, so we'll ignore it and consume the event.
            Logger.debug(
                "Ignoring checkbox input change for '%s'. Max selections reached!",
                event.value
            )
            true
        } else {
            if (event.isChecked) {
                selectedValues.add(event.value)
            } else {
                selectedValues.remove(event.value)
            }

            trickleEvent(CheckboxEvent.ViewUpdate(event.value, event.isChecked), layoutData)
            bubbleEvent(
                DataChange(FormData.CheckboxController(identifier, selectedValues), isValid),
                layoutData
            )
            true
        }

    private fun onViewAttached(event: ViewAttachedToWindow, layoutData: LayoutData): Boolean {
        if (event.viewType == ViewType.CHECKBOX &&
            event.model is CheckboxModel &&
            selectedValues.isNotEmpty()) {

            val model = event.model as CheckboxModel
            val isChecked = selectedValues.contains(model.reportingValue)
            trickleEvent(CheckboxEvent.ViewUpdate(model.reportingValue, isChecked), layoutData)
        }
        // Always pass the event on.
        return super.onEvent(event, layoutData)
    }

    companion object {

        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): CheckboxController {
            val viewJson = json.opt("view").optMap()
            val isRequired = requiredFromJson(json)
            return CheckboxController(
                identifier = identifierFromJson(json),
                view = Thomas.model(viewJson),
                minSelection = json.opt("min_selection").getInt(if (isRequired) 1 else 0),
                maxSelection = json.opt("max_selection").getInt(Int.MAX_VALUE),
                isRequired = isRequired,
                contentDescription = contentDescriptionFromJson(json)
            )
        }
    }
}
