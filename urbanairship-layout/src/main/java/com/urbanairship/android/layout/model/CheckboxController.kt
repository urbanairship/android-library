/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import androidx.annotation.VisibleForTesting
import com.urbanairship.Logger
import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.event.CheckboxEvent
import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.event.Event.ViewAttachedToWindow
import com.urbanairship.android.layout.event.Event.ViewInit
import com.urbanairship.android.layout.event.EventType
import com.urbanairship.android.layout.event.FormEvent.DataChange
import com.urbanairship.android.layout.info.CheckboxControllerInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.json.JsonValue

/**
 * Controller for checkbox inputs.
 *
 * Must be a descendant of `FormController` or `NpsFormController`.
 */
internal class CheckboxController(
    val view: BaseModel,
    override val identifier: String,
    override val isRequired: Boolean = false,
    private val minSelection: Int = if (isRequired) 1 else 0,
    private val maxSelection: Int = Int.MAX_VALUE,
    override val contentDescription: String? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    environment: ModelEnvironment
) : LayoutModel<CheckboxControllerInfo>(
    viewType = ViewType.CHECKBOX_CONTROLLER,
    backgroundColor = backgroundColor,
    border = border,
    environment = environment
), Identifiable, Accessible, Validatable {
    constructor(info: CheckboxControllerInfo, env: ModelEnvironment) : this(
        view = env.modelProvider.create(info.view, env),
        identifier = info.identifier,
        isRequired = info.isRequired,
        minSelection = info.minSelection,
        maxSelection = info.maxSelection,
        contentDescription = info.contentDescription,
        backgroundColor = info.backgroundColor,
        border = info.border,
        environment = env
    )

    override val children: List<BaseModel> = listOf(view)

    override val isValid: Boolean
        get() {
            val count = selectedValues.size
            val isFilled = count in minSelection..maxSelection
            val isOptional = count == 0 && !isRequired
            return isFilled || isOptional
        }

    private val checkboxes: MutableList<CheckboxModel> = mutableListOf()
    private val selectedValues: MutableSet<JsonValue> = mutableSetOf()

    init {
        view.addListener(this)
    }

    @VisibleForTesting
    fun getCheckboxes(): List<CheckboxModel> = checkboxes.toList()

    @VisibleForTesting
    fun getSelectedValues(): Set<JsonValue> = selectedValues.toSet()

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
}
