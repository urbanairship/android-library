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
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.event.ReportingEvent.ButtonTap
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.ButtonClickBehaviorType
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.TapEffect
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.property.hasCancel
import com.urbanairship.android.layout.property.hasCancelOrDismiss
import com.urbanairship.android.layout.property.hasFormSubmit
import com.urbanairship.android.layout.property.hasPagerNext
import com.urbanairship.android.layout.property.hasPagerPrevious
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.util.DelicateLayoutApi
import com.urbanairship.android.layout.widget.TappableView
import com.urbanairship.json.JsonValue
import java.lang.Integer.max
import java.lang.Integer.min
import kotlinx.coroutines.launch

internal abstract class ButtonModel<T>(
    viewType: ViewType,
    val identifier: String,
    val actions: Map<String, JsonValue>?,
    private val clickBehaviors: List<ButtonClickBehaviorType>,
    val tapEffect: TapEffect,
    backgroundColor: Color?,
    border: Border?,
    visibility: VisibilityInfo?,
    eventHandlers: List<EventHandler>?,
    enableBehaviors: List<EnableBehaviorType>?,
    private val reportingMetadata: JsonValue?,
    private val formState: SharedState<State.Form>?,
    private val pagerState: SharedState<State.Pager>?,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<T, ButtonModel.Listener>(
    viewType = viewType,
    backgroundColor = backgroundColor,
    border = border,
    visibility = visibility,
    eventHandlers = eventHandlers,
    enableBehaviors = enableBehaviors,
    environment = environment,
    properties = properties
) where T : View, T : TappableView {
    abstract fun contentDescription(context: Context): String?

    abstract fun reportingDescription(context: Context): String

    interface Listener : BaseModel.Listener {
        fun dismissSoftKeyboard()
    }

    override var listener: Listener? = null

    @CallSuper
    override fun onViewAttached(view: T) {
        viewScope.launch {
            view.taps().collect {
                val reportingContext = layoutState.reportingContext(buttonId = identifier)

                // Report button tap event.
                report(ButtonTap(identifier, reportingMetadata), reportingContext)

                // Run any actions.
                if (!actions.isNullOrEmpty()) {
                    runActions(actions, reportingContext)
                }

                // Run any handlers for tap events.
                if (eventHandlers.hasTapHandler()) {
                    handleViewEvent(EventHandler.Type.TAP)
                }

                evaluateClickBehaviors(view.context ?: UAirship.getApplicationContext())
            }
        }
    }

    private suspend fun evaluateClickBehaviors(context: Context) {
        if (clickBehaviors.hasFormSubmit) {
            // If we have a FORM_SUBMIT behavior, handle it first and then
            // handle the rest of the behaviors after submitting.
            handleSubmit(context)
        } else if (clickBehaviors.hasCancelOrDismiss) {
            // If there's only a CANCEL or DISMISS, and no FORM_SUBMIT, handle
            // immediately. We don't need to handle pager behaviors, as the layout
            // will be dismissed.
            handleDismiss(context, clickBehaviors.hasCancel)
        } else {
            // No FORM_SUBMIT, CANCEL, or DISMISS, so we only need to
            // handle pager behaviors.
            if (clickBehaviors.hasPagerNext) {
                handlePagerNext(context, fallback = clickBehaviors.pagerNextFallback)
            }
            if (clickBehaviors.hasPagerPrevious) {
                handlePagerPrevious()
            }
        }
    }

    private fun handleSubmit(context: Context) {
        // Dismiss the keyboard, if it's open.
        listener?.dismissSoftKeyboard()

        val submitEvent = LayoutEvent.SubmitForm(
            buttonIdentifier = identifier,
            onSubmitted = {
                // After submitting, handle the rest of the behaviors.
                if (clickBehaviors.hasCancelOrDismiss) {
                    handleDismiss(context, clickBehaviors.hasCancel)
                }
                if (clickBehaviors.hasPagerNext) {
                    handlePagerNext(context, fallback = clickBehaviors.pagerNextFallback)
                }
                if (clickBehaviors.hasPagerPrevious) {
                    handlePagerPrevious()
                }
            }
        )
        modelScope.launch {
            environment.eventHandler.broadcast(submitEvent)
        }
    }

    private suspend fun handlePagerNext(context: Context, fallback: PagerNextFallback) {
        checkNotNull(pagerState) {
            "Pager state is required for Buttons with pager click behaviors!"
        }

        fun pagerNext() {
            pagerState.update { state ->
                state.copyWithPageIndex(min(state.pageIndex + 1, state.pageIds.size - 1))
            }
        }

        @OptIn(DelicateLayoutApi::class)
        val hasNext = pagerState.value.hasNext

        when {
            !hasNext && fallback == PagerNextFallback.FIRST ->
                pagerState.update { state ->
                    state.copyWithPageIndexAndResetProgress(0)
                }
            !hasNext && fallback == PagerNextFallback.DISMISS ->
                handleDismiss(context, isCancel = false)
            else ->
                pagerNext()
        }
    }

    private fun handlePagerPrevious() {
        checkNotNull(pagerState) {
            "Pager state is required for Buttons with pager click behaviors!"
        }
        pagerState.update { state ->
            state.copyWithPageIndex(max(state.pageIndex - 1, 0))
        }
    }

    private suspend fun handleDismiss(context: Context, isCancel: Boolean) {
        report(
            ReportingEvent.DismissFromButton(
                identifier,
                reportingDescription(context),
                isCancel,
                environment.displayTimer.time
            ),
            layoutState.reportingContext(buttonId = identifier)
        )
        modelScope.launch {
            environment.eventHandler.broadcast(LayoutEvent.Finish)
        }
    }
}
