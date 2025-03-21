/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.CheckboxControllerInfo
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.hasFormInputHandler
import com.urbanairship.android.layout.reporting.ThomasFormField
import com.urbanairship.json.JsonValue
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
            checkboxState.changes.collect { checkbox ->
                formState.updateFormInput(
                    value = ThomasFormField.CheckboxController(
                        identifier = checkbox.identifier,
                        originalValue = checkbox.selectedItems,
                        filedType = ThomasFormField.FiledType.just(
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

        modelScope.launch {
            formState.formUpdates.collect { form ->
                checkboxState.update { state ->
                    state.copy(isEnabled = form.isEnabled)
                }
            }
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
}
