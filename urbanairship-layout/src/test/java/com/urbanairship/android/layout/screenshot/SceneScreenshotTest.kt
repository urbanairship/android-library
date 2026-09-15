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

/** Stated here rather than inline so the annotation and the drift check cannot disagree. */
private const val QUALIFIERS = "w411dp-h891dp-xhdpi"

/**
 * Renders every Thomas scene fixture and writes a PNG for the visual-diff harness (`uitests/`).
 *
 * Excluded from the ordinary unit test run — see `wantsScreenshots` in the module's build.gradle —
 * because a full sweep is far too slow to sit in the check that runs on every PR and merge.
 *
 * The presentation identity is pinned rather than inherited: the SDK level and the qualifiers are
 * what every baseline is captured against, so changing either invalidates the whole set. Both are
 * restated in `uitests/config.json` for the harness, and [config] fails the run if they drift.
 *
 * 35 is a ceiling rather than a preference. Robolectric's native graphics has no text measurement
 * above it — 36 fails as `UnsatisfiedLinkError` in `MeasuredText.nGetExtent` — and that is
 * independent of the Roborazzi version.
 *
 * Being past `Build.VERSION_CODES.R` means the renderer takes its modern window-size path
 * (`ResourceUtils.getWindowHeightPixels`) rather than the pre-30 `displayMetrics` fallback. That
 * path still resolves to the full display here, because Robolectric reports no system bar insets,
 * so `ignore_safe_area` makes no difference to a capture and safe-area geometry goes unexercised.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = QUALIFIERS)
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

        val model = try {
            buildModel(layout)
        } catch (e: Exception) {
            ScreenshotManifest.skipped(total, fixture.path, "does not build: ${e.message}")
            return
        }

        val target = host(layout, model)
        // Params passed explicitly: the single-argument setContentView hard-codes MATCH_PARENT on
        // both axes and throws away the view's own, which would stretch a bounded embedded host to
        // the full window.
        activity.setContentView(target.root, ViewGroup.LayoutParams(target.width, target.height))

        // The image views load inside doOnAttach, and a banner reveals its frame in a post{} with a
        // delayed transition, so both only land once the looper has drained.
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
     * `config.json` restates two things the build already knows, because neither can be read from
     * it: `@Config` needs a compile-time constant, and the harness needs the values without a
     * Gradle model. Both feed what a capture is labelled with — the SDK names the baseline
     * artifact, the Roborazzi version goes into `provenance.json` — so drift would attribute a set
     * of screenshots to a toolchain that did not produce them. Fail instead of mislabelling.
     */
    private fun config(): JsonMap {
        val config = JsonValue.parseString(ScreenshotPaths.config.readText()).optMap()
        val name = ScreenshotPaths.config.name

        val sdk = config.opt("robolectric").optMap().opt("sdk").getInt(0)
        check(sdk == Build.VERSION.SDK_INT) {
            "robolectric.sdk is $sdk in $name but the capture runs at API " +
                "${Build.VERSION.SDK_INT}; update @Config(sdk = ...) or the config to match"
        }

        val roborazzi = config.opt("roborazzi").optMap().opt("version").string
        val resolved = System.getProperty("thomas.roborazzi.version")
        check(roborazzi == resolved) {
            "roborazzi.version is $roborazzi in $name but the build resolves $resolved; " +
                "update the config or the version catalog to match"
        }

        // Qualifiers decide the capture's pixel dimensions but, unlike the SDK, do not appear in
        // the baseline label. Drifting them would silently reuse the same artifact name for a
        // different geometry and turn the next diff into 122 size mismatches.
        val qualifiers = config.opt("robolectric").optMap().opt("qualifiers").string
        check(qualifiers == QUALIFIERS) {
            "robolectric.qualifiers is $qualifiers in $name but the capture runs at " +
                "$QUALIFIERS; update @Config(qualifiers = ...) or the config to match"
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
    /**
     * Building the model tree is where the payload is validated — a form nested somewhere the
     * schema does not allow one fails here rather than at decode. So a failure is the shared
     * corpus drifting ahead of Android's support, the same class as a decode failure, and is
     * recorded rather than failed. Rendering stays outside this on purpose: a crash there would
     * be our bug, not the fixture's, and should stop the sweep.
     */
    private fun buildModel(layout: LayoutInfo): AnyModel {
        val viewModel = LayoutViewModel()
        val modelEnvironment = viewModel.getOrCreateEnvironment(
            reporter = mockk<Reporter>(relaxUnitFun = true),
            displayTimer = DisplayTimer(activity),
            actionRunner = { _, _ -> }
        )
        return viewModel.getOrCreateModel(layout.view, modelEnvironment)
    }

    private fun host(layout: LayoutInfo, model: AnyModel): Target {
        val metrics = activity.resources.displayMetrics
        val themed = ContextThemeWrapper(activity, R.style.UrbanAirship_Layout)

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
