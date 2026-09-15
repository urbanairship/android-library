/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.screenshot

import android.app.Application
import android.os.Build
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.urbanairship.Airship
import com.urbanairship.Platform
import com.urbanairship.UrlAllowList
import com.urbanairship.android.layout.BannerPresentation
import com.urbanairship.android.layout.EmbeddedPresentation
import com.urbanairship.android.layout.ModalPresentation
import com.urbanairship.android.layout.R
import com.urbanairship.android.layout.environment.DefaultViewEnvironment
import com.urbanairship.android.layout.environment.Reporter
import com.urbanairship.android.layout.environment.ThomasActionRunner
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.android.layout.model.AnyModel
import com.urbanairship.android.layout.reporting.DisplayTimer
import com.urbanairship.android.layout.ui.LayoutViewModel
import com.urbanairship.android.layout.ui.ThomasBannerView
import com.urbanairship.android.layout.ui.ThomasEmbeddedView
import com.urbanairship.android.layout.view.ModalView
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every Thomas scene fixture and writes a PNG for the visual-diff harness (`uitests/`).
 *
 * Excluded from the ordinary unit test run — see `wantsScreenshots` in the module's build.gradle —
 * because a full sweep is far too slow to sit in the check that runs on every PR and merge.
 *
 * The presentation identity is pinned rather than inherited: the Robolectric SDK level and the
 * display qualifiers below are what every baseline is captured against, so changing either
 * invalidates the whole baseline set. Keep `robolectric.sdk` in `uitests/config.json` in step with
 * the level here — that is what names the baseline artifact.
 *
 * 32 is the ceiling, not a preference: Robolectric 4.16's native graphics has no text measurement
 * for 33 or above, which fails as `UnsatisfiedLinkError` in `MeasuredText.nGetExtent`. It is past
 * `Build.VERSION_CODES.R`, so the renderer takes its modern window-size path
 * (`ResourceUtils.getWindowHeightPixels`) rather than the pre-30 `displayMetrics` fallback. That
 * path still resolves to the full display here, because Robolectric reports no system bar insets,
 * so `ignore_safe_area` makes no difference to a capture and safe-area geometry goes unexercised.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [32], qualifiers = "w411dp-h891dp-xhdpi")
internal class SceneScreenshotTest(
    private val name: String,
    private val fixture: ThomasFixture,
    private val total: Int
) {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var activity: ComponentActivity

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Rendering a whole scene tree reaches eight Airship accessors, every one of which goes
        // through requireReadyInstance(). A real takeOff is not an option: land() is module-internal
        // so state cannot be reset between cases, takeOff is once per JVM, and reading an accessor
        // mid-takeoff deadlocks the main thread through runBlocking.
        // Both modes are needed: `application`, `platform`, `runtimeConfig` and `urlAllowList` are
        // @JvmStatic and compile to static calls that mockkObject never sees, while the rest are
        // instance members on the singleton that mockkStatic never sees.
        mockkObject(Airship)
        mockkStatic(Airship::class)
        every { Airship.imageLoader } returns FakeImageLoader
        every { Airship.platform } returns Platform.ANDROID
        every { Airship.application } returns ApplicationProvider.getApplicationContext<Application>()
        every { Airship.isFlyingOrTakingOff } returns true
        every { Airship.runtimeConfig } returns mockk(relaxed = true)
        every { Airship.inputValidator } returns mockk(relaxed = true)
        every { Airship.internalAi } returns mockk(relaxed = true)
        every { Airship.urlAllowList } returns mockk<UrlAllowList>(relaxed = true) {
            every { isAllowed(any(), any()) } returns true
        }

        // Robolectric's default host theme carries an ActionBar, which takes 56dp off the content
        // frame and leaves every scene laid out in a viewport 112px shorter than the window size
        // the renderer itself reads back. The layout module's own theme is NoActionBar, and is what
        // the production hosts wrap their context in anyway.
        activity = Robolectric.buildActivity(ComponentActivity::class.java).let { controller ->
            controller.get().setTheme(R.style.UrbanAirship_Layout)
            controller.setup().get()
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Airship::class)
        unmockkObject(Airship)
        Dispatchers.resetMain()
    }

    @Test
    fun capture() {
        skipReason()?.let { reason ->
            ScreenshotManifest.skipped(total, fixture.path, reason)
            return
        }

        // A fixture the renderer cannot decode is recorded and passed over rather than failed, the
        // way the iOS generator does it: the corpus is shared with web and iOS and drifts ahead of
        // any one platform's schema support. The diff still catches it, as a baseline with no
        // matching shot reports `gone`.
        val stubbed = try {
            SceneStubs.apply(fixture.read())
        } catch (e: Exception) {
            ScreenshotManifest.skipped(total, fixture.path, "fixture does not parse: ${e.message}")
            return
        }

        val layout = try {
            LayoutInfo(stubbed.payload)
        } catch (e: Exception) {
            ScreenshotManifest.skipped(total, fixture.path, "does not decode: ${e.message}")
            return
        }

        val target = host(layout)
        // Params passed explicitly: the single-argument setContentView hard-codes MATCH_PARENT on
        // both axes and throws away the view's own, which would stretch a bounded embedded host to
        // the full window.
        activity.setContentView(target.root, ViewGroup.LayoutParams(target.width, target.height))

        // The image views load inside doOnAttach, and a banner reveals its frame in a post{} with a
        // delayed transition, so both only land once the looper has drained.
        shadowOf(Looper.getMainLooper()).idle()

        // The host activity's decor leaves its content frame short of the display, so the pass
        // above settles the scene at the wrong height. Re-measuring alone does not fix it:
        // ConstraintLayout only re-solves its children from `setChildrenConstraints()` when the
        // hierarchy is marked dirty, and nothing here touches their LayoutParams, so a resized
        // parent would keep the previous pass's child bounds and leave the difference unpainted.
        target.root.requestLayout()
        target.root.measure(
            View.MeasureSpec.makeMeasureSpec(target.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(target.height, View.MeasureSpec.EXACTLY)
        )
        target.root.layout(0, 0, target.width, target.height)
        shadowOf(Looper.getMainLooper()).idle()

        val file = File(ScreenshotPaths.shots, "${name}__p0.png")
        file.parentFile?.mkdirs()
        target.root.captureRoboImage(filePath = file.absolutePath)

        ScreenshotManifest.captured(total, fixture.path, "${name}__p0", stubbed.stubs)
    }

    /** A fixture the config pins as unsupported, with the reason it gives. */
    private fun skipReason(): String? =
        config().opt("sweep").optMap().opt("skip").optMap().opt(fixture.path).string

    /**
     * `@Config` needs a compile-time constant, so the SDK level is stated twice: here and as
     * `robolectric.sdk` in `config.json`, which is what names the baseline artifact. Drift between
     * them would label a set of captures with an API level they were not taken at, so it fails the
     * run rather than going unnoticed.
     */
    private fun config(): JsonMap {
        val config = JsonValue.parseString(ScreenshotPaths.config.readText()).optMap()
        val declared = config.opt("robolectric").optMap().opt("sdk").getInt(0)
        check(declared == Build.VERSION.SDK_INT) {
            "robolectric.sdk is $declared in ${ScreenshotPaths.config.name} but the capture runs " +
                "at API ${Build.VERSION.SDK_INT}; update @Config(sdk = ...) or the config to match"
        }
        return config
    }

    private class Target(val root: View, val width: Int, val height: Int)

    /**
     * Mirrors what each presentation's production host does, minus the window: `ModalActivity`,
     * `BannerLayout.makeView` and `EmbeddedLayout.makeView`. The theme wrapper is not optional —
     * without it the layout's own `?attr/colorControlNormal` tints resolve against whatever theme
     * the test activity carries.
     */
    private fun host(layout: LayoutInfo): Target {
        val metrics = activity.resources.displayMetrics
        val themed = ContextThemeWrapper(activity, R.style.UrbanAirship_Layout)

        val viewModel = LayoutViewModel()
        val modelEnvironment = viewModel.getOrCreateEnvironment(
            reporter = mockk<Reporter>(relaxUnitFun = true),
            displayTimer = DisplayTimer(activity),
            actionRunner = { _, _ -> }
        )
        val model: AnyModel = viewModel.getOrCreateModel(layout.view, modelEnvironment)

        fun environment(ignoreSafeAreas: Boolean) = DefaultViewEnvironment(
            activity = activity,
            activityMonitor = mockk<ActivityMonitor>(relaxed = true),
            webViewClientFactory = null,
            imageCache = null,
            isIgnoringSafeAreas = ignoreSafeAreas,
            layoutVersion = layout.version
        )

        return when (val presentation = layout.presentation) {
            is ModalPresentation -> {
                val placement = presentation.getResolvedPlacement(themed)
                val view = ModalView(themed, model, presentation, environment(placement.shouldIgnoreSafeArea()))
                Target(view, metrics.widthPixels, metrics.heightPixels)
            }

            is BannerPresentation -> {
                val placement = presentation.getResolvedPlacement(themed)
                val view = ThomasBannerView(themed, model, presentation, environment(placement.shouldIgnoreSafeArea()))
                Target(view, metrics.widthPixels, metrics.heightPixels)
            }

            is EmbeddedPresentation -> {
                // An embedded scene renders into whatever box its host gives it, so the box has to
                // be part of the pinned identity too. Bounded the way the iOS harness bounds its
                // host view, rather than letting a `height: 100%` resolve against the whole screen.
                val height = (EMBEDDED_HOST_HEIGHT_DP * metrics.density).toInt()
                val view = ThomasEmbeddedView(
                    context = themed,
                    model = model,
                    presentation = presentation,
                    environment = environment(false),
                    fillWidth = true,
                    fillHeight = true
                )
                val frame = FrameLayout(themed).apply {
                    layoutParams = ViewGroup.LayoutParams(metrics.widthPixels, height)
                    addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                }
                Target(frame, metrics.widthPixels, height)
            }

            else -> error("unsupported presentation: ${presentation.type}")
        }
    }

    internal companion object {
        private const val EMBEDDED_HOST_HEIGHT_DP = 320
        private const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT

        /**
         * Pure file IO on purpose. This runs on the normal classloader, outside the Robolectric
         * sandbox, where `org.json` is the stub from android.jar — and with `returnDefaultValues`
         * on, parsing here would quietly return empty values instead of failing.
         */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun fixtures(): Collection<Array<Any>> {
            val discovered = ThomasFixture.discover(ScreenshotPaths.fixtures)
            return discovered.map { arrayOf(it.name, it, discovered.size) }
        }
    }
}
