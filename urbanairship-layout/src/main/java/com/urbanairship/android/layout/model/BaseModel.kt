/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View.OnAttachStateChangeListener
import androidx.annotation.VisibleForTesting
import com.urbanairship.Provider
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.Accessible
import com.urbanairship.android.layout.info.View
import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.StateAction
import com.urbanairship.android.layout.property.hasFormBehaviors
import com.urbanairship.android.layout.property.hasPagerBehaviors
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.reporting.AttributeName
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.util.debouncedClicks
import com.urbanairship.android.layout.util.resolveContentDescription
import com.urbanairship.android.layout.util.resolveOptional
import com.urbanairship.android.layout.widget.CheckableView
import com.urbanairship.android.layout.widget.TappableView
import com.urbanairship.json.JsonValue
import com.urbanairship.json.toJsonMap
import com.urbanairship.util.PlatformUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import android.view.View as AndroidView

internal typealias AnyModel = BaseModel<*, *, *>

internal data class ModelProperties(
    val pagerPageId: String?
)

internal abstract class BaseModel<T : AndroidView, I : View, L : BaseModel.Listener>(
    val viewInfo: I,
    protected val environment: ModelEnvironment,
    protected val properties: ModelProperties,
    private val platformProvider: Provider<Int> = Provider { UAirship.shared().platformType }
) {

    internal interface Listener {

        fun onStateUpdated(state: State.Layout) {}
        fun setBackground(old: Background?, new: Background)
        fun setVisibility(visible: Boolean)
        fun setEnabled(enabled: Boolean)
    }

    internal open var listener: L? = null

    val viewId: Int = AndroidView.generateViewId()

    private var background: Background? = null

    fun createView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ): T {
        background = null

        val view = onCreateView(context, viewEnvironment, itemProperties)
        onViewCreated(view)

        updateBackground()
        view.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: AndroidView) {
                setupViewListeners(view)
                onViewAttached(view)
            }

            override fun onViewDetachedFromWindow(v: AndroidView) {
                onViewDetached(view)

                // Stop child jobs for the view scope, but leave the scope itself running so that
                // we can handle re-attaches.
                viewJob.cancelChildren()
            }
        })

        if (viewInfo.enableBehaviors != null) {
            if (viewInfo.enableBehaviors.hasPagerBehaviors) {
                checkNotNull(layoutState.pager) { "Pager state is required for pager behaviors" }
                modelScope.launch {
                    layoutState.pager.changes.collect { handlePagerBehaviors(it) }
                }
            }

            if (viewInfo.enableBehaviors.hasFormBehaviors) {
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
        if (!viewInfo.type.isFormInput) return

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
        if (viewInfo.eventHandlers.hasTapHandler() && view !is TappableView && view !is CheckableView<*>) {
            viewScope.launch {
                view.debouncedClicks().collect { handleViewEvent(EventHandler.Type.TAP) }
            }
        }

        viewScope.launch {
            layoutState.layout?.changes?.collect {
                updateBackground(it)
                updateVisibility(it)
                listener?.onStateUpdated(it)
            }
        }
    }

    protected abstract fun onCreateView(
        context: Context, viewEnvironment: ViewEnvironment, itemProperties: ItemProperties?
    ): T

    open fun contentDescription(context: Context): String? {
        if (viewInfo is Accessible) {
            return context.resolveContentDescription(
                viewInfo.contentDescription, viewInfo.localizedContentDescription
            )
        }

        return null
    }

    protected open fun onViewCreated(view: T) = Unit

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    internal open fun onViewAttached(view: T) = Unit

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    internal open fun onViewDetached(view: T) = Unit

    protected val modelScope = environment.modelScope

    private val viewJob = SupervisorJob()
    internal val viewScope = CoroutineScope(Dispatchers.Main.immediate + viewJob)

    protected val layoutState = environment.layoutState

    protected fun report(event: ReportingEvent, state: LayoutData) =
        environment.reporter.report(event, state)

    protected fun runActions(
        actions: Map<String, JsonValue>?,
        state: LayoutData = layoutState.reportingContext()
    ) {
        val platform = PlatformUtils.asString(platformProvider.get())
        val mergedActions = actions.orEmpty().toMutableMap()

        mergedActions
            .remove(KEY_PLATFORM_OVERRIDE)
            ?.map
            ?.get(platform)
            ?.map
            ?.let { overrides ->
                overrides.forEach { mergedActions[it.key] = it.value }
            }

        environment.actionsRunner.run(mergedActions, state)
    }

    protected fun broadcast(event: LayoutEvent) = modelScope.launch {
        environment.eventHandler.broadcast(event)
    }

    protected fun registerChannels(channels: List<FormData.ChannelRegistration>) =
        environment.channelRegistrar.register(channels)

    protected fun updateAttributes(attributes: Map<AttributeName, AttributeValue>) =
        environment.attributeHandler.update(attributes)

    private fun updateBackground(state: State.Layout? = null) {
        val background = if (state != null) {
            Background(
                color = state.resolveOptional(
                    overrides = viewInfo.commonViewOverrides?.backgroundColor,
                    default = viewInfo.backgroundColor
                ), border = state.resolveOptional(
                    overrides = viewInfo.commonViewOverrides?.border, default = viewInfo.border
                )
            )
        } else {
            Background(color = viewInfo.backgroundColor, border = viewInfo.border)
        }

        if (background != this.background) {
            listener?.setBackground(this.background, background)
            this.background = background
        }
    }

    private fun updateVisibility(state: State.Layout) {
        val visibility = viewInfo.visibility ?: return

        val matcher = visibility.invertWhenStateMatcher
        val match = matcher.apply(state.state.toJsonMap())

        val isVisible = if (match) {
            !visibility.default
        } else {
            visibility.default
        }

        listener?.setVisibility(isVisible)
    }

    private fun handleFormBehaviors(state: State.Form) {
        val behaviors = viewInfo.enableBehaviors ?: return
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
        val behaviors = viewInfo.enableBehaviors ?: return
        val hasPagerNextBehavior = behaviors.contains(EnableBehaviorType.PAGER_NEXT)
        val hasPagerPrevBehavior = behaviors.contains(EnableBehaviorType.PAGER_PREVIOUS)

        val isEnabled =
            (hasPagerNextBehavior && state.hasNext) || (hasPagerPrevBehavior && state.hasPrevious)

        listener?.setEnabled(isEnabled)
    }

    fun handleViewEvent(type: EventHandler.Type, value: Any? = null) {
        for (handler in viewInfo.eventHandlers.orEmpty()) {
            if (handler.type == type) {
                runStateActions(handler.actions, value)
            }
        }
    }

    fun runStateActions(
        actions: List<StateAction>?,
        formValue: Any? = null
    ) = actions?.forEach { action ->
        when (action) {
            is StateAction.SetFormValue -> layoutState.layout?.let { state ->
                UALog.v("StateAction: SetFormValue ${action.key} = ${JsonValue.wrapOpt(formValue)}")
                state.update {
                    it.copy(state = it.state + (action.key to JsonValue.wrapOpt(formValue)))
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

    private companion object {
        private const val KEY_PLATFORM_OVERRIDE = "platform_action_overrides"
    }
}
