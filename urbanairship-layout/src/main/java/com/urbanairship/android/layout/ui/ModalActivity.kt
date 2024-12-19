/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.ui

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RestrictTo
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.urbanairship.UALog
import com.urbanairship.android.layout.ModalPresentation
import com.urbanairship.android.layout.ModelFactoryException
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.display.DisplayArgsLoader
import com.urbanairship.android.layout.display.DisplayArgsLoader.LoadException
import com.urbanairship.android.layout.environment.DefaultViewEnvironment
import com.urbanairship.android.layout.environment.ExternalReporter
import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.Reporter
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.property.ModalPlacement
import com.urbanairship.android.layout.property.Orientation
import com.urbanairship.android.layout.reporting.DisplayTimer
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.util.FullScreenAdjustResizeWorkaround.Companion.applyAdjustResizeWorkaround
import com.urbanairship.android.layout.view.ModalView
import com.urbanairship.util.parcelableExtra
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ModalActivity : AppCompatActivity() {

    private val viewModel by lazy {
        ViewModelProvider(this)[LayoutViewModel::class.java]
    }

    // TODO(API35): Replace the hardcoded version with VANILLA_ICE_CREAM
    //   once we've updated the SDK to target API 35.
    private val isAtLeastApi35 = Build.VERSION.SDK_INT >= 35

    private lateinit var loader: DisplayArgsLoader
    private lateinit var externalListener: ThomasListenerInterface
    private lateinit var reporter: Reporter
    private lateinit var displayTimer: DisplayTimer

    private var disableBackButton = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loader = parcelableExtra(EXTRA_DISPLAY_ARGS_LOADER) ?: run {
            UALog.e("Missing layout args loader")
            finish()
            return@onCreate
        }

        val restoredTime = savedInstanceState?.getLong(KEY_DISPLAY_TIME) ?: 0
        displayTimer = DisplayTimer(this, restoredTime)

        try {
            val args = loader.displayArgs
            externalListener = args.listener
            reporter = ExternalReporter(externalListener)

            val presentation = (args.payload.presentation as? ModalPresentation) ?: run {
                UALog.e("Not a modal presentation")
                finish()
                return@onCreate
            }

            disableBackButton = presentation.isDisableBackButton

            val placement = presentation.getResolvedPlacement(this)
            setOrientationLock(placement)

            val shouldInsetLayout = handleIgnoreSafeAreas(placement.shouldIgnoreSafeArea())

            val modelEnvironment = viewModel.getOrCreateEnvironment(
                reporter = reporter,
                actionRunner = args.actionRunner,
                displayTimer = displayTimer
            )

            val model = viewModel.getOrCreateModel(args.payload.view, modelEnvironment)

            observeLayoutEvents(modelEnvironment.layoutEvents)

            val viewEnvironment: ViewEnvironment = DefaultViewEnvironment(
                this,
                args.inAppActivityMonitor,
                args.webViewClientFactory,
                args.imageCache,
                placement.shouldIgnoreSafeArea()
            )

            val view = ModalView(this, model, presentation, viewEnvironment).apply {
                id = viewModel.rootViewId
                layoutParams = ConstraintLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)

                if (presentation.isDismissOnTouchOutside) {
                    setOnClickOutsideListener {
                        reportDismissFromOutside()
                        finish()
                    }
                }
            }

            setContentView(view)

            // Inset the layout if we're on Android 35+ and forced to ignore safe areas.
            if (shouldInsetLayout) {
                ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
                    val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                    v.updatePadding(
                        top = insets.top,
                        bottom = insets.bottom,
                        left = insets.left,
                        right = insets.right
                    )
                    WindowInsetsCompat.CONSUMED
                }

                ViewCompat.requestApplyInsets(view)
            }

            if (placement.shouldIgnoreSafeArea()) {
                // Apply workaround for adjustResize not working with fullscreen activities,
                // so that the keyboard does not cover the modal when we're ignoring safe areas.
                // ref: https://issuetracker.google.com/issues/36911528
                applyAdjustResizeWorkaround()
            }
        } catch (e: LoadException) {
            UALog.e(e, "Failed to load model!")
            finish()
        } catch (e: ModelFactoryException) {
            UALog.e(e, "Failed to load model!")
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && ::loader.isInitialized) {
            loader.dispose()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_DISPLAY_TIME, displayTimer.time)
    }

    override fun onBackPressed() {
        if (!disableBackButton) {
            super.onBackPressed()
            reportDismissFromOutside()
        }
    }

    private fun observeLayoutEvents(events: Flow<LayoutEvent>) = lifecycleScope.launch {
        events
            .filterIsInstance<LayoutEvent.Finish>()
            .collect { finish() }
    }

    private fun reportDismissFromOutside(state: LayoutData = LayoutData.empty()) =
        reporter.report(ReportingEvent.DismissFromOutside(displayTimer.time), state)

    /**
     * Handles safe areas if necessary, and returns a boolean indicating whether insets should be
     * applied at the top level.
     */
    private fun handleIgnoreSafeAreas(shouldIgnoreSafeArea: Boolean): Boolean {
        // If we're on API 35+, edge-to-edge mode is enabled by default. We'll handle this by
        // considering all layouts on API 35+ as ignoring safe areas. For a layout that doesn't
        // ignore safe areas, we'll return true so that insets can be applied to the layout at the
        // top level.
        if (isAtLeastApi35 || shouldIgnoreSafeArea) {
            enableEdgeToEdge()

            if (!isAtLeastApi35) {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                    window.addFlags(FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                            FLAG_LAYOUT_NO_LIMITS or FLAG_LAYOUT_IN_SCREEN
                    )
                }

                transparentSystemBars()
            }
        }

        return isAtLeastApi35 && !shouldIgnoreSafeArea
    }

    @Suppress("DEPRECATION")
    private fun transparentSystemBars() {
        // These are both deprecated and do nothing on API 35+,
        // but we still want to set them for older API levels.
        window.statusBarColor = android.R.color.transparent
        window.navigationBarColor = android.R.color.transparent
    }

    private fun setOrientationLock(placement: ModalPlacement) {
        try {
            if (placement.orientationLock != null) {
                if (Build.VERSION.SDK_INT != Build.VERSION_CODES.O) {
                    when (placement.orientationLock) {
                        Orientation.PORTRAIT ->
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        Orientation.LANDSCAPE ->
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else -> Unit
                    }
                } else {
                    // Orientation locking isn't allowed on API 26 for transparent activities,
                    // so we'll do the best we can and inherit the parent activity's orientation.
                    // If the parent activity is locked to an orientation, we'll be locked to that
                    // orientation, too. Otherwise, rotation will be allowed even though the layout
                    // requested it not be.
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_BEHIND
                }
            }
        } catch (e: Exception) {
            UALog.e(e, "Unable to set orientation lock.")
        }
    }

    public companion object {
        // Asset loader
        public const val EXTRA_DISPLAY_ARGS_LOADER: String =
            "com.urbanairship.android.layout.ui.EXTRA_DISPLAY_ARGS_LOADER"

        private const val KEY_DISPLAY_TIME = "display_time"
    }
}
