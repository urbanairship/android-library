/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.environment.ThomasFormStatus
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.CheckboxControllerInfo
import com.urbanairship.android.layout.info.FormValidationMode
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.hasFormInputHandler
import com.urbanairship.android.layout.reporting.ThomasFormField
import com.urbanairship.android.layout.reporting.ThomasFormFieldStatus
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Controller for checkbox inputs.
 *
 * Must be a descendant of `FormController` or `NpsFormController`.
 */
internal class CheckboxController(
    viewInfo: CheckboxControllerInfo,
    val view: AnyModel,
    private val formState: ThomasForm,
    private val checkboxState: SharedState<State.Checkbox>,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<View, CheckboxControllerInfo, BaseModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    init {

        modelScope.launch {
            formState.formUpdates.collect { form ->
                checkboxState.update { state ->
                    state.copy(isEnabled = form.isEnabled)
                }
            }
        }

        if (formState.validationMode == FormValidationMode.ON_DEMAND) {
            wireValidationActions(
                identifier = viewInfo.identifier,
                thomasForm = formState,
                initialValue = checkboxState.changes.value.selectedItems,
                valueUpdates = checkboxState.changes.map { it.selectedItems },
                actions = mapOf(
                    ValidationAction.VALID to viewInfo.onValid,
                    ValidationAction.EDIT to viewInfo.onEdit,
                    ValidationAction.ERROR to viewInfo.onError
                )
            )
        }
    }

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = view.createView(context, viewEnvironment, itemProperties)

    private fun isValid(selectedItems: Set<JsonValue>): Boolean {
        val count = selectedItems.size
        val isFilled = count in viewInfo.minSelection..viewInfo.maxSelection
        val isOptional = count == 0 && !viewInfo.isRequired
        return isFilled || isOptional
    }

    override fun onViewAttached(view: View) {
        super.onViewAttached(view)

        viewScope.launch {
            checkboxState.changes.collect { checkbox ->
                formState.updateFormInput(
                    value = ThomasFormField.CheckboxController(
                        identifier = checkbox.identifier,
                        originalValue = checkbox.selectedItems,
                        fieldType = ThomasFormField.FieldType.just(
                            value = checkbox.selectedItems,
                            validator = { isValid(it) }
                        )
                    ),
                    pageId = properties.pagerPageId
                )

                if (viewInfo.eventHandlers.hasFormInputHandler()) {
                    handleViewEvent(EventHandler.Type.FORM_INPUT, checkbox.selectedItems.toList())
                }
            }
        }

    }
}
