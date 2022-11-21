/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import android.view.View.OnAttachStateChangeListener
import androidx.annotation.VisibleForTesting
import com.urbanairship.Logger
import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.info.VisibilityInfo
import com.urbanairship.android.layout.property.Actions
import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.StateAction
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.reporting.AttributeName
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.util.debouncedClicks
import com.urbanairship.android.layout.util.toJsonMap
import com.urbanairship.android.layout.widget.CheckableView
import com.urbanairship.android.layout.widget.TappableView
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

internal typealias AnyModel = BaseModel<*, *>

internal abstract class BaseModel<T : View, L : BaseModel.Listener>(
    val viewType: ViewType,
    val backgroundColor: Color? = null,
    val border: Border? = null,
    val visibility: VisibilityInfo? = null,
    val eventHandlers: List<EventHandler>? = null,
    val enableBehaviors: List<EnableBehaviorType>? = null,
    protected val environment: ModelEnvironment
) {
    constructor(info: ViewInfo, environment: ModelEnvironment) : this(
        viewType = info.type,
        backgroundColor = info.backgroundColor,
        border = info.border,
        visibility = info.visibility,
        eventHandlers = info.eventHandlers,
        enableBehaviors = info.enableBehaviors,
        environment
    )

    internal interface Listener {
        fun setVisibility(visible: Boolean)
    }

    internal open var listener: L? = null

    val viewId: Int = View.generateViewId()

    fun createView(context: Context, viewEnvironment: ViewEnvironment): T {
        val view = onCreateView(context, viewEnvironment)
        onViewCreated(view)

        view.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                onViewAttached(view)
            }

            override fun onViewDetachedFromWindow(v: View) {
                // Stop child jobs for the view scope, but leave the scope itself running so that
                // we can handle re-attaches.
                viewJob.cancelChildren()
            }
        })

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

        return view
    }

    protected abstract fun onCreateView(context: Context, viewEnvironment: ViewEnvironment): T

    protected open fun onViewCreated(view: T) = Unit

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    internal open fun onViewAttached(view: T) = Unit

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

    fun handleViewEvent(type: EventHandler.Type, value: Any? = null) {
        for (handler in eventHandlers.orEmpty()) {
            if (handler.type == type) {
                for (action in handler.actions) {
                    when (action) {
                        is StateAction.SetFormValue -> layoutState.layout?.let { state ->
                           Logger.verbose("StateAction: SetFormValue ${action.key} = ${JsonValue.wrapOpt(value)}")
                            state.update {
                                it.copy(state = it.state + (action.key to JsonValue.wrapOpt(value)))
                            }
                        } ?: Logger.warn("StateAction: SetFormValue skipped. Missing State Controller!")

                        is StateAction.SetState -> layoutState.layout?.let { state ->
                            Logger.verbose("StateAction: SetState ${action.key} = ${action.value}")
                            state.update {
                                it.copy(state = it.state + (action.key to action.value))
                            }
                        } ?: Logger.warn("StateAction: SetState skipped. Missing State Controller!")

                        StateAction.ClearState -> layoutState.layout?.let { state ->
                            Logger.verbose("StateAction: ClearState")
                            state.update {
                                it.copy(state = emptyMap())
                            }
                        } ?: Logger.warn("StateAction: ClearState skipped. Missing State Controller!")
                    }
                }
            }
        }
    }
}
