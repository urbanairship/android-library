/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.view.View
import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.FormBehaviorType
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.FormData
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.launch

/**
 * Base model for top-level form controllers.
 *
 * @see FormController
 * @see NpsFormController
 */
internal abstract class BaseFormController<T : View>(
    viewType: ViewType,
    val identifier: String,
    val responseType: String?,
    submitBehavior: FormBehaviorType?,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo?,
    eventHandlers: List<EventHandler>?,
    enableBehaviors: List<EnableBehaviorType>?,
    private val formState: SharedState<State.Form>,
    private val parentFormState: SharedState<State.Form>?,
    environment: ModelEnvironment,
) : BaseModel<T, BaseModel.Listener>(
    viewType = viewType,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment
) {
    abstract val view: AnyModel
    abstract fun buildFormData(state: State.Form): FormData.BaseForm

    private val isChildForm = submitBehavior == null

    init {
        if (isChildForm) {
            initChildForm()
        } else {
            initParentForm()
        }
    }

    private fun initChildForm() {
        checkNotNull(parentFormState) { "Child form requires parent form state!" }

        modelScope.launch {
            formState.changes.collect { childState ->
                parentFormState.update { parentState ->
                    parentState.copyWithFormInput(buildFormData(childState))
                }
            }
        }
    }

    private fun initParentForm() {
        modelScope.launch {
            environment.layoutEvents
                .filterIsInstance<LayoutEvent.SubmitForm>()
                .combine(formState.changes, ::Pair)
                .filterNot { (_, form) -> form.isSubmitted }
                .collect { (event, form) ->
                    formState.update { state ->
                        val submitted = state.copy(isSubmitted = true)
                        val result = submitted.formResult()
                        report(
                            result,
                            layoutState.reportingContext(
                                formContext = form.reportingContext(),
                                buttonId = event.buttonIdentifier
                            )
                        )
                        updateAttributes(result.attributes)

                        // Mark the form state as submitted.
                        submitted
                    }

                    event.onSubmitted.invoke()
                }
        }

        modelScope.launch {
            formState.changes.collect { form ->
                if (!form.isDisplayReported) {
                    val formContext = form.reportingContext()
                    report(
                        ReportingEvent.FormDisplay(formContext),
                        layoutState.reportingContext(formContext)
                    )
                    formState.update { state ->
                        state.copy(isDisplayReported = true)
                    }
                }
            }
        }
    }
}
