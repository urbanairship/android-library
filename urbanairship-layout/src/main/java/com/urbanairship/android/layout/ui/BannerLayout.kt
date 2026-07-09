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
import com.urbanairship.android.layout.AirshipBannerViewManager
import com.urbanairship.android.layout.BannerPresentation
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
import com.urbanairship.android.layout.property.BannerPlacement
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

private object BannerViewModelStore : ViewModelStore()

private object BannerViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore
        get() = BannerViewModelStore
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class BannerLayout(
    private val context: Context,
    public val viewInstanceId: String,
    args: DisplayArgs,
    private val bannerViewManager: AirshipBannerViewManager
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

    private var currentView: WeakReference<ThomasBannerView>? = null
    private var displayTimer: DisplayTimer? = null

    private val _isVisible = MutableStateFlow(false)

    private val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    /** @hide **/
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun getPresentation(): BannerPresentation? =
        payload.presentation as? BannerPresentation

    /** @hide **/
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun getPlacement(): BannerPlacement? =
        getPresentation()?.getResolvedPlacement(context)

    /**
     * Creates the banner view, notifying the optional [frameSizeChangedListener] when the
     * banner frame's size changes. The listener may be used by the host to size animations and
     * swipe-to-dismiss gestures relative to the banner content.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun makeView(
        frameSizeChangedListener: ((width: Int, height: Int) -> Unit)? = null
    ): View? {
        val activity = context.getActivity()
        if (activity == null) {
            UALog.e { "Airship Banner Views must be hosted by an Activity! Current Activity is null." }
            return null
        }
        if (activity !is LifecycleOwner) {
            UALog.e { "Airship Banner Views must be hosted by an Activity that implements LifecycleOwner!" }
            return null
        }

        val presentation = getPresentation()
        if (presentation == null) {
            UALog.e { "BannerLayout requires a BannerPresentation!" }
            return null
        }

        val timer = DisplayTimer(activity, 0)

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
            getPlacement()?.shouldIgnoreSafeArea() ?: false
        )

        val viewModelProvider = ViewModelProvider(BannerViewModelStoreOwner)
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
            // Create the banner view using our theme, to prevent app custom themes from affecting
            // the banner view.
            val themedContext = ContextThemeWrapper(context, R.style.UrbanAirship_Layout)
            val bannerView = ThomasBannerView(
                context = themedContext,
                model = model,
                presentation = presentation,
                environment = viewEnvironment
            )
            bannerView.frameSizeChangedListener = frameSizeChangedListener

            bannerView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
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

            currentView = WeakReference(bannerView)
            return bannerView
        } catch (e: ModelFactoryException) {
            UALog.e("Failed to load model!", e)
            return null
        }
    }

    /** Removes the banner from the pending queue, without reporting. */
    private fun dismiss() {
        bannerViewManager.dismiss(viewInstanceId)
    }

    /**
     * Dismisses the banner from a user action outside of the layout (e.g. a swipe), reporting
     * a user dismiss.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun dismissFromUser() {
        reportDismissFromOutside(ReportingEvent.DismissData.UserDismissed)
        dismiss()
    }

    /**
     * Dismisses the banner after the auto-dismiss duration has elapsed, reporting a timed out
     * dismiss.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun dismissFromTimeout() {
        reportDismissFromOutside(ReportingEvent.DismissData.TimedOut)
        dismiss()
    }

    @MainThread
    private fun onDisplayFinished() {
        UALog.v("Banner finished displaying! $viewInstanceId")
        layoutScope.cancel()
        BannerViewModelStore.clear()
    }

    private fun observeLayoutEvents(events: Flow<LayoutEvent>) = layoutScope.launch {
        events.filterIsInstance<LayoutEvent.Finish>()
            .collect { dismiss() }
    }

    private fun reportStateChange(events: Flow<LayoutEvent>) = layoutScope.launch {
        events
            .filterIsInstance<LayoutEvent.StateUpdate>()
            .distinctUntilChanged()
            .collect {
                externalListener.onStateChanged(it.state)
            }
    }

    private fun reportDismissFromOutside(data: ReportingEvent.DismissData) {
        reporter.report(
            event = ReportingEvent.Dismiss(
                data = data,
                displayTime = (displayTimer?.time ?: 0).milliseconds,
                context = LayoutData.EMPTY
            )
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BannerLayout
        if (viewInstanceId != other.viewInstanceId) return false

        return true
    }

    override fun hashCode(): Int = Objects.hash(viewInstanceId)
}
