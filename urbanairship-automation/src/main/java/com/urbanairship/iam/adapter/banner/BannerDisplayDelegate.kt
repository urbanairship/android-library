/* Copyright Airship and Contributors */

package com.urbanairship.iam.adapter.banner

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MainThread
import com.urbanairship.Predicate
import com.urbanairship.UALog.e
import com.urbanairship.actions.ActionRunner
import com.urbanairship.actions.run
import com.urbanairship.android.layout.analytics.DisplayResult
import com.urbanairship.app.ActivityListener
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.SimpleActivityListener
import com.urbanairship.automation.R
import com.urbanairship.iam.adapter.DelegatingDisplayAdapter
import com.urbanairship.iam.adapter.InAppMessageDisplayListener
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.iam.assets.AirshipCachedAssets
import com.urbanairship.iam.content.Banner
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.iam.info.InAppMessageButtonInfo
import com.urbanairship.iam.view.BannerView
import com.urbanairship.iam.view.InAppViewUtils
import com.urbanairship.util.ManifestUtils
import com.urbanairship.util.timer.ActiveTimer
import java.lang.ref.WeakReference
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Banner display adapter.
 */
internal class BannerDisplayDelegate(
    private val displayContent: InAppMessageDisplayContent.BannerContent,
    private val assets: AirshipCachedAssets?,
    private val activityMonitor: ActivityMonitor,
    private val actionRunner: ActionRunner
) : DelegatingDisplayAdapter.Delegate {

    override val activityPredicate: Predicate<Activity> = object : Predicate<Activity> {
        override fun apply(value: Activity): Boolean {
            try {
                if (getContainerView(value) == null) {
                    e("BannerAdapter - Unable to display in-app message. No view group found.")
                    return false
                }
            } catch (e: Exception) {
                e("Failed to find container view.", e)
                return false
            }
            return true
        }
    }

    private val listener: ActivityListener = object : SimpleActivityListener() {
        override fun onActivityStopped(activity: Activity) {
            if (activityPredicate.apply(activity)) {
                this@BannerDisplayDelegate.onActivityStopped(activity)
            }
        }

        override fun onActivityResumed(activity: Activity) {
            if (activityPredicate.apply(activity)) {
                this@BannerDisplayDelegate.onActivityResumed(activity)
            }
        }

        override fun onActivityPaused(activity: Activity) {
            if (activityPredicate.apply(activity)) {
                this@BannerDisplayDelegate.onActivityPaused(activity)
            }
        }
    }
    private var lastActivity: WeakReference<Activity>? = null
    private var currentView: WeakReference<BannerView>? = null
    private var analyticsListener: InAppMessageDisplayListener? = null
    private var continuation: CancellableContinuation<DisplayResult>? = null


    override suspend fun display(
        context: Context,
        analytics: InAppMessageAnalyticsInterface
    ): DisplayResult {

        analyticsListener = InAppMessageDisplayListener(
            analytics = analytics,
            timer = ActiveTimer(activityMonitor),
            onDismiss = { continuation?.resumeWith(Result.success(it)) }
        )

        activityMonitor.addActivityListener(listener)

        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine {
                continuation = it
                display()
            }
        }
    }


    /**
     * Called when the banner is finished displaying.
     */
    private fun onDisplayFinished() {
        activityMonitor.removeActivityListener(listener)
    }

    /**
     * Inflates the banner view.
     * @param activity The activity.
     * @return The banner view.
     */
    private fun onCreateView(activity: Activity): BannerView {
        return BannerView(activity, displayContent.banner, assets)
    }

    /**
     * Called after the banner view is created.
     *
     * @param view The banner view.
     * @param activity The activity.
     */
    internal fun onViewCreated(view: BannerView, activity: Activity) {
        if (getLastActivity() !== activity) {
            when(displayContent.banner.placement) {
                Banner.Placement.BOTTOM -> {
                    view.setAnimations(R.animator.ua_iam_slide_in_bottom, R.animator.ua_iam_slide_out_bottom)
                }
                Banner.Placement.TOP -> {
                    view.setAnimations(R.animator.ua_iam_slide_in_top, R.animator.ua_iam_slide_out_top)
                }
            }
        }

        view.setListener(object : BannerView.Listener {
            override fun onButtonClicked(view: BannerView, buttonInfo: InAppMessageButtonInfo) {
                buttonInfo.actions?.let {
                    actionRunner.run(it.map)
                }
                analyticsListener?.onButtonDismissed(buttonInfo)

                onDisplayFinished()
            }

            override fun onBannerClicked(view: BannerView) {
                if (displayContent.banner.actions?.isNotEmpty == true) {
                    actionRunner.run(displayContent.banner.actions.map)
                    analyticsListener?.onMessageTapDismissed()
                }

                onDisplayFinished()
            }

            override fun onTimedOut(view: BannerView) {
                analyticsListener?.onTimedOut()
                onDisplayFinished()
            }

            override fun onUserDismissed(view: BannerView) {
                analyticsListener?.onUserDismissed()
                onDisplayFinished()
            }
        })

        analyticsListener?.onAppear()
    }

    /**
     * Gets the banner's container view.
     *
     * @param activity The activity.
     * @return The banner's container view or null.
     */
    internal fun getContainerView(activity: Activity): ViewGroup? {
        val containerId = getContainerId(activity)
        var view: View? = null
        if (containerId != 0) {
            view = activity.findViewById(containerId)
        }
        if (view == null) {
            view = activity.findViewById(android.R.id.content)
        }
        return if (view is ViewGroup) {
            view
        } else null
    }

    /**
     * Attempts to display the banner.
     */
    private fun display() {
        val activity = activityMonitor.getResumedActivities(activityPredicate).firstOrNull() ?: return
        val container = getContainerView(activity) ?: return

        val view = onCreateView(activity)
        onViewCreated(view, activity)

        if (view.parent == null) {
            if (container.id == android.R.id.content) {
                // Android stops dispatching insets to the remaining children if a child
                // consumes the insets. To work around this, we are inserting the view
                // at index 0, but setting the Z value larger than the other children
                // so it's drawn on top.
                view.z = InAppViewUtils.getLargestChildZValue(container) + 1
                container.addView(view, 0)
            } else {
                container.addView(view)
            }
        }
        lastActivity = WeakReference(activity)
        currentView = WeakReference(view)

        handleBannerInsetsIfNeeded(activity, view)
    }

    private fun handleBannerInsetsIfNeeded(activity: Activity, view: BannerView) {
        val insetEdgeToEdge = ManifestUtils.getActivityInfo(activity, activity.javaClass)?.metaData
            ?.getBoolean(BANNER_INSET_EDGE_TO_EDGE, false) ?: false

        if (insetEdgeToEdge) {
            val resources = activity.resources
            val top = resources.getIdentifier("status_bar_height", "dimen", "android").let {
                if (it > 0) { resources.getDimensionPixelSize(it) } else { 0 }
            }
            val bottom = resources.getIdentifier("navigation_bar_height", "dimen", "android").let {
                if (it > 0) { resources.getDimensionPixelSize(it) } else { 0 }
            }

            view.setPaddingRelative(0, top, 0, bottom)
        }
    }

    /**
     * Gets the Banner fragment's container ID.
     *
     * The default implementation checks the activities metadata for `BANNER_CONTAINER_ID`.
     *
     * @param activity The activity.
     * @return The container ID or 0 if its not defined.
     */
    private fun getContainerId(activity: Activity): Int {
        synchronized(cachedContainerIds) {
            val cachedId = cachedContainerIds[activity.javaClass]
            if (cachedId != null) {
                return cachedId
            }
            val containerId = ManifestUtils
                .getActivityInfo(activity, activity.javaClass)
                ?.metaData
                ?.getInt(BANNER_CONTAINER_ID, 0)
                ?: 0

            cachedContainerIds[activity.javaClass] = containerId
            return containerId
        }
    }

    @MainThread
    private fun onActivityResumed(activity: Activity) {
        val currentView = getCurrentView()
        if (currentView == null || !currentView.isAttachedToWindow) {
            display()
        } else if (activity === getLastActivity()) {
            currentView.onResume()
        }
    }

    @MainThread
    private fun onActivityStopped(activity: Activity) {
        if (activity !== getLastActivity()) {
            return
        }
        val view = getCurrentView()
        if (view != null) {
            currentView = null
            lastActivity = null
            view.dismiss(false)
            display()
        }
    }

    @MainThread
    private fun onActivityPaused(activity: Activity) {
        if (activity !== getLastActivity()) {
            return
        }
        val currentView = getCurrentView()
        currentView?.onPause()
    }

    @MainThread
    private fun getCurrentView(): BannerView? = currentView?.get()

    @MainThread
    private fun getLastActivity(): Activity? = lastActivity?.get()

    companion object {
        /**
         * Metadata an app can use to specify the banner's container ID per activity.
         */
        const val BANNER_CONTAINER_ID = "com.urbanairship.iam.banner.BANNER_CONTAINER_ID"

        /**
         * Metadata an app can use to specify that the banner should be inset for edge-to-edge mode.
         *
         * The default value is false. In most cases, this flag will not be necessary, but can be
         * used if banners are not being inset properly without this flag being set.
         */
        const val BANNER_INSET_EDGE_TO_EDGE = "com.urbanairship.iam.banner.BANNER_INSET_EDGE_TO_EDGE"

        private val cachedContainerIds: MutableMap<Class<*>, Int> = HashMap()
    }
}
