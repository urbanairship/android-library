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
import com.urbanairship.android.layout.property.hasCancel
import com.urbanairship.android.layout.property.hasCancelOrDismiss
import com.urbanairship.android.layout.property.hasFormSubmit
import com.urbanairship.android.layout.property.hasFormValidate
import com.urbanairship.android.layout.property.hasPagerNext
import com.urbanairship.android.layout.property.hasPagerPrevious
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.widget.TappableView
import kotlin.time.Duration.Companion.milliseconds
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
        viewScope.launch {
            view.taps().collect {
                listener?.setEnabled(enabled = false)

                val reportingContext = layoutState.reportingContext(buttonId = viewInfo.identifier)

                // Report button tap event.
                report(
                    event = ButtonTap(
                        data = ReportingEvent.ButtonTapData(
                            identifier = viewInfo.identifier,
                            reportingMetadata = viewInfo.reportingMetadata),
                        context = reportingContext
                    )
                )

                // Run any actions.
                runActions(viewInfo.actions, reportingContext)

                // Run any handlers for tap events.
                if (viewInfo.eventHandlers.hasTapHandler() && !viewInfo.clickBehaviors.hasFormSubmit) {
                    handleViewEvent(EventHandler.Type.TAP)
                }

                evaluateClickBehaviors(view.context ?: Airship.applicationContext)
                listener?.setEnabled(enabled = true)
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
                handlePagerNext()
            }
            if (viewInfo.clickBehaviors.hasPagerPrevious) {
                handlePagerPrevious()
            }
        }
    }

    private suspend fun handleSubmit(context: Context) {
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
                handlePagerNext()
            }
            if (viewInfo.clickBehaviors.hasPagerPrevious) {
                handlePagerPrevious()
            }
        }

        broadcast(submitEvent).join()
    }

    private suspend fun handleFormValidation(context: Context) {
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
                handlePagerNext()
            }
            if (viewInfo.clickBehaviors.hasPagerPrevious) {
                handlePagerPrevious()
            }
        }

        broadcast(validateEvent).join()
    }

    private suspend fun handlePagerNext() = broadcast(
        LayoutEvent.PagerNext(viewInfo.clickBehaviors.pagerNextFallback)
    ).join()

    private suspend fun handlePagerPrevious() = broadcast(LayoutEvent.PagerPrevious).join()

    private suspend fun handleDismiss(context: Context, isCancel: Boolean) {
        report(
            event = ReportingEvent.Dismiss(
                data = ReportingEvent.DismissData.ButtonTapped(
                    identifier = viewInfo.identifier,
                    description = reportingDescription(context),
                    cancel = isCancel
                ),
                displayTime = environment.displayTimer.time.milliseconds,
                context = layoutState.reportingContext(buttonId = viewInfo.identifier)
            )
        )

        broadcast(LayoutEvent.Finish).join()
    }
}
