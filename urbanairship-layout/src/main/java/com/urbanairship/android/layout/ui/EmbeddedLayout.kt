package com.urbanairship.android.layout.ui

import android.content.Context
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
import com.urbanairship.android.layout.ThomasListener
import com.urbanairship.android.layout.display.DisplayArgs
import com.urbanairship.android.layout.environment.DefaultViewEnvironment
import com.urbanairship.android.layout.environment.ExternalReporter
import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.Reporter
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
import java.io.ObjectStreamException
import java.util.Objects

// TODO(embedded): make this better, or share with the banner model store if there isn't anything
//    nicer we can do...
private object EmbeddedViewModelStore : ViewModelStore()
// TODO(embedded): this too...
private object EmbeddedViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore
        get() = EmbeddedViewModelStore
}

// TODO: we maybe need to make this public because of the compose transition scope generic type :-/
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class EmbeddedLayout(
    private val context: Context,
    public val embeddedViewId: String,
    args: DisplayArgs,
    private val embeddedViewManager: AirshipEmbeddedViewManager
) {
    private val viewJob = SupervisorJob()
    private val layoutScope = CoroutineScope(Dispatchers.Main.immediate + viewJob)

    private val activityMonitor: ActivityMonitor = args.inAppActivityMonitor
    private val webViewClientFactory: Factory<AirshipWebViewClient>? = args.webViewClientFactory
    private val imageCache: ImageCache? = args.imageCache
    private val payload: LayoutInfo = args.payload
    private val externalListener: ThomasListener = args.listener
    private val viewModelKey: String = payload.hash.toString()
    private val reporter: Reporter = ExternalReporter(externalListener)
    /** @hide **/
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val viewInstanceId: String =  payload.hash.toString()

    private var currentView: WeakReference<ThomasEmbeddedView>? = null
    private var displayTimer: DisplayTimer? = null

    /** @hide **/
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun getPlacement(): EmbeddedPlacement? {
        val presentation = (payload.presentation as? EmbeddedPresentation)
        return presentation?.getResolvedPlacement(context)
    }

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
                presentation = presentation,
                environment = viewEnvironment,
                fillWidth = fillWidth,
                fillHeight = fillHeight
            )

            observeLayoutEvents(modelEnvironment.layoutEvents)

            embeddedView.listener = object : ThomasEmbeddedView.Listener {
                override fun onDismissed() {
                    UALog.v("EmbeddedLayout dismissed! $embeddedViewId")
                    // Dismiss the view via the manager. This will update the AirshipEmbeddedView.
                    embeddedViewManager.dismiss(embeddedViewId, viewInstanceId)
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

    private fun dismiss(animate: Boolean = true, isInternal: Boolean = false) {
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