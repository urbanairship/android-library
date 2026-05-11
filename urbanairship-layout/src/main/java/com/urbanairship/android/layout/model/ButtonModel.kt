/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import androidx.annotation.CallSuper
import com.urbanairship.Airship
import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.event.ReportingEvent.ButtonTap
import com.urbanairship.android.layout.info.Button
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.Outcome
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.widget.TappableView
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal abstract class ButtonModel<T, I: Button>(
    viewInfo: I,
    private val formState: ThomasForm?,
    private val pagerState: SharedState<State.Pager>?,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<T, I, ButtonModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) where T : View, T : TappableView {

    open fun reportingDescription(context: Context): String {
        return contentDescription(context) ?: viewInfo.identifier
    }

    interface Listener : BaseModel.Listener {

        fun dismissSoftKeyboard()
    }

    override var listener: Listener? = null

    @CallSuper
    override fun onViewAttached(view: T) {
        pagerState?.let { state ->
            viewScope.launch {
                state.changes
                    .map { it.isScrolling }
                    .distinctUntilChanged()
                    .collect { isScrolling ->
                        listener?.setEnabled(!isScrolling)
                    }
            }
        }

        viewScope.launch {
            view.taps().collect {
                listener?.setEnabled(enabled = false)

                val reportingContext = layoutState.reportingContext(buttonId = viewInfo.identifier)

                report(
                    event = ButtonTap(
                        data = ReportingEvent.ButtonTapData(
                            identifier = viewInfo.identifier,
                            reportingMetadata = viewInfo.reportingMetadata),
                        context = reportingContext
                    )
                )

                evaluateOutcomes(view.context ?: Airship.application)

                if (viewInfo.enableBehaviors?.isNotEmpty() == true) {
                    val currentPager = layoutState.pager?.changes?.value
                    val currentForm = layoutState.thomasForm?.formUpdates?.value
                    val shouldBeEnabled = mapEnableBehavior(form = currentForm, pager = currentPager)
                    listener?.setEnabled(shouldBeEnabled)
                } else {
                    listener?.setEnabled(enabled = true)
                }
            }
        }
    }

    private suspend fun evaluateOutcomes(context: Context) {
        val tapHandlerOutcomes = viewInfo.eventHandlers
            .orEmpty()
            .filter { it.type == EventHandler.Type.TAP }
            .flatMap { it.outcomes.orEmpty() }
        val resolved = tapHandlerOutcomes + viewInfo.outcomes
        val delegation = buttonOutcomeHandler(context)

        val hasFormValidate = resolved.any {
            it is Outcome.Form && it.command == Outcome.Form.Command.VALIDATE
        }
        val hasFormSubmit = resolved.any {
            it is Outcome.Form && it.command == Outcome.Form.Command.SUBMIT
        }

        if (hasFormValidate) {
            handleFormValidation(context, resolved, hasFormSubmit)
        } else if (hasFormSubmit) {
            handleSubmit(context, resolved)
        } else {
            outcomeProcessor.process(resolved, handlerOutcome = delegation)
        }
    }

    private suspend fun handleSubmit(
        context: Context,
        resolved: List<Outcome>
    ) {
        listener?.dismissSoftKeyboard()

        val nonFormOutcomes = resolved.filter { it !is Outcome.Form }
        val delegation = buttonOutcomeHandler(context)
        val submitEvent = LayoutEvent.SubmitForm(buttonIdentifier = viewInfo.identifier) {
            outcomeProcessor.process(nonFormOutcomes, handlerOutcome = delegation)
        }

        broadcast(submitEvent).join()
    }

    private suspend fun handleFormValidation(
        context: Context,
        resolved: List<Outcome>,
        hasFormSubmit: Boolean
    ) {
        listener?.dismissSoftKeyboard()

        val delegation = buttonOutcomeHandler(context)
        val validateEvent = LayoutEvent.ValidateForm(buttonIdentifier = viewInfo.identifier) {
            if (hasFormSubmit) {
                handleSubmit(context, resolved)
            } else {
                val nonFormOutcomes = resolved.filter { it !is Outcome.Form }
                outcomeProcessor.process(nonFormOutcomes, handlerOutcome = delegation)
            }
        }

        broadcast(validateEvent).join()
    }

    /**
     * Button-specific delegation that overrides dismiss to include reporting
     * and async-view-reload to use the button's identifier.
     */
    private fun buttonOutcomeHandler(context: Context): suspend (HandlerOutcome) -> Unit = { outcome ->
        when (outcome) {
            is HandlerOutcome.Dismiss -> {
                report(
                    event = ReportingEvent.Dismiss(
                        data = ReportingEvent.DismissData.ButtonTapped(
                            identifier = viewInfo.identifier,
                            description = reportingDescription(context),
                            cancel = outcome.cancel
                        ),
                        displayTime = environment.displayTimer.time.milliseconds,
                        context = layoutState.reportingContext(buttonId = viewInfo.identifier)
                    )
                )
                environment.eventHandler.broadcast(LayoutEvent.Finish(cancel = outcome.cancel))
            }
            is HandlerOutcome.AsyncViewReload -> {
                environment.eventHandler.broadcast(
                    LayoutEvent.AsyncViewReload(viewInfo.identifier)
                )
            }
            else -> defaultHandler(outcome)
        }
    }
}
