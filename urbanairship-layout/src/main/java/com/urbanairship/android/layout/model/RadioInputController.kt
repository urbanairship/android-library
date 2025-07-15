/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.FormValidationMode
import com.urbanairship.android.layout.info.RadioInputControllerInfo
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.hasFormInputHandler
import com.urbanairship.android.layout.reporting.ThomasFormField
import com.urbanairship.android.layout.view.RadioInputView
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.flow.map
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

    // Keep reference to container view for accessibility updates
    private var containerView: View? = null

    init {
        modelScope.launch {
            formState.formUpdates.collect { form ->
                radioState.update { it.copy(isEnabled = form.isEnabled) }
            }
        }

        if (formState.validationMode == FormValidationMode.ON_DEMAND) {
            wireValidationActions(
                identifier = viewInfo.identifier,
                thomasForm = formState,
                initialValue = radioState.changes.value.selectedItem,
                valueUpdates = radioState.changes.map { it.selectedItem },
                validatable = viewInfo
            )
        }
    }

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ): View {
        val containerView = view.createView(context, viewEnvironment, itemProperties)
        this.containerView = containerView
        return containerView
    }

    private fun isValid(selectedItem: JsonValue?): Boolean {
        return if (selectedItem == null || selectedItem.isNull) {
            !viewInfo.isRequired
        } else {
            true
        }
    }

    private fun setupAccessibility(containerView: View) {
        // Make the container focusable so TalkBack can select it
        containerView.isFocusable = true
        // Ensure the container is important for accessibility
        containerView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        // Set up accessibility for the radio button group
        ViewCompat.setAccessibilityDelegate(containerView, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = RadioGroup::class.java.name

                info.contentDescription = host.context.getString(com.urbanairship.R.string.ua_accessibility_radio_group)
            }
        })
    }

    override fun onViewAttached(view: View) {
        super.onViewAttached(view)

        // Set up accessibility after views are attached
        setupAccessibility(view)

        // Listen to radio input state updates and push them into form state.
        viewScope.launch {
            radioState.changes.collect { radio ->
                formState.updateFormInput(
                    value = ThomasFormField.RadioInputController(
                        identifier = radio.identifier,
                        originalValue = radio.selectedItem?.reportingValue,
                        fieldType = ThomasFormField.FieldType.just(
                            value = radio.selectedItem?.reportingValue ?: JsonValue.NULL,
                            validator = { isValid(it) },
                            attributes = ThomasFormField.makeAttributes(
                                name = viewInfo.attributeName,
                                value = radio.selectedItem?.attributeValue
                            ),
                        )
                    ),
                    pageId = properties.pagerPageId
                )

                if (viewInfo.eventHandlers.hasFormInputHandler()) {
                    handleViewEvent(EventHandler.Type.FORM_INPUT, radio.selectedItem?.reportingValue)
                }
            }
        }
    }
}
