package com.urbanairship.android.layout.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import androidx.annotation.RestrictTo
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.urbanairship.UALog
import com.urbanairship.actions.Action
import com.urbanairship.actions.DefaultActionRunner
import com.urbanairship.actions.run
import com.urbanairship.android.layout.EmbeddedPresentation
import com.urbanairship.android.layout.ModelFactoryException
import com.urbanairship.android.layout.R
import com.urbanairship.android.layout.display.DisplayArgs
import com.urbanairship.android.layout.environment.DefaultViewEnvironment
import com.urbanairship.android.layout.environment.ExternalReporter
import com.urbanairship.android.layout.environment.Reporter
import com.urbanairship.android.layout.environment.ThomasActionRunner
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.android.layout.property.ConstrainedSize
import com.urbanairship.android.layout.property.EmbeddedPlacement
import com.urbanairship.android.layout.reporting.DisplayTimer
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.util.getActivity
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.json.JsonValue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object ThomasLayoutViewFactory {

    private object SimpleViewModelStore : ViewModelStore()
    private object SimpleViewModelStoreOwner : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore
            get() = SimpleViewModelStore
    }

    private val viewToTimer = MutableStateFlow<Map<String, DisplayTimer>>(mapOf())

    public fun createView(
        context: Context,
        displayArgs: DisplayArgs,
        viewId: String
    ): View? {

        val activity = context.getActivity() ?: run {
            UALog.e { "SimpleLayoutView must be hosted by an Activity! Current Activity is null." }
            return null
        }

        if (activity !is LifecycleOwner) {
            UALog.e { "SimpleLayoutView must be hosted by an Activity that implements LifecycleOwner!" }
            return null
        }

        val timer = DisplayTimer(activity, 0)
        viewToTimer.update { it + (viewId to timer) }

        val reportDismiss = {
            displayArgs.listener.onDismiss(false)
            clear()
        }

        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                // Clean up the view model store when the activity is destroyed.
                reportDismiss()
                activity.lifecycle.removeObserver(this)
            }
        })

        val viewEnvironment: ViewEnvironment = DefaultViewEnvironment(
            activity = activity,
            activityMonitor = displayArgs.inAppActivityMonitor,
            webViewClientFactory = displayArgs.webViewClientFactory,
            imageCache = displayArgs.imageCache,
            isIgnoringSafeAreas = false // Embedded views never ignore safe areas?
        )

        val viewModelProvider = ViewModelProvider(SimpleViewModelStoreOwner)
        val viewModel = viewModelProvider[viewId, LayoutViewModel::class.java]

        val reporter = ExternalReporter(displayArgs.listener)

        try {
            val modelEnvironment = viewModel.getOrCreateEnvironment(
                reporter = reporter,
                actionRunner = displayArgs.actionRunner,
                displayTimer = timer
            )
            val model = viewModel.getOrCreateModel(
                viewInfo = displayArgs.payload.view,
                modelEnvironment = modelEnvironment
            )

            // Create the embedded view using our theme, to prevent app custom themes from affecting
            // the embedded view.
            val themedContext = ContextThemeWrapper(context, R.style.UrbanAirship_Layout)
            val embeddedView = ThomasEmbeddedView(
                context = themedContext,
                model = model,
                presentation = presentation,
                environment = viewEnvironment,
                fillWidth = true,
                fillHeight = true
            )

            embeddedView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    reporter.onVisibilityChanged(true, displayArgs.inAppActivityMonitor.isAppForegrounded)
                }

                override fun onViewDetachedFromWindow(v: View) {
                    reporter.onVisibilityChanged(false, displayArgs.inAppActivityMonitor.isAppForegrounded)
                }
            })

            embeddedView.listener = object : ThomasEmbeddedView.Listener {
                override fun onDismissed() {
                    reportDismiss()
                }
            }

            return embeddedView
        } catch (e: ModelFactoryException) {
            UALog.e("Failed to load model!", e)
            return null
        }
    }

    public fun clear() {
        SimpleViewModelStore.clear()
        viewToTimer.value = mapOf()
    }

    public fun calculateDisplayTime(viewId: String): Duration {
        return viewToTimer.value[viewId]?.time?.milliseconds ?: Duration.ZERO
    }

    private val presentation = EmbeddedPresentation(
        defaultPlacement = EmbeddedPlacement(
            size = ConstrainedSize(
                width = "100%",
                height = "100%",
                minWidth = null,
                minHeight = null,
                maxWidth = null,
                maxHeight = null),
            margin = null,
            border = null,
            backgroundColor = null),
        placementSelectors = null,
        embeddedId = ""
    )
}
