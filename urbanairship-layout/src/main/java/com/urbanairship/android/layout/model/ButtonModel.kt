/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.view.View
import androidx.annotation.CallSuper
import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.event.ReportingEvent.ButtonTap
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.ButtonClickBehaviorType
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.widget.TappableView
import com.urbanairship.json.JsonValue
import java.lang.Integer.max
import java.lang.Integer.min
import kotlinx.coroutines.launch

internal abstract class ButtonModel<T>(
    viewType: ViewType,
    val identifier: String,
    val actions: Map<String, JsonValue>? = null,
    private val clickBehaviors: List<ButtonClickBehaviorType>,
    val contentDescription: String? = null,
    backgroundColor: Color? = null,
    border: Border? = null,
    visibility: VisibilityInfo? = null,
    eventHandlers: List<EventHandler>? = null,
    enableBehaviors: List<EnableBehaviorType>? = null,
    private val formState: SharedState<State.Form>?,
    private val pagerState: SharedState<State.Pager>?,
    environment: ModelEnvironment
) : BaseModel<T, ButtonModel.Listener>(
    viewType = viewType,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment
) where T : View, T : TappableView {
    abstract val reportingDescription: String

    interface Listener : BaseModel.Listener {
        fun setEnabled(isEnabled: Boolean)
    }

    override var listener: Listener? = null
        set(listener) {
            field = listener
            listener?.setEnabled(isEnabled())
        }

    private var isEnabled = true

    init {
        val hasPagerEnableBehaviors = enableBehaviors.orEmpty().any {
            it == EnableBehaviorType.PAGER_NEXT || it == EnableBehaviorType.PAGER_PREVIOUS
        }
        val hasFormEnableBehaviors = enableBehaviors.orEmpty().any {
            it == EnableBehaviorType.FORM_VALIDATION || it == EnableBehaviorType.FORM_SUBMISSION
        }

        if (hasPagerEnableBehaviors) {
            checkNotNull(pagerState) {
                "Pager state is required for Buttons with pager enable behaviors!"
            }
            modelScope.launch {
                pagerState.changes.collect { state ->
                    handlePagerScroll(state.hasNext, state.hasPrevious)
                }
            }
        }

        if (hasFormEnableBehaviors) {
            checkNotNull(formState) {
                "Form state is required for Buttons with form enable behaviors!"
            }
            modelScope.launch {
                formState.changes.collect { state ->
                    handleFormUpdate(state)
                }
            }
        }
    }

    @CallSuper
    override fun onViewAttached(view: T) {
        viewScope.launch {
            view.taps().collect {
                val reportingContext = layoutState.reportingContext(buttonId = identifier)

                // Report button tap event.
                report(ButtonTap(identifier), reportingContext)

                // Run any actions.
                if (!actions.isNullOrEmpty()) {
                    runActions(actions, reportingContext)
                }

                // Run any handlers for tap events.
                if (eventHandlers.hasTapHandler()) {
                    handleViewEvent(EventHandler.Type.TAP)
                }

                evaluateClickBehaviors()
            }
        }
    }

    private suspend fun evaluateClickBehaviors() {
        // If we have a FORM_SUBMIT behavior, handle it first.
        if (clickBehaviors.hasFormSubmit) {
            val submitEvent = LayoutEvent.SubmitForm(
                buttonIdentifier = identifier,
                onSubmitted = {
                    // If there's also a CANCEL or DISMISS, pass along a block
                    // so we can handle it after the FORM_SUBMIT has completed.
                    if (clickBehaviors.hasCancelOrDismiss) {
                      handleDismiss(clickBehaviors.hasCancel)
                    }
                }
            )
            modelScope.launch {
                environment.eventHandler.broadcast(submitEvent)
            }
        } else if (clickBehaviors.hasCancelOrDismiss) {
            // If there's only a CANCEL or DISMISS, and no FORM_SUBMIT, handle
            // immediately.
            handleDismiss(clickBehaviors.hasCancel)
        }

        if (clickBehaviors.hasPagerNext) {
            checkNotNull(pagerState) {
                "Pager state is required for Buttons with pager click behaviors!"
            }
            pagerState.update { state ->
                state.copyWithPageIndex(min(state.pageIndex + 1, state.pages.size - 1))
            }
        }

        if (clickBehaviors.hasPagerPrevious) {
            checkNotNull(pagerState) {
                "Pager state is required for Buttons with pager click behaviors!"
            }
            pagerState.update { state ->
                state.copyWithPageIndex(max(state.pageIndex - 1, 0))
            }
        }
    }

    private suspend fun handleDismiss(isCancel: Boolean) {
        report(
            ReportingEvent.DismissFromButton(
                identifier,
                reportingDescription,
                isCancel,
                environment.displayTimer.time
            ),
            layoutState.reportingContext(buttonId = identifier)
        )
        modelScope.launch {
            environment.eventHandler.broadcast(LayoutEvent.Finish)
        }
    }

    private fun isEnabled(): Boolean {
        return enableBehaviors.isNullOrEmpty() || isEnabled
    }

    private fun handleFormUpdate(state: State.Form) {
        val behaviors = enableBehaviors.orEmpty()
        val hasFormValidationBehavior = behaviors.contains(EnableBehaviorType.FORM_VALIDATION)
        val hasFormSubmitBehavior = behaviors.contains(EnableBehaviorType.FORM_SUBMISSION)

        val isValid = !hasFormValidationBehavior || state.isValid
        val isSubmitted = !hasFormSubmitBehavior || state.isSubmitted
        val enabled = isValid && isSubmitted

        isEnabled = enabled
        listener?.setEnabled(enabled)
    }

    private fun handlePagerScroll(hasNext: Boolean, hasPrevious: Boolean) {
        val behaviors = enableBehaviors.orEmpty()
        val hasPagerNextBehavior = behaviors.contains(EnableBehaviorType.PAGER_NEXT)
        val hasPagerPrevBehavior = behaviors.contains(EnableBehaviorType.PAGER_PREVIOUS)

        if (hasPagerNextBehavior) {
            isEnabled = hasNext
            listener?.setEnabled(hasNext)
        }
        if (hasPagerPrevBehavior) {
            isEnabled = hasPrevious
            listener?.setEnabled(hasPrevious)
        }
    }
}

// Click behavior helper extensions

private val List<ButtonClickBehaviorType>.hasFormSubmit: Boolean
    get() = contains(ButtonClickBehaviorType.FORM_SUBMIT)

private val List<ButtonClickBehaviorType>.hasDismiss: Boolean
    get() = contains(ButtonClickBehaviorType.DISMISS)

private val List<ButtonClickBehaviorType>.hasCancel: Boolean
    get() = contains(ButtonClickBehaviorType.CANCEL)

private val List<ButtonClickBehaviorType>.hasCancelOrDismiss: Boolean
    get() = hasCancel || hasDismiss

private val List<ButtonClickBehaviorType>.hasPagerNext: Boolean
    get() = contains(ButtonClickBehaviorType.PAGER_NEXT)

private val List<ButtonClickBehaviorType>.hasPagerPrevious: Boolean
    get() = contains(ButtonClickBehaviorType.PAGER_PREVIOUS)
