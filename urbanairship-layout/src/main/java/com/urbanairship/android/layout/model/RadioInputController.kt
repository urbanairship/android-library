/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.RadioInputControllerInfo
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.hasFormInputHandler
import com.urbanairship.android.layout.reporting.ThomasFormField
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.launch

/** Controller for radio inputs. */
internal class RadioInputController(
    viewInfo: RadioInputControllerInfo,
    val view: AnyModel,
    private val formState: ThomasForm,
    private val radioState: SharedState<State.Radio>,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<View, RadioInputControllerInfo, BaseModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    init {
        // Listen to radio input state updates and push them into form state.
        modelScope.launch {
            radioState.changes.collect { radio ->
                formState.updateFormInput(
                    value = ThomasFormField.RadioInputController(
                        identifier = radio.identifier,
                        originalValue = radio.selectedItem,
                        filedType = ThomasFormField.FiledType.just(
                            value = radio.selectedItem ?: JsonValue.NULL,
                            validator = { !it.isNull || !viewInfo.isRequired },
                            attributes = ThomasFormField.makeAttributes(
                                name = viewInfo.attributeName,
                                value = radio.attributeValue
                            ),
                        )
                    ),
                    pageId = properties.pagerPageId
                )

                if (viewInfo.eventHandlers.hasFormInputHandler()) {
                    handleViewEvent(EventHandler.Type.FORM_INPUT, radio.selectedItem)
                }
            }
        }

        modelScope.launch {
            formState.formUpdates.collect { form ->
                radioState.update { it.copy(isEnabled = form.isEnabled) }
            }
        }
    }

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = view.createView(context, viewEnvironment, itemProperties)
}
