/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import android.view.View.OnAttachStateChangeListener
import androidx.annotation.VisibleForTesting
import com.urbanairship.UALog
import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Actions
import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.StateAction
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.property.hasFormBehaviors
import com.urbanairship.android.layout.property.hasPagerBehaviors
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.reporting.AttributeName
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.util.debouncedClicks
import com.urbanairship.android.layout.widget.CheckableView
import com.urbanairship.android.layout.widget.TappableView
import com.urbanairship.json.JsonValue
import com.urbanairship.json.toJsonMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

internal typealias AnyModel = BaseModel<*, *>

internal data class ModelProperties(
    val pagerPageId: String?
)

internal abstract class BaseModel<T : View, L : BaseModel.Listener>(
    val viewType: ViewType,
    val backgroundColor: Color? = null,
    val border: Border? = null,
    val visibility: VisibilityInfo? = null,
    val eventHandlers: List<EventHandler>? = null,
    val enableBehaviors: List<EnableBehaviorType>? = null,
    protected val environment: ModelEnvironment,
    protected val properties: ModelProperties,
) {
    internal interface Listener {
        fun setVisibility(visible: Boolean)
        fun setEnabled(enabled: Boolean)
    }

    internal open var listener: L? = null

    val viewId: Int = View.generateViewId()

    fun createView(context: Context, viewEnvironment: ViewEnvironment): T {
        val view = onCreateView(context, viewEnvironment)
        onViewCreated(view)

        view.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                setupViewListeners(view)
                onViewAttached(view)
            }

            override fun onViewDetachedFromWindow(v: View) {
                onViewDetached(view)

                // Stop child jobs for the view scope, but leave the scope itself running so that
                // we can handle re-attaches.
                viewJob.cancelChildren()
            }
        })

        if (enableBehaviors != null) {
            if (enableBehaviors.hasPagerBehaviors) {
                checkNotNull(layoutState.pager) { "Pager state is required for pager behaviors" }
                modelScope.launch {
                    layoutState.pager.changes.collect { handlePagerBehaviors(it) }
                }
            }

            if (enableBehaviors.hasFormBehaviors) {
                checkNotNull(layoutState.form) { "Form state is required for form behaviors" }
                modelScope.launch {
                    layoutState.form.changes.collect { handleFormBehaviors(it) }
                }
            }
        }

        return view
    }

    /**
     * Helper for form input models that allows them to listen for when they are displayed
     * in the current Pager page.
     *
     * If this model is not in a Pager, the block will always be called with `isDisplayed = true`.
     *
     * This method is a no-op for models that are not form inputs or form input controllers.
     */
    protected fun onFormInputDisplayed(block: suspend (isDisplayed: Boolean) -> Unit) {
        if (!viewType.isFormInput) return

        modelScope.launch {
            var isDisplayed = false

            layoutState.pager?.changes?.collect { state ->
                val currentPageId = state.pageIds[state.pageIndex]
                val wasDisplayed = isDisplayed
                isDisplayed = currentPageId == properties.pagerPageId
                if (wasDisplayed != isDisplayed) {
                    block(isDisplayed)
                }
                // If we don't have pager state, the model is always considered to be "displayed".
            } ?: block(true)
        }
    }

    private fun setupViewListeners(view: T) {
        // Apply tap handler for any models that don't implement their own click handling.
        if (eventHandlers.hasTapHandler() && view !is TappableView && view !is CheckableView<*>) {
            viewScope.launch {
                view.debouncedClicks()
                    .collect { handleViewEvent(EventHandler.Type.TAP) }
            }
        }

        // Listen to layout state changes in order to determine visibility
        if (visibility != null) {
            viewScope.launch {
                layoutState.layout?.changes?.collect {
                    val isVisible = checkVisibility(it)
                    listener?.setVisibility(isVisible)
                }
            }
        }
    }

    protected abstract fun onCreateView(context: Context, viewEnvironment: ViewEnvironment): T

    protected open fun onViewCreated(view: T) = Unit

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    internal open fun onViewAttached(view: T) = Unit

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    internal open fun onViewDetached(view: T) = Unit

    protected val modelScope = environment.modelScope

    private val viewJob = SupervisorJob()
    protected val viewScope = CoroutineScope(Dispatchers.Main.immediate + viewJob)

    protected val layoutState = environment.layoutState

    protected fun report(event: ReportingEvent, state: LayoutData) =
        environment.reporter.report(event, state)

    protected fun runActions(
        actions: Actions,
        state: LayoutData = layoutState.reportingContext()
    ) = environment.actionsRunner.run(actions, state)

    protected fun broadcast(event: LayoutEvent) =
        modelScope.launch {
            environment.eventHandler.broadcast(event)
        }

    protected fun updateAttributes(attributes: Map<AttributeName, AttributeValue>) =
        environment.attributeHandler.update(attributes)

    private fun checkVisibility(state: State.Layout): Boolean {
        val matcher = visibility?.invertWhenStateMatcher ?: return true
        val match = matcher.apply(state.state.toJsonMap())

        return if (match) {
                visibility.default
            } else {
                !visibility.default
            }
    }

    private fun handleFormBehaviors(state: State.Form) {
        val behaviors = enableBehaviors ?: return
        val hasFormValidationBehavior = behaviors.contains(EnableBehaviorType.FORM_VALIDATION)
        val hasFormSubmitBehavior = behaviors.contains(EnableBehaviorType.FORM_SUBMISSION)
        val isValid = !hasFormValidationBehavior || state.isValid

        val isEnabled = when {
            hasFormSubmitBehavior && hasFormValidationBehavior -> !state.isSubmitted && isValid
            hasFormSubmitBehavior -> !state.isSubmitted
            hasFormValidationBehavior -> isValid
            else -> true
        }

        listener?.setEnabled(isEnabled)
    }

    private fun handlePagerBehaviors(state: State.Pager) {
        val behaviors = enableBehaviors ?: return
        val hasPagerNextBehavior = behaviors.contains(EnableBehaviorType.PAGER_NEXT)
        val hasPagerPrevBehavior = behaviors.contains(EnableBehaviorType.PAGER_PREVIOUS)

        val isEnabled = (hasPagerNextBehavior && state.hasNext) ||
                (hasPagerPrevBehavior && state.hasPrevious)

        listener?.setEnabled(isEnabled)
    }

    fun handleViewEvent(type: EventHandler.Type, value: Any? = null) {
        for (handler in eventHandlers.orEmpty()) {
            if (handler.type == type) {
                for (action in handler.actions) {
                    when (action) {
                        is StateAction.SetFormValue -> layoutState.layout?.let { state ->
                           UALog.v("StateAction: SetFormValue ${action.key} = ${JsonValue.wrapOpt(value)}")
                            state.update {
                                it.copy(state = it.state + (action.key to JsonValue.wrapOpt(value)))
                            }
                        } ?: UALog.w("StateAction: SetFormValue skipped. Missing State Controller!")

                        is StateAction.SetState -> layoutState.layout?.let { state ->
                            UALog.v("StateAction: SetState ${action.key} = ${action.value}")
                            state.update {
                                it.copy(state = it.state + (action.key to action.value))
                            }
                        } ?: UALog.w("StateAction: SetState skipped. Missing State Controller!")

                        StateAction.ClearState -> layoutState.layout?.let { state ->
                            UALog.v("StateAction: ClearState")
                            state.update {
                                it.copy(state = emptyMap())
                            }
                        } ?: UALog.w("StateAction: ClearState skipped. Missing State Controller!")
                    }
                }
            }
        }
    }
}
