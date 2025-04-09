/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.view.View
import com.urbanairship.UALog
import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.environment.ThomasFormStatus
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.FormInfo
import com.urbanairship.android.layout.info.FormValidationMode
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.hasFormBehaviors
import com.urbanairship.android.layout.property.hasPagerBehaviors
import com.urbanairship.android.layout.reporting.ThomasFormField
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
    private val formState: ThomasForm,
    private val parentFormState: ThomasForm?,
    private val pagerState: SharedState<State.Pager>?,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<T, I, BaseModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    abstract val view: AnyModel
    abstract fun buildFormData(state: State.Form): ThomasFormField.BaseForm

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
                    formState.formUpdates.collect { state ->
                        handleFormUpdate(state)
                    }
                }
            }
        }

        wireFormValidation()
    }

    private fun initChildForm() {
        checkNotNull(parentFormState) { "Child form requires parent form state!" }

        // Update the parent form with the child form's data whenever it changes.
        modelScope.launch {
            environment.layoutEvents
                .filterIsInstance<LayoutEvent.SubmitForm>()
                .map { formState.formUpdates.value }
                .collect {
                    parentFormState.updateFormInput(buildFormData(it), pageId = properties.pagerPageId)
                    formState.validate()
                }
        }

        // Inherit the parent form's enabled and submitted states whenever they change.
        modelScope.launch {
            parentFormState.formUpdates.collect { parentState ->
                formState.updateStatus(
                    isSubmitted = if (parentState.status.isSubmitted) { true } else { null },
                    isEnabled = if (!parentState.isEnabled) { false } else { null }
                )
            }
        }

        // Update the parent form with the child form's display state, whenever it changes.
        onFormInputDisplayed { isDisplayed ->
            parentFormState.updateWithDisplayState(viewInfo.identifier, isDisplayed)
        }
    }

    private fun initParentForm() {
        modelScope.launch {
            environment.layoutEvents.filterIsInstance<LayoutEvent.SubmitForm>()
                // We want to combine the submit event with the latest form state, but don't want
                // to receive updates from the form state changes flow, so we're using a map here
                // instead of a combine.
                .map {
                    it to formState.prepareSubmit()
                }.distinctUntilChanged().collect { (event, formResult) ->
                    formResult?.let {
                        val (result, context) = formResult
                        report(
                            event = result,
                            state = layoutState.reportingContext(
                                formContext = context,
                                buttonId = event.buttonIdentifier
                            )
                        )

                        updateAttributes(result.attributes)
                        registerChannels(result.channels)
                        event.onSubmitted.invoke()
                    }
                }
        }

        modelScope.launch {
            formState.formUpdates.collect { form ->
                // Bail out if we've already reported the form display.
                if (form.isDisplayReported) return@collect

                // Report if any inputs are displayed, otherwise wait for a future state change.
                if (form.displayedInputs.isNotEmpty()) {
                    val formContext = form.reportingContext()
                    report(
                        event = ReportingEvent.FormDisplay(formContext),
                        state = layoutState.reportingContext(formContext)
                    )
                    formState.displayReported()

                    // Now that we've reported, we can stop collecting form state changes.
                    cancel("Successfully reported form display.")
                } else {
                    UALog.v("Skipped form display reporting! No inputs are currently displayed.")
                }
            }
        }
    }

    private fun wireFormValidation() {
        modelScope.launch {
            environment.layoutEvents
                .filterIsInstance<LayoutEvent.ValidateForm>()
                .collect {
                    if (formState.formUpdates.value.isSubmitted) {
                        return@collect
                    }

                    if (formState.validate()) {
                        it.onValidated()
                    }
                }
        }
    }

    private fun handleFormUpdate(state: State.Form) {
        val behaviors = viewInfo.formEnabled ?: return

        val isParentEnabled = parentFormState?.isEnabled ?: true
        val hasFormValidationBehavior = behaviors.contains(EnableBehaviorType.FORM_VALIDATION)
        val hasFormSubmitBehavior = behaviors.contains(EnableBehaviorType.FORM_SUBMISSION)
        val isValid = !hasFormValidationBehavior || state.status == ThomasFormStatus.VALID

        val isEnabled = isParentEnabled && when {
            hasFormSubmitBehavior && hasFormValidationBehavior -> !state.isSubmitted && isValid
            hasFormSubmitBehavior -> !state.isSubmitted
            hasFormValidationBehavior -> isValid
            else -> state.isEnabled
        }

        formState.updateStatus(isEnabled = isEnabled)
    }

    private fun handlePagerScroll(state: State.Pager) {
        if (state.isScrollDisabled) { return  }
        val behaviors = viewInfo.formEnabled ?: return

        val isParentEnabled = parentFormState?.isEnabled ?: true
        val hasPagerNextBehavior = behaviors.contains(EnableBehaviorType.PAGER_NEXT)
        val hasPagerPrevBehavior = behaviors.contains(EnableBehaviorType.PAGER_PREVIOUS)

        val isEnabled =
            isParentEnabled &&
                    (hasPagerNextBehavior && hasPagerPrevBehavior && (state.hasNext || state.hasPrevious)) ||
                    (hasPagerNextBehavior && state.hasNext) ||
                    (hasPagerPrevBehavior && state.hasPrevious)

        formState.updateStatus(isEnabled = isEnabled)
    }
}
