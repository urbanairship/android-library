package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.BasicToggleLayoutInfo
import com.urbanairship.android.layout.info.FormValidationMode
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.hasFormInputHandler
import com.urbanairship.android.layout.reporting.ThomasFormField
import com.urbanairship.android.layout.view.ToggleLayoutView
import kotlinx.coroutines.launch

internal class BasicToggleLayoutModel(
    viewInfo: BasicToggleLayoutInfo,
    view: AnyModel,
    private val formState: ThomasForm,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseToggleLayoutModel<ToggleLayoutView<BasicToggleLayoutModel>, BasicToggleLayoutInfo>(viewInfo, view, formState, environment, properties) {

    override fun onCreateView(
        context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?
    ) = ToggleLayoutView(context, this, viewEnvironment, itemProperties, ToggleLayoutView.ToggleLayoutType.BASIC).apply {
        id = viewId
    }

    override fun onViewCreated(view: ToggleLayoutView<BasicToggleLayoutModel>) {
        super.onViewCreated(view)
        onFormInputDisplayed { isDisplayed ->
            formState.updateWithDisplayState(viewInfo.identifier, isDisplayed)
        }
    }

    override fun onViewAttached(view: ToggleLayoutView<BasicToggleLayoutModel>) {
        super.onViewAttached(view)
        // Update form state on every checked change.
        viewScope.launch {
            isOn.collect { isOn ->
                formState.updateFormInput(
                    value = ThomasFormField.Toggle(
                        identifier = viewInfo.identifier,
                        originalValue = isOn,
                        fieldType = ThomasFormField.FieldType.just(
                            value = isOn,
                            validator = { it || !viewInfo.isRequired },
                            attributes = ThomasFormField.makeAttributes(
                                name = viewInfo.attributeName,
                                value = viewInfo.attributeValue
                            )
                        )
                    ),
                    pageId = properties.pagerPageId
                )

                if (viewInfo.eventHandlers.hasFormInputHandler()) {
                    handleViewEvent(EventHandler.Type.FORM_INPUT, isOn)
                }
            }
        }

        if (formState.validationMode == FormValidationMode.ON_DEMAND) {
            wireValidationActions(
                identifier = viewInfo.identifier,
                thomasForm = formState,
                initialValue = isOn.value,
                valueUpdates = isOn,
                validatable = viewInfo
            )
        }
    }
}
