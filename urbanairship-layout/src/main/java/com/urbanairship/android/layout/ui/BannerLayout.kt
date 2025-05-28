package com.urbanairship.android.layout.ui

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.annotation.MainThread
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.customview.widget.ViewDragHelper.STATE_DRAGGING
import androidx.customview.widget.ViewDragHelper.STATE_IDLE
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.urbanairship.Predicate
import com.urbanairship.UALog
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
import com.urbanairship.android.layout.property.VerticalPosition
import com.urbanairship.android.layout.reporting.DisplayTimer
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.util.Factory
import com.urbanairship.android.layout.util.ImageCache
import com.urbanairship.app.ActivityListener
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.SimpleActivityListener
import com.urbanairship.util.ManifestUtils
import com.urbanairship.webkit.AirshipWebViewClient
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

private object BannerViewModelStore : ViewModelStore()

private object BannerViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore
        get() = BannerViewModelStore
}

internal class BannerLayout(
    private val context: Context,
    args: DisplayArgs
) {
    private val viewJob = SupervisorJob()
    private val bannerScope = CoroutineScope(Dispatchers.Main.immediate + viewJob)

    private val activityMonitor: ActivityMonitor = args.inAppActivityMonitor
    private val webViewClientFactory: Factory<AirshipWebViewClient>? = args.webViewClientFactory
    private val imageCache: ImageCache? = args.imageCache
    private val payload: LayoutInfo = args.payload
    private val externalListener: ThomasListenerInterface = args.listener
    private val viewModelKey: String = args.hashCode().toString()
    private val reporter: Reporter = ExternalReporter(externalListener)
    private val actionRunner: ThomasActionRunner = args.actionRunner

    private val activityPredicate = Predicate { activity: Activity ->
        try {
            if (getContainerView(activity) == null) {
                UALog.e("BannerAdapter - Unable to display in-app message. No view group found.")
                return@Predicate false
            }
        } catch (e: Exception) {
            UALog.e("Failed to find container view.", e)
            return@Predicate false
        }
        true
    }

    private val displayTimer: DisplayTimer = DisplayTimer(activityMonitor, activityPredicate, 0)

    private val activityListener: ActivityListener = object : SimpleActivityListener() {
        override fun onActivityStopped(activity: Activity) {
            if (activityPredicate.apply(activity)) {
                this@BannerLayout.onActivityStopped(activity)
            }
        }

        override fun onActivityResumed(activity: Activity) {
            if (activityPredicate.apply(activity)) {
                this@BannerLayout.onActivityResumed(activity)
            }
        }

        override fun onActivityPaused(activity: Activity) {
            if (activityPredicate.apply(activity)) {
                this@BannerLayout.onActivityPaused(activity)
            }
        }
    }

    private var lastActivity: WeakReference<Activity>? = null
    private var currentView: WeakReference<ThomasBannerView>? = null

    init {
        activityMonitor.addActivityListener(activityListener)
    }

    /**
     * Attempts to display the banner.
     */
    fun display() {
        val activityList = activityMonitor.getResumedActivities(activityPredicate)
        val activity = activityList.firstOrNull() ?: return
        val presentation = (payload.presentation as? BannerPresentation) ?: return

        val placement = presentation.getResolvedPlacement(context)
        if (placement.shouldIgnoreSafeArea()) {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        }
        val viewEnvironment: ViewEnvironment = DefaultViewEnvironment(
            activity,
            activityMonitor,
            webViewClientFactory,
            imageCache,
            placement.shouldIgnoreSafeArea()
        )
        val container = getContainerView(activity) ?: return

        val viewModelProvider = ViewModelProvider(BannerViewModelStoreOwner)
        val viewModel = viewModelProvider[viewModelKey, LayoutViewModel::class.java]

        try {
            val modelEnvironment = viewModel.getOrCreateEnvironment(
                reporter = reporter,
                actionRunner = actionRunner,
                displayTimer = displayTimer
            )
            val model = viewModel.getOrCreateModel(
                viewInfo = payload.view,
                modelEnvironment = modelEnvironment
            )
            val bannerView = ThomasBannerView(
                context = context,
                model = model,
                presentation = presentation,
                environment = viewEnvironment
            ).apply {
                layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
            }

            if (lastActivity?.get() !== activity) {
                if (VerticalPosition.BOTTOM == placement.position?.vertical) {
                    bannerView.setAnimations(
                        R.animator.ua_layout_slide_in_bottom,
                        R.animator.ua_layout_slide_out_bottom
                    )
                } else {
                    bannerView.setAnimations(
                        R.animator.ua_layout_slide_in_top,
                        R.animator.ua_layout_slide_out_top
                    )
                }
            }

            observeLayoutEvents(modelEnvironment.layoutEvents)
            reportStateChange(modelEnvironment.layoutEvents)

            bannerView.setListener(object : ThomasBannerView.Listener {
                override fun onTimedOut() = onDisplayFinished()
                override fun onDismissed() {
                    reportDismissFromOutside()
                    onDisplayFinished()
                }
                override fun onDragStateChanged(state: Int) {
                    when (state) {
                        STATE_DRAGGING -> bannerView.displayTimer.stop()
                        STATE_IDLE -> if (bannerView.isResumed) {
                            bannerView.displayTimer.start()
                        }
                    }
                }
            })

            if (bannerView.parent == null) {
                container.addView(bannerView)
            }

            lastActivity = WeakReference(activity)
            currentView = WeakReference(bannerView)
        } catch (e: ModelFactoryException) {
            UALog.e("Failed to load model!", e)
        }
    }

    fun dismiss(animate: Boolean = false, isInternal: Boolean = false) {
        currentView?.get()?.dismiss(animate = animate, isInternal = isInternal)
    }

    /** Called when the banner is finished displaying. */
    @MainThread
    private fun onDisplayFinished() {
        activityMonitor.removeActivityListener(activityListener)
        viewJob.cancelChildren()
        BannerViewModelStore.clear()
    }

    /**
     * Gets the banner's container view.
     *
     * @param activity The activity.
     * @return The banner's container view or null.
     */
    private fun getContainerView(activity: Activity): ViewGroup? {
        val containerId = getContainerId(activity)
        var view: View? = null
        if (containerId != 0) {
            view = activity.findViewById(containerId)
        }
        if (view == null) {
            view = activity.findViewById(android.R.id.content)
        }
        return view as? ViewGroup
    }

    /**
     * Gets the Banner fragment's container ID.
     *
     * The default implementation checks the activities metadata for [.BANNER_CONTAINER_ID].
     *
     * @param activity The activity.
     * @return The container ID or 0 if not defined.
     */
    private fun getContainerId(activity: Activity): Int {
        synchronized(cachedContainerIds) {
            val cachedId = cachedContainerIds[activity.javaClass]
            if (cachedId != null) {
                return cachedId
            }
            var containerId = 0
            val info = ManifestUtils.getActivityInfo(activity.javaClass)
            if (info?.metaData != null) {
                containerId = info.metaData.getInt(BANNER_CONTAINER_ID, containerId)
            }
            cachedContainerIds[activity.javaClass] = containerId
            return containerId
        }
    }

    private fun observeLayoutEvents(events: Flow<LayoutEvent>) = bannerScope.launch {
        events
            .filterIsInstance<LayoutEvent.Finish>()
            .collect { dismiss() }
    }

    private fun reportStateChange(events: Flow<LayoutEvent>) = bannerScope.launch {
        events
            .filterIsInstance<LayoutEvent.StateUpdate>()
            .distinctUntilChanged()
            .collect {
                externalListener.onStateChanged(it.state)
            }
    }

    private fun reportDismissFromOutside(state: LayoutData = LayoutData.empty()) {
        reporter.report(
            event = ReportingEvent.Dismiss(
                data = ReportingEvent.DismissData.UserDismissed,
                displayTime = displayTimer.time.milliseconds,
                context = state
            )
        )
    }

    @MainThread
    private fun onActivityResumed(activity: Activity) {
        val currentView = currentView?.get()
        if (currentView == null || !ViewCompat.isAttachedToWindow(currentView)) {
            display()
        } else if (activity === lastActivity?.get()) {
            currentView.onResume()
        }
    }

    @MainThread
    private fun onActivityStopped(activity: Activity) {
        if (activity !== lastActivity?.get()) {
            return
        }
        val view = currentView?.get()
        if (view != null) {
            currentView = null
            lastActivity = null
            view.dismiss(false, isInternal = true)
            display()
        }
    }

    @MainThread
    private fun onActivityPaused(activity: Activity) {
        if (activity !== lastActivity?.get()) {
            return
        }
        currentView?.get()?.onPause()
    }

    companion object {

        /**
         * Metadata an app can use to specify the banner's container ID per activity.
         */
        const val BANNER_CONTAINER_ID = "com.urbanairship.iam.banner.BANNER_CONTAINER_ID"
        private val cachedContainerIds: MutableMap<Class<*>, Int> = HashMap()
    }
}
