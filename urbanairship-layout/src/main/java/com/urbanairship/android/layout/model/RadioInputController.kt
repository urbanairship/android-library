/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.RadioInputControllerInfo
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.hasFormInputHandler
import com.urbanairship.android.layout.reporting.FormData
import kotlinx.coroutines.launch

/** Controller for radio inputs. */
internal class RadioInputController(
    viewInfo: RadioInputControllerInfo,
    val view: AnyModel,
    private val formState: SharedState<State.Form>,
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
                formState.update { form ->
                    form.copyWithFormInput(
                        FormData.RadioInputController(
                            identifier = radio.identifier,
                            value = radio.selectedItem,
                            isValid = radio.selectedItem != null || !viewInfo.isRequired,
                            attributeName = viewInfo.attributeName,
                            attributeValue = radio.attributeValue
                        )
                    )
                }

                if (viewInfo.eventHandlers.hasFormInputHandler()) {
                    handleViewEvent(EventHandler.Type.FORM_INPUT, radio.selectedItem)
                }
            }
        }

        modelScope.launch {
            formState.changes.collect { form ->
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
