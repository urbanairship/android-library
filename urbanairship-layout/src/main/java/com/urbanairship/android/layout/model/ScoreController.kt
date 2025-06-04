package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.FormValidationMode
import com.urbanairship.android.layout.info.ScoreControllerInfo
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.hasFormInputHandler
import com.urbanairship.android.layout.reporting.ThomasFormField
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class ScoreController(
    viewInfo: ScoreControllerInfo,
    val view: AnyModel,
    private val formState: ThomasForm,
    private val scoreState: SharedState<State.Score>,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<View, ScoreControllerInfo, BaseModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {
    init {
        modelScope.launch {
            formState.formUpdates.collect { form ->
                scoreState.update { it.copy(isEnabled = form.isEnabled) }
            }
        }

        if (formState.validationMode == FormValidationMode.ON_DEMAND) {
            wireValidationActions(
                identifier = viewInfo.identifier,
                thomasForm = formState,
                initialValue = scoreState.changes.value.selectedItem,
                valueUpdates = scoreState.changes.map { it.selectedItem },
                validatable = viewInfo
            )
        }
    }

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = view.createView(context, viewEnvironment, itemProperties)

    private fun isValid(selectedItem: JsonValue?): Boolean {
        return if (selectedItem == null || selectedItem.isNull) {
            !viewInfo.isRequired
        } else {
            true
        }
    }

    override fun onViewAttached(view: View) {
        super.onViewAttached(view)

        // Listen to radio input state updates and push them into form state.
        viewScope.launch {
            scoreState.changes.collect { score ->
                formState.updateFormInput(
                    value = ThomasFormField.ScoreInputController(
                        identifier = score.identifier,
                        originalValue = score.selectedItem?.reportingValue,
                        fieldType = ThomasFormField.FieldType.just(
                            value = score.selectedItem?.reportingValue ?: JsonValue.NULL,
                            validator = { isValid(it) },
                            attributes = ThomasFormField.makeAttributes(
                                name = viewInfo.attributeName,
                                value = score.selectedItem?.attributeValue
                            ),
                        )
                    ),
                    pageId = properties.pagerPageId
                )

                if (viewInfo.eventHandlers.hasFormInputHandler()) {
                    handleViewEvent(EventHandler.Type.FORM_INPUT, score.selectedItem?.reportingValue)
                }
            }
        }
    }
}
