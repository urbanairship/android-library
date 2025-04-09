/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ScoreInfo
import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.hasFormInputHandler
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.reporting.ThomasFormField
import com.urbanairship.android.layout.util.debouncedClicks
import com.urbanairship.android.layout.util.scoreChanges
import com.urbanairship.android.layout.view.ScoreView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Model for Score views.
 *
 * Must be a descendant of `FormController` or `NpsFormController`.
 *
 * Numbers will have an equal height/width and will scale to fill the container.
 * With auto width, the container will be up to 320dp. The top-level background and border apply to the entire widget.
 */
internal class ScoreModel(
    viewInfo: ScoreInfo,
    private val formState: ThomasForm,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<ScoreView, ScoreInfo, ScoreModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    interface Listener : BaseModel.Listener {

        fun onSetSelectedScore(value: Int?)
    }

    // used for on demand validation actions
    private val valuesUpdate = MutableStateFlow(-1)

    init {
        // Set the initial empty score value
        formState.updateFormInput(
            value = ThomasFormField.Score(
                identifier = viewInfo.identifier,
                originalValue = null,
                fieldType = ThomasFormField.FieldType.just(
                    value = -1,
                    validator = { it > 0 || !viewInfo.isRequired },
                    attributes = ThomasFormField.makeAttributes(
                        name = viewInfo.attributeName,
                        value = AttributeValue.NULL
                    )
                )
            ),
            pageId = properties.pagerPageId
        )
    }

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = ScoreView(context, this).apply {
        id = viewId

        // Restore state, if available
        formState.inputData<ThomasFormField.Score>(viewInfo.identifier)?.originalValue?.let {
            setSelectedScore(it)
        }
    }

    override fun onViewCreated(view: ScoreView) {
        super.onViewCreated(view)

        onFormInputDisplayed { isDisplayed ->
            formState.updateWithDisplayState(viewInfo.identifier, isDisplayed)
        }
    }

    override fun onViewAttached(view: ScoreView) {
        viewScope.launch {
            view.scoreChanges().collect { score ->
                formState.updateFormInput(
                    value = ThomasFormField.Score(
                        identifier = viewInfo.identifier,
                        originalValue = score,
                        fieldType = ThomasFormField.FieldType.just(
                            value = score,
                            validator = { (it > -1) || !viewInfo.isRequired },
                            attributes = ThomasFormField.makeAttributes(
                                name = viewInfo.attributeName,
                                value = AttributeValue.wrap(score)
                            )
                        )
                    ),
                    pageId = properties.pagerPageId
                )

                if (viewInfo.eventHandlers.hasFormInputHandler()) {
                    handleViewEvent(EventHandler.Type.FORM_INPUT, score)
                }

                valuesUpdate.update { score }
            }
        }

        if (viewInfo.eventHandlers.hasTapHandler()) {
            viewScope.launch {
                // Merge score item clicks with any clicks on the score view, outside the items.
                merge(
                    view.taps(),
                    view.debouncedClicks()
                ).collect { handleViewEvent(EventHandler.Type.TAP) }
            }
        }

        viewScope.launch {
            formState.formUpdates.collect { state ->
                listener?.setEnabled(state.isEnabled)
            }
        }

        wireValidationActions(
            thomasForm = formState,
            valueUpdates = valuesUpdate.asStateFlow(),
            actions = mapOf(
                ValidationAction.EDIT to viewInfo.onEdit,
                ValidationAction.VALID to viewInfo.onValid,
                ValidationAction.ERROR to viewInfo.onError
            ),
            isValid = { !viewInfo.isRequired || it > -1 }
        )
    }
}
