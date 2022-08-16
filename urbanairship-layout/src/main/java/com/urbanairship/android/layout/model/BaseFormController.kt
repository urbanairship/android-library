/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.event.Event.ViewAttachedToWindow
import com.urbanairship.android.layout.event.EventType
import com.urbanairship.android.layout.event.FormEvent
import com.urbanairship.android.layout.event.FormEvent.DataChange
import com.urbanairship.android.layout.event.FormEvent.InputInit
import com.urbanairship.android.layout.event.FormEvent.ValidationUpdate
import com.urbanairship.android.layout.event.ReportingEvent.FormDisplay
import com.urbanairship.android.layout.event.ReportingEvent.FormResult
import com.urbanairship.android.layout.info.FormInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.FormBehaviorType
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.AttributeName
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.android.layout.reporting.FormInfo as FormReportingInfo
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.json.JsonValue

/**
 * Base model for top-level form controllers.
 *
 * @see FormController
 * @see NpsFormController
 */
internal abstract class BaseFormController<VI : FormInfo>(
    viewType: ViewType,
    override val identifier: String,
    val responseType: String?,
    private val submitBehavior: FormBehaviorType?,
    backgroundColor: Color? = null,
    border: Border? = null,
    environment: ModelEnvironment,
) : LayoutModel<VI>(viewType, backgroundColor, border, environment), Identifiable {

    protected abstract val formType: String
    protected abstract val initEvent: FormEvent.Init
    protected abstract val formDataChangeEvent: DataChange
    protected abstract val formResultEvent: FormResult

    abstract val view: BaseModel

    protected val formInfo: FormReportingInfo
        get() = FormReportingInfo(identifier, formType, responseType, isSubmitted)

    protected val formData: MutableMap<String, FormData<*>> = mutableMapOf()
    protected val attributes: MutableMap<AttributeName, JsonValue> = mutableMapOf()

    protected val isFormValid: Boolean
        get() = inputValidity.values.all { isValid -> isValid }

    private val hasSubmitBehavior: Boolean
        get() = submitBehavior != null

    private val inputValidity: MutableMap<String, Boolean> = HashMap()

    private var isDisplayReported = false
    private var isSubmitted = false

    override fun onEvent(event: Event, layoutData: LayoutData): Boolean {
        val dataOverride = layoutData.withFormInfo(formInfo)
        return when (event.type) {
            EventType.FORM_INIT -> {
                onNestedFormInit(event as FormEvent.Init)
                hasSubmitBehavior || super.onEvent(event, dataOverride)
            }
            EventType.FORM_INPUT_INIT -> {
                onInputInit(event as InputInit)
                true
            }
            EventType.FORM_DATA_CHANGE -> {
                onDataChange(event as DataChange)
                if (!hasSubmitBehavior) {
                    // Update parent controller if this is a child form
                    bubbleEvent(formDataChangeEvent, layoutData)
                }
                true
            }
            EventType.VIEW_ATTACHED -> {
                onViewAttached(event as ViewAttachedToWindow)
                if (hasSubmitBehavior) {
                    true
                } else super.onEvent(event, dataOverride)
            }
            EventType.BUTTON_BEHAVIOR_FORM_SUBMIT -> {
                // Submit form if this controller has a submit behavior.
                if (hasSubmitBehavior) {
                    onSubmit()
                    return true
                }
                // Otherwise update with our form data and let parent form controller handle it.
                super.onEvent(event, dataOverride)
            }
            else -> super.onEvent(event, dataOverride)
        }
    }

    private fun onSubmit() {
        isSubmitted = true
        bubbleEvent(formResultEvent, LayoutData.form(formInfo))
    }

    private fun onNestedFormInit(init: FormEvent.Init) {
        updateFormValidity(init.identifier, init.isValid)
    }

    private fun onInputInit(init: InputInit) {
        updateFormValidity(init.identifier, init.isValid)
        if (inputValidity.size == 1) {
            if (!hasSubmitBehavior) {
                // This is a nested form, since it has no submit behavior.
                // Bubble an init event to announce this form to a parent form controller.
                bubbleEvent(initEvent, LayoutData.form(formInfo))
            }
        }
    }

    private fun onViewAttached(attach: ViewAttachedToWindow) {
        if (attach.viewType.isFormInput && !isDisplayReported) {
            isDisplayReported = true
            val formInfo = formInfo
            bubbleEvent(FormDisplay(formInfo), LayoutData.form(formInfo))
        }
    }

    private fun onDataChange(data: DataChange) {
        val identifier = data.value.identifier
        val isValid = data.isValid
        if (isValid) {
            formData[identifier] = data.value
            attributes.putAll(data.attributes)
        } else {
            formData.remove(identifier)
            for (key in data.attributes.keys) {
                attributes.remove(key)
            }
        }
        updateFormValidity(identifier, isValid)
    }

    private fun updateFormValidity(inputId: String, isValid: Boolean) {
        inputValidity[inputId] = isValid
        trickleEvent(ValidationUpdate(isFormValid), LayoutData.form(formInfo))
    }
}
