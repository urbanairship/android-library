/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.ui

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RestrictTo
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat.setDecorFitsSystemWindows
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
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.Reporter
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.property.ModalPlacement
import com.urbanairship.android.layout.property.Orientation
import com.urbanairship.android.layout.reporting.DisplayTimer
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.view.ModalView
import com.urbanairship.util.parcelableExtra
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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

    private val isAtLeastApi35 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
    private val isAtLeastApi28 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    private lateinit var loader: DisplayArgsLoader
    private lateinit var externalListener: ThomasListenerInterface
    private lateinit var reporter: Reporter
    private lateinit var displayTimer: DisplayTimer
    private lateinit var modelEnvironment: ModelEnvironment

    private var disableBackButton = false
    private var dismissReported = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Always enable edge-to-edge for this Activity.
        // If the displayed layout does not ignore safe areas,
        // we'll inset it to only draw in the safe area.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        loader = parcelableExtra(EXTRA_DISPLAY_ARGS_LOADER) ?: run {
            UALog.e("Missing layout args loader")
            finish()
            return@onCreate
        }

        val restoredTime = savedInstanceState?.getLong(KEY_DISPLAY_TIME) ?: 0
        displayTimer = DisplayTimer(this, restoredTime)

        try {
            val args = loader.getDisplayArgs()
            externalListener = args.listener
            reporter = ExternalReporter(externalListener)

            val presentation = (args.payload.presentation as? ModalPresentation) ?: run {
                UALog.e("Not a modal presentation")
                finish()
                return@onCreate
            }

            disableBackButton = presentation.disableBackButton

            onBackPressedDispatcher.addCallback(this) {
                if (!disableBackButton) {
                    reportDismissFromOutside()
                    finishAfterTransition()
                }
            }

            val placement = presentation.getResolvedPlacement(this)
            setOrientationLock(placement)

            handleIgnoreSafeAreas(placement.shouldIgnoreSafeArea())

            modelEnvironment = viewModel.getOrCreateEnvironment(
                reporter = reporter,
                actionRunner = args.actionRunner,
                displayTimer = displayTimer
            )

            val model = viewModel.getOrCreateModel(args.payload.view, modelEnvironment)

            observeLayoutEvents(modelEnvironment.layoutEvents)
            reportStateChange(modelEnvironment.layoutEvents)

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

                if (presentation.dismissOnTouchOutside) {
                    setOnClickOutsideListener {
                        reportDismissFromOutside()
                        finishAfterTransition()
                    }
                }
            }

            setContentView(view)

            ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
                val systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
                val ignoreSafeArea = placement.shouldIgnoreSafeArea()

                when {
                    ignoreSafeArea -> {
                        // Extend edge-to-edge for system bars, but keep content above the IME.
                        v.updatePadding(
                            top = 0,
                            bottom = imeInsets.bottom,
                            left = 0,
                            right = 0
                        )
                        view.applyKeyboardInset(imeInsets.bottom)
                        // Subtract IME from the system-bar bottom so children respecting
                        // safe areas don't re-apply navbar padding inside the IME region.
                        val adjustedSystemBars = Insets.of(
                            systemBarInsets.left,
                            systemBarInsets.top,
                            systemBarInsets.right,
                            maxOf(0, systemBarInsets.bottom - imeInsets.bottom)
                        )
                        WindowInsetsCompat.Builder(windowInsets)
                            .setInsets(WindowInsetsCompat.Type.systemBars(), adjustedSystemBars)
                            .build()
                    }
                    isAtLeastApi28 -> {
                        // Absorb system-bar and IME insets as padding on the ModalView.
                        // The modalFrame uses MATCH_CONSTRAINT, so it naturally fills the
                        // remaining inner area without any margin gymnastics.
                        v.updatePadding(
                            top = systemBarInsets.top,
                            bottom = maxOf(systemBarInsets.bottom, imeInsets.bottom),
                            left = systemBarInsets.left,
                            right = systemBarInsets.right
                        )
                        view.applyKeyboardInset(imeInsets.bottom)
                        WindowInsetsCompat.CONSUMED
                    }
                    else -> {
                        // API 27 and below: enableEdgeToEdge + setDecorFitsSystemWindows(true)
                        // means the decor absorbs system-bar insets AND adjustResize shrinks
                        // the window when the IME shows. ModalView handles the resulting
                        // size change via onSizeChanged().
                        v.updatePadding(0, 0, 0, 0)
                        WindowInsetsCompat.CONSUMED
                    }
                }
            }

            ViewCompat.requestApplyInsets(view)
        } catch (e: LoadException) {
            UALog.e(e, "Failed to load model!")
            finish()
        } catch (e: ModelFactoryException) {
            UALog.e(e, "Failed to load model!")
            finish()
        }
    }

    /** Override to optionally disable the back button. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // If the back button is pressed, and it's disabled, we ignore it.
        if (keyCode == KeyEvent.KEYCODE_BACK && disableBackButton) {
            UALog.v("Ignored back button press. Back button is disabled for this Scene.")
            return true
        }
        // Otherwise, let the event through.
        return super.onKeyDown(keyCode, event)
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

    private fun observeLayoutEvents(events: Flow<LayoutEvent>) = lifecycleScope.launch {
        events
            .filterIsInstance<LayoutEvent.Finish>()
            .collect { finishAfterTransition() }
    }

    private fun reportStateChange(events: Flow<LayoutEvent>) = lifecycleScope.launch {
        events
            .filterIsInstance<LayoutEvent.StateUpdate>()
            .distinctUntilChanged()
            .collect {
                externalListener.onStateChanged(it.state)
            }
    }

    private fun reportDismissFromOutside(state: LayoutData = LayoutData.EMPTY) {
        if (dismissReported) {
            UALog.e { "Dismissed already called! not reporting dismiss again." }
            return
        }

        reporter.report(
            event = ReportingEvent.Dismiss(
                data = ReportingEvent.DismissData.UserDismissed,
                displayTime = displayTimer.time.milliseconds,
                context = state
            )
        )
        dismissReported = true
    }

    private fun handleIgnoreSafeAreas(shouldIgnoreSafeArea: Boolean) {
        when {
            // If we're ignoring safe area and on API 34 or below, make the system bars transparent.
            shouldIgnoreSafeArea && !isAtLeastApi35 -> transparentSystemBars()
            // AppCompat decor on API 27 and below doesn't truly go edge-to-edge with a translucent
            // window. We'll let the platform keep us in the safe area.
            !shouldIgnoreSafeArea && !isAtLeastApi28 -> setDecorFitsSystemWindows(window, true)
        }
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
                requestedOrientation = if (Build.VERSION.SDK_INT != Build.VERSION_CODES.O) {
                    when (placement.orientationLock) {
                        Orientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        Orientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                } else {
                    // Orientation locking isn't allowed on API 26 for transparent activities,
                    // so we'll do the best we can and inherit the parent activity's orientation.
                    // If the parent activity is locked to an orientation, we'll be locked to that
                    // orientation, too. Otherwise, rotation will be allowed even though the layout
                    // requested it to not be.
                    ActivityInfo.SCREEN_ORIENTATION_BEHIND
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
