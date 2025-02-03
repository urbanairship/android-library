/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.view.View
import com.urbanairship.UALog
import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.FormInfo
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.hasFormBehaviors
import com.urbanairship.android.layout.property.hasPagerBehaviors
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.android.layout.util.DelicateLayoutApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Base model for top-level form controllers.
 *
 * @see FormController
 * @see NpsFormController
 */
internal abstract class BaseFormController<T : View, I : FormInfo>(
    viewInfo: I,
    private val formState: SharedState<State.Form>,
    private val parentFormState: SharedState<State.Form>?,
    private val pagerState: SharedState<State.Pager>?,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<T, I, BaseModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    abstract val view: AnyModel
    abstract fun buildFormData(state: State.Form): FormData.BaseForm

    private val isChildForm = viewInfo.submitBehavior == null

    init {
        if (isChildForm) {
            initChildForm()
        } else {
            initParentForm()
        }

        viewInfo.formEnabled?.let { behaviors ->
            if (behaviors.hasPagerBehaviors) {
                checkNotNull(pagerState) {
                    "Pager state is required for Forms with pager enable behaviors!"
                }
                modelScope.launch {
                    pagerState.changes.collect { state ->
                        handlePagerScroll(state)
                    }
                }
            }

            if (behaviors.hasFormBehaviors) {
                modelScope.launch {
                    formState.changes.collect { state ->
                        handleFormUpdate(state)
                    }
                }
            }
        }
    }

    private fun initChildForm() {
        checkNotNull(parentFormState) { "Child form requires parent form state!" }

        // Update the parent form with the child form's data whenever it changes.
        modelScope.launch {
            formState.changes.collect { childState ->
                parentFormState.update { parentState ->
                    parentState.copyWithFormInput(buildFormData(childState))
                }
            }
        }

        // Inherit the parent form's enabled and submitted states whenever they change.
        modelScope.launch {
            parentFormState.changes.collect { parentState ->
                formState.update { childState ->
                    var updated = childState
                    if (parentState.isSubmitted) {
                        updated = updated.copy(isSubmitted = true)
                    }
                    if (!parentState.isEnabled) {
                        updated = updated.copy(isEnabled = false)
                    }
                    updated
                }
            }
        }

        // Update the parent form with the child form's display state, whenever it changes.
        onFormInputDisplayed { isDisplayed ->
            parentFormState.update { state ->
                state.copyWithDisplayState(viewInfo.identifier, isDisplayed)
            }
        }
    }

    private fun initParentForm() {
        modelScope.launch {
            environment.layoutEvents.filterIsInstance<LayoutEvent.SubmitForm>()
                // We want to combine the submit event with the latest form state, but don't want
                // to receive updates from the form state changes flow, so we're using a map here
                // instead of a combine.
                .map {
                    @OptIn(DelicateLayoutApi::class) it to formState.value
                }.distinctUntilChanged().collect { (event, form) ->
                    if (!form.isSubmitted) {
                        formState.update { state ->
                            val submitted = state.copy(isSubmitted = true)
                            val result = submitted.formResult()
                            report(
                                event = result, state = layoutState.reportingContext(
                                    formContext = form.reportingContext(),
                                    buttonId = event.buttonIdentifier
                                )
                            )
                            updateAttributes(result.attributes)
                            registerChannels(result.channels)
                            // Mark the form state as submitted.
                            submitted
                        }
                    }
                    event.onSubmitted.invoke()
                }
        }

        modelScope.launch {
            formState.changes.collect { form ->
                // Bail out if we've already reported the form display.
                if (form.isDisplayReported) return@collect

                // Report if any inputs are displayed, otherwise wait for a future state change.
                if (form.displayedInputs.isNotEmpty()) {
                    val formContext = form.reportingContext()
                    report(
                        event = ReportingEvent.FormDisplay(formContext),
                        state = layoutState.reportingContext(formContext)
                    )
                    formState.update { state ->
                        state.copy(isDisplayReported = true)
                    }
                    // Now that we've reported, we can stop collecting form state changes.
                    cancel("Successfully reported form display.")
                } else {
                    UALog.v("Skipped form display reporting! No inputs are currently displayed.")
                }
            }
        }
    }

    @OptIn(DelicateLayoutApi::class)
    private fun handleFormUpdate(state: State.Form) {
        val behaviors = viewInfo.formEnabled ?: return

        val isParentEnabled = parentFormState?.value?.isEnabled ?: true
        val hasFormValidationBehavior = behaviors.contains(EnableBehaviorType.FORM_VALIDATION)
        val hasFormSubmitBehavior = behaviors.contains(EnableBehaviorType.FORM_SUBMISSION)
        val isValid = !hasFormValidationBehavior || state.isValid

        val isEnabled = isParentEnabled && when {
            hasFormSubmitBehavior && hasFormValidationBehavior -> !state.isSubmitted && isValid
            hasFormSubmitBehavior -> !state.isSubmitted
            hasFormValidationBehavior -> isValid
            else -> state.isEnabled
        }

        formState.update {
            it.copy(isEnabled = isEnabled)
        }
    }

    private fun handlePagerScroll(state: State.Pager) {
        val behaviors = viewInfo.formEnabled ?: return

        @OptIn(DelicateLayoutApi::class) val isParentEnabled =
            parentFormState?.value?.isEnabled ?: true
        val hasPagerNextBehavior = behaviors.contains(EnableBehaviorType.PAGER_NEXT)
        val hasPagerPrevBehavior = behaviors.contains(EnableBehaviorType.PAGER_PREVIOUS)

        val isEnabled =
            isParentEnabled && (hasPagerNextBehavior && hasPagerPrevBehavior && (state.hasNext || state.hasPrevious)) || (hasPagerNextBehavior && state.hasNext) || (hasPagerPrevBehavior && state.hasPrevious)

        formState.update {
            it.copy(isEnabled = isEnabled)
        }
    }
}
