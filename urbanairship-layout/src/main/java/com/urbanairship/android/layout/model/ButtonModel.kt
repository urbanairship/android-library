/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import androidx.annotation.CallSuper
import com.urbanairship.UAirship
import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.event.ReportingEvent.ButtonTap
import com.urbanairship.android.layout.info.Button
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.hasCancel
import com.urbanairship.android.layout.property.hasCancelOrDismiss
import com.urbanairship.android.layout.property.hasFormSubmit
import com.urbanairship.android.layout.property.hasFormValidate
import com.urbanairship.android.layout.property.hasPagerNext
import com.urbanairship.android.layout.property.hasPagerPrevious
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.widget.TappableView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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
    private var isProcessing = MutableStateFlow(false)

    @CallSuper
    override fun onViewAttached(view: T) {
        viewScope.launch {
            view.taps().collect {

                val reportingContext = layoutState.reportingContext(buttonId = viewInfo.identifier)

                // Report button tap event.
                report(ButtonTap(viewInfo.identifier, viewInfo.reportingMetadata), reportingContext)

                // Run any actions.
                runActions(viewInfo.actions, reportingContext)

                // Run any handlers for tap events.
                if (viewInfo.eventHandlers.hasTapHandler() && !viewInfo.clickBehaviors.hasFormSubmit) {
                    handleViewEvent(EventHandler.Type.TAP)
                }

                evaluateClickBehaviors(view.context ?: UAirship.getApplicationContext())
            }
        }

        // If we're processing, disable the button, via the enabled listener.
        viewScope.launch {
            isProcessing.collect {
                listener?.setEnabled(enabled = !it)
            }
        }
    }

    private suspend fun evaluateClickBehaviors(context: Context) {
        if (viewInfo.clickBehaviors.hasFormValidate) {
            // If we have a FORM_VALIDATE behavior, handle it first and then
            // handle the rest of the behaviors after validating.
            handleFormValidation(context)
        } else if (viewInfo.clickBehaviors.hasFormSubmit) {
            // If we have a FORM_SUBMIT behavior, handle it first and then
            // handle the rest of the behaviors after submitting.
            handleSubmit(context)
        } else if (viewInfo.clickBehaviors.hasCancelOrDismiss) {
            // If there's only a CANCEL or DISMISS, and no FORM_SUBMIT, handle
            // immediately. We don't need to handle pager behaviors, as the layout
            // will be dismissed.
            handleDismiss(context, viewInfo.clickBehaviors.hasCancel)
        } else {
            // No FORM_SUBMIT, CANCEL, or DISMISS, so we only need to
            // handle pager behaviors.
            if (viewInfo.clickBehaviors.hasPagerNext) {
                handlePagerNext(context, fallback = viewInfo.clickBehaviors.pagerNextFallback)
            }
            if (viewInfo.clickBehaviors.hasPagerPrevious) {
                handlePagerPrevious()
            }
        }
    }

    private fun handleSubmit(context: Context) {
        // Dismiss the keyboard, if it's open.
        listener?.dismissSoftKeyboard()

        val submitEvent = LayoutEvent.SubmitForm(buttonIdentifier = viewInfo.identifier) {
            // Run any handlers for tap events.
            if (viewInfo.eventHandlers.hasTapHandler()) {
                handleViewEvent(EventHandler.Type.TAP)
            }

            // After submitting, handle the rest of the behaviors.
            if (viewInfo.clickBehaviors.hasCancelOrDismiss) {
                handleDismiss(context, viewInfo.clickBehaviors.hasCancel)
            }
            if (viewInfo.clickBehaviors.hasPagerNext) {
                handlePagerNext(context, fallback = viewInfo.clickBehaviors.pagerNextFallback)
            }
            if (viewInfo.clickBehaviors.hasPagerPrevious) {
                handlePagerPrevious()
            }
        }

        isProcessing.update { true }
        modelScope.launch {
            broadcast(submitEvent)
            isProcessing.update { false }
        }
    }

    private fun handleFormValidation(context: Context) {
        // Dismiss the keyboard, if it's open.
        listener?.dismissSoftKeyboard()

        val validateEvent = LayoutEvent.ValidateForm(buttonIdentifier = viewInfo.identifier) {
            // After validating, handle the rest of the behaviors.
            if (viewInfo.clickBehaviors.hasFormSubmit) {
                handleSubmit(context)
            }
            if (viewInfo.clickBehaviors.hasCancelOrDismiss) {
                handleDismiss(context, viewInfo.clickBehaviors.hasCancel)
            }
            if (viewInfo.clickBehaviors.hasPagerNext) {
                handlePagerNext(context, fallback = viewInfo.clickBehaviors.pagerNextFallback)
            }
            if (viewInfo.clickBehaviors.hasPagerPrevious) {
                handlePagerPrevious()
            }
        }

        isProcessing.update { true }
        modelScope.launch {
            broadcast(validateEvent)
            isProcessing.update { false }
        }
    }

    private suspend fun handlePagerNext(context: Context, fallback: PagerNextFallback) {
        checkNotNull(pagerState) {
            "Pager state is required for Buttons with pager click behaviors!"
        }

        if (pagerState.changes.first().hasNext) {
           pagerState.update { it.copyWithPageRequest(PageRequest.NEXT) }
        } else {
            when(fallback) {
                PagerNextFallback.NONE -> {}
                PagerNextFallback.DISMISS -> handleDismiss(context, isCancel = false)
                PagerNextFallback.FIRST -> pagerState.update { it.copyWithPageRequest(PageRequest.FIRST) }
            }
        }
    }

    private fun handlePagerPrevious() {
        checkNotNull(pagerState) {
            "Pager state is required for Buttons with pager click behaviors!"
        }
        pagerState.update { it.copyWithPageRequest(PageRequest.BACK) }
    }

    private suspend fun handleDismiss(context: Context, isCancel: Boolean) {
        report(
            ReportingEvent.DismissFromButton(
                viewInfo.identifier,
                reportingDescription(context),
                isCancel,
                environment.displayTimer.time
            ),
            layoutState.reportingContext(buttonId = viewInfo.identifier)
        )
        modelScope.launch {
            environment.eventHandler.broadcast(LayoutEvent.Finish)
        }
    }
}
