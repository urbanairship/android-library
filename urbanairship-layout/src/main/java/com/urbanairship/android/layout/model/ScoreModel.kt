/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.environment.inputData
import com.urbanairship.android.layout.info.ScoreInfo
import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.hasFormInputHandler
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.android.layout.util.debouncedClicks
import com.urbanairship.android.layout.util.scoreChanges
import com.urbanairship.android.layout.view.ScoreView
import kotlinx.coroutines.flow.merge
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
    private val formState: SharedState<State.Form>,
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

    init {
        // Set the initial empty score value
        formState.update { state ->
            state.copyWithFormInput(
                FormData.Score(
                    identifier = viewInfo.identifier,
                    value = null,
                    isValid = !viewInfo.isRequired,
                    attributeName = viewInfo.attributeName,
                    attributeValue = AttributeValue.NULL
                )
            )
        }
    }

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = ScoreView(context, this).apply {
        id = viewId

        // Restore state, if available
        formState.inputData<FormData.Score>(viewInfo.identifier)?.value?.let {
            setSelectedScore(it)
        }
    }

    override fun onViewCreated(view: ScoreView) {
        super.onViewCreated(view)

        onFormInputDisplayed { isDisplayed ->
            formState.update { state ->
                state.copyWithDisplayState(viewInfo.identifier, isDisplayed)
            }
        }
    }

    override fun onViewAttached(view: ScoreView) {
        viewScope.launch {
            view.scoreChanges().collect { score ->
                formState.update { state ->
                    state.copyWithFormInput(
                        FormData.Score(
                            identifier = viewInfo.identifier,
                            value = score,
                            isValid = score > -1 || !viewInfo.isRequired,
                            attributeName = viewInfo.attributeName,
                            attributeValue = AttributeValue.wrap(score)
                        )
                    )
                }

                if (viewInfo.eventHandlers.hasFormInputHandler()) {
                    handleViewEvent(EventHandler.Type.FORM_INPUT, score)
                }
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
            formState.changes.collect { state ->
                listener?.setEnabled(state.isEnabled)
            }
        }
    }
}
