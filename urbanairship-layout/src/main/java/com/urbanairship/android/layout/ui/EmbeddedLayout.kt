package com.urbanairship.android.layout.ui

import android.content.Context
import android.view.ViewGroup
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.urbanairship.UALog
import com.urbanairship.android.layout.DefaultEmbeddedViewManager
import com.urbanairship.android.layout.ModelFactoryException
import com.urbanairship.android.layout.ThomasListener
import com.urbanairship.android.layout.display.DisplayArgs
import com.urbanairship.android.layout.environment.DefaultViewEnvironment
import com.urbanairship.android.layout.environment.ExternalReporter
import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.Reporter
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.android.layout.reporting.DisplayTimer
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.util.Factory
import com.urbanairship.android.layout.util.ImageCache
import com.urbanairship.android.layout.util.getActivity
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.webkit.AirshipWebViewClient
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

// TODO(embedded): make this better, or share with the banner model store if there isn't anything
//    nicer we can do...
private object EmbeddedViewModelStore : ViewModelStore()
// TODO(embedded): this too...
private object EmbeddedViewModelStoreOwner : ViewModelStoreOwner {
    override fun getViewModelStore(): ViewModelStore = EmbeddedViewModelStore
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class EmbeddedLayout(
    private val context: Context,
    private val embeddedViewId: String,
    args: DisplayArgs,
) {
    private val viewJob = SupervisorJob()
    private val layoutScope = CoroutineScope(Dispatchers.Main.immediate + viewJob)

    private val activityMonitor: ActivityMonitor = args.inAppActivityMonitor
    private val webViewClientFactory: Factory<AirshipWebViewClient>? = args.webViewClientFactory
    private val imageCache: ImageCache? = args.imageCache
    private val payload: LayoutInfo = args.payload
    private val externalListener: ThomasListener = args.listener
    private val viewModelKey: String = args.hashCode().toString()
    private val reporter: Reporter = ExternalReporter(externalListener)
    private val viewInstanceId: String = payload.hashCode().toString()

    private var currentView: WeakReference<ThomasEmbeddedView>? = null
    private var displayTimer: DisplayTimer? = null

    /**
     * Attempts to display the banner.
     */
    public fun displayIn(
        parent: ViewGroup,
        // TODO(embedded): wrap content? can we, or will that potentially mess up the UI if too big?
        widthSpec: Int = ViewGroup.LayoutParams.MATCH_PARENT,
        heightSpec: Int = ViewGroup.LayoutParams.MATCH_PARENT
    ) {
        val activity = context.getActivity() ?: return
        if (activity !is LifecycleOwner) {
            UALog.e { "Airship Embedded Views must be hosted by an Activity that implements LifecycleOwner!" }
            return
        }

        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                // Clean up the view model store when the activity is destroyed.
                onDisplayFinished()
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
        val viewModel = viewModelProvider[viewModelKey, LayoutViewModel::class.java]

        // Try to use the parent view's lifecycle owner, with a
        // fallback to out ActivityMonitor if there isn't one.
        val timer = parent.findViewTreeLifecycleOwner()
            ?.let { DisplayTimer(it, 0) }
            // TODO(embedded): do we even want to fallback, or should we just return and log and error?
            ?: DisplayTimer(activityMonitor, null, 0)
        displayTimer = timer

        try {
            val modelEnvironment = viewModel.getOrCreateEnvironment(
                reporter = reporter,
                listener = externalListener,
                displayTimer = timer
            )
            val model = viewModel.getOrCreateModel(
                viewInfo = payload.view,
                modelEnvironment = modelEnvironment
            )
            val embeddedView = ThomasEmbeddedView(
                context = context,
                model = model,
                environment = viewEnvironment
            ).apply {
                // TODO(embedded): respect layout placement
                layoutParams = ConstraintLayout.LayoutParams(widthSpec, heightSpec)
            }

            observeLayoutEvents(modelEnvironment.layoutEvents)

            embeddedView.listener = object : ThomasEmbeddedView.Listener {
                override fun onDismissed() {
                    UALog.v("EmbeddedLayout dismissed! $embeddedViewId")
                    // Dismiss the view via the manager. This will update the AirshipEmbeddedView.
                    DefaultEmbeddedViewManager.dismiss(embeddedViewId, viewInstanceId)
                    reportDismissFromOutside()
                }
            }

            if (embeddedView.parent == null) {
                parent.addView(embeddedView)
                embeddedView.showAnimated()
                currentView = WeakReference(embeddedView)
            }
        } catch (e: ModelFactoryException) {
            UALog.e("Failed to load model!", e)
        }
    }

    public fun dismiss(animate: Boolean = true, isInternal: Boolean = false) {
        currentView?.get()?.dismiss(animate, isInternal)
    }

    /** Called when the banner is finished displaying. */
    @MainThread
    private fun onDisplayFinished() {
        UALog.v("EmbeddedLayout finished displaying! $embeddedViewId")
        layoutScope.cancel()
        EmbeddedViewModelStore.clear()
    }

    private fun observeLayoutEvents(events: Flow<LayoutEvent>) = layoutScope.launch {
        events
            .filterIsInstance<LayoutEvent.Finish>()
            .take(1)
            .collect { dismiss() }
    }

    private fun reportDismissFromOutside(state: LayoutData = LayoutData.empty()) {
        reporter.report(ReportingEvent.DismissFromOutside(displayTimer?.time ?: 0), state)
    }
}
