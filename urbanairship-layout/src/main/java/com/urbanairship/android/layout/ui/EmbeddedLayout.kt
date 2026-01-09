/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.urbanairship.UALog
import com.urbanairship.android.layout.AirshipEmbeddedViewManager
import com.urbanairship.android.layout.EmbeddedPresentation
import com.urbanairship.android.layout.ModelFactoryException
import com.urbanairship.android.layout.R
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.display.DisplayArgs
import com.urbanairship.android.layout.environment.DefaultViewEnvironment
import com.urbanairship.android.layout.environment.ExternalReporter
import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.Reporter
import com.urbanairship.android.layout.environment.ThomasActionRunner
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.android.layout.property.EmbeddedPlacement
import com.urbanairship.android.layout.reporting.DisplayTimer
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.util.Factory
import com.urbanairship.android.layout.util.ImageCache
import com.urbanairship.android.layout.util.getActivity
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.SimpleApplicationListener
import com.urbanairship.webkit.AirshipWebViewClient
import java.lang.ref.WeakReference
import java.util.Objects
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

private object EmbeddedViewModelStore : ViewModelStore()

private object EmbeddedViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore
        get() = EmbeddedViewModelStore
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class EmbeddedLayout(
    private val context: Context,
    public val embeddedViewId: String,
    public val viewInstanceId: String,
    args: DisplayArgs,
    private val embeddedViewManager: AirshipEmbeddedViewManager
) {
    private val viewJob = SupervisorJob()
    private val layoutScope = CoroutineScope(Dispatchers.Main.immediate + viewJob)
    private var layoutEventsJob: Job? = null
    private var stateUpdateReportJob: Job? = null

    private val payload: LayoutInfo = args.payload
    private val activityMonitor: ActivityMonitor = args.inAppActivityMonitor
    private val webViewClientFactory: Factory<AirshipWebViewClient>? = args.webViewClientFactory
    private val externalListener: ThomasListenerInterface = args.listener
    private val imageCache: ImageCache? = args.imageCache
    private val actionRunner: ThomasActionRunner = args.actionRunner


    private val reporter: Reporter = ExternalReporter(externalListener)

    private var currentView: WeakReference<ThomasEmbeddedView>? = null
    private var displayTimer: DisplayTimer? = null

    private val _isVisible = MutableStateFlow(false)

    private val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    /** @hide **/
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun getPlacement(): EmbeddedPlacement? {
        val presentation = (payload.presentation as? EmbeddedPresentation)
        return presentation?.getResolvedPlacement(context)
    }

    public fun getOrCreateView(
        fillWidth: Boolean = false,
        fillHeight: Boolean = false
    ): View? =
        currentView?.get() ?: makeView(fillWidth, fillHeight)

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun makeView(
        fillWidth: Boolean = false,
        fillHeight: Boolean = false
    ): View? {
        val activity = context.getActivity()
        if (activity == null) {
            UALog.e { "Airship Embedded Views must be hosted by an Activity! Current Activity is null." }
            return null
        }
        if (activity !is LifecycleOwner) {
            UALog.e { "Airship Embedded Views must be hosted by an Activity that implements LifecycleOwner!" }
            return null
        }

        val timer = DisplayTimer(activity, 0)

        val presentation = (payload.presentation as? EmbeddedPresentation)
        if (presentation == null) {
            UALog.e { "EmbeddedLayout requires an EmbeddedPresentation!" }
            return null
        }

        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                // Clean up the view model store when the activity is destroyed.
                onDisplayFinished()
                activity.lifecycle.removeObserver(this)
            }
        })

        activityMonitor.addApplicationListener(object : SimpleApplicationListener() {
            override fun onForeground(time: Long) {
                super.onForeground(time)
                reporter.onVisibilityChanged(isVisible.value, true)
            }

            override fun onBackground(time: Long) {
                super.onBackground(time)
                reporter.onVisibilityChanged(isVisible.value, false)
            }
        })

        val viewEnvironment: ViewEnvironment = DefaultViewEnvironment(
            activity,
            activityMonitor,
            webViewClientFactory,
            imageCache,
            false // Embedded views never ignore safe areas?
        )

        val viewModelProvider = ViewModelProvider(EmbeddedViewModelStoreOwner)
        val viewModel = viewModelProvider[viewInstanceId, LayoutViewModel::class.java]

        displayTimer = timer

        try {
            val modelEnvironment = viewModel.getOrCreateEnvironment(
                reporter = reporter,
                actionRunner = actionRunner,
                displayTimer = timer
            )
            val model = viewModel.getOrCreateModel(
                viewInfo = payload.view,
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
                fillWidth = fillWidth,
                fillHeight = fillHeight
            )

            embeddedView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    val updated = true
                    reporter.onVisibilityChanged(updated, activityMonitor.isAppForegrounded)
                    _isVisible.value = updated
                }

                override fun onViewDetachedFromWindow(v: View) {
                    val updated = false
                    reporter.onVisibilityChanged(updated, activityMonitor.isAppForegrounded)
                    _isVisible.value = updated
                }
            })

            layoutEventsJob?.cancel()
            layoutEventsJob = observeLayoutEvents(modelEnvironment.layoutEvents)

            stateUpdateReportJob?.cancel()
            stateUpdateReportJob = reportStateChange(modelEnvironment.layoutEvents)

            embeddedView.listener = object : ThomasEmbeddedView.Listener {
                override fun onDismissed() {
                    dismiss()
                    reportDismissFromOutside()
                }
            }

            currentView = WeakReference(embeddedView)
            return embeddedView
        } catch (e: ModelFactoryException) {
            UALog.e("Failed to load model!", e)
            return null
        }
    }

    private fun dismiss() {
        embeddedViewManager.dismiss(embeddedViewId, viewInstanceId)
    }

    private fun dismissAndCancel() {
        embeddedViewManager.dismissAll(embeddedViewId)
    }

    @MainThread
    private fun onDisplayFinished() {
        UALog.v("Embedded content finished displaying! $embeddedViewId, $viewInstanceId")
        layoutScope.cancel()
        EmbeddedViewModelStore.clear()
    }

    private fun observeLayoutEvents(events: Flow<LayoutEvent>) = layoutScope.launch {
        events.filterIsInstance<LayoutEvent.Finish>()
            .collect {
                if (it.cancel) {
                    dismissAndCancel()
                } else {
                    dismiss()
                }
            }
    }

    private fun reportStateChange(events: Flow<LayoutEvent>) = layoutScope.launch {
        events
            .filterIsInstance<LayoutEvent.StateUpdate>()
            .distinctUntilChanged()
            .collect {
                externalListener.onStateChanged(it.state)
            }
    }

    private fun reportDismissFromOutside(state: LayoutData = LayoutData.EMPTY) {
        reporter.report(
            event = ReportingEvent.Dismiss(
                data = ReportingEvent.DismissData.UserDismissed,
                displayTime = (displayTimer?.time ?: 0).milliseconds,
                context = state
            )
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EmbeddedLayout
        if (embeddedViewId != other.embeddedViewId) return false
        if (viewInstanceId != other.viewInstanceId) return false

        return true
    }

    override fun hashCode(): Int = Objects.hash(embeddedViewId, viewInstanceId)
}
