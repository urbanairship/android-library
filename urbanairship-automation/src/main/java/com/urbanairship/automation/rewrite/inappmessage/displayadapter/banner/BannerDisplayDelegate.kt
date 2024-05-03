/* Copyright Airship and Contributors */
package com.urbanairship.automation.rewrite.inappmessage.displayadapter.banner

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MainThread
import androidx.core.view.ViewCompat
import com.urbanairship.Predicate
import com.urbanairship.UALog.e
import com.urbanairship.app.ActivityListener
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.SimpleActivityListener
import com.urbanairship.automation.R
import com.urbanairship.automation.rewrite.inappmessage.InAppActionUtils
import com.urbanairship.automation.rewrite.inappmessage.InAppActivityMonitor
import com.urbanairship.automation.rewrite.inappmessage.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.automation.rewrite.inappmessage.assets.AirshipCachedAssets
import com.urbanairship.automation.rewrite.inappmessage.content.Banner
import com.urbanairship.automation.rewrite.inappmessage.content.InAppMessageDisplayContent
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.DelegatingDisplayAdapter
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.DisplayResult
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.InAppMessageDisplayListener
import com.urbanairship.automation.rewrite.inappmessage.info.InAppMessageButtonInfo
import com.urbanairship.automation.rewrite.inappmessage.view.BannerView
import com.urbanairship.automation.rewrite.inappmessage.view.InAppViewUtils
import com.urbanairship.automation.rewrite.utils.ActiveTimer
import com.urbanairship.util.ManifestUtils
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
    private val activityMonitor: ActivityMonitor
) : DelegatingDisplayAdapter.Delegate {

    override val activityPredicate: Predicate<Activity> = object : Predicate<Activity> {
        override fun apply(activity: Activity): Boolean {
            try {
                if (getContainerView(activity) == null) {
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
            onDismiss = { continuation?.resumeWith(Result.success(it)) })

        activityMonitor.addActivityListener(listener)

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine {
                continuation = it
                display(context)
            }
        }
    }


    /**
     * Called when the banner is finished displaying.
     * @param context The context.
     */
    private fun onDisplayFinished(context: Context) {
        activityMonitor.removeActivityListener(listener)
    }

    /**
     * Inflates the banner view.
     * @param activity The activity.
     * @return The banner view.
     */
    internal fun onCreateView(activity: Activity): BannerView {
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
                InAppActionUtils.runActions(buttonInfo)
                analyticsListener?.onButtonDismissed(buttonInfo)

                onDisplayFinished(view.context)
            }

            override fun onBannerClicked(view: BannerView) {
                if (displayContent.banner.actions.isNotEmpty()) {
                    InAppActionUtils.runActions(displayContent.banner.actions)
                    analyticsListener?.onMessageTapDismissed()
                }

                onDisplayFinished(view.context)
            }

            override fun onTimedOut(view: BannerView) {
                analyticsListener?.onTimedOut()
                onDisplayFinished(view.context)
            }

            override fun onUserDismissed(view: BannerView) {
                analyticsListener?.onUserDismissed()
                onDisplayFinished(view.context)
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
     *
     * @param context THe application context.
     */
    private fun display(context: Context) {
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
                .getActivityInfo(activity.javaClass)
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
        if (currentView == null || !ViewCompat.isAttachedToWindow(currentView)) {
            display(activity)
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
            display(activity.applicationContext)
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
        private val cachedContainerIds: MutableMap<Class<*>, Int> = HashMap()
    }
}
