/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.environment

import android.content.Context
import android.view.View.MeasureSpec
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.property.WindowSize
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
public class ThomasEnvironmentTest {

    private val environment = ThomasEnvironment(
        darkMode = true,
        textScale = 1.5,
        windowWidth = 360.0,
        windowHeight = 640.0,
        windowSize = WindowSize.SMALL,
        safeWindowWidth = 360.0,
        safeWindowHeight = 600.0,
        locale = Locale.CANADA_FRENCH
    )

    @Test
    public fun testJson() {
        val expected = jsonMapOf(
            "platform" to "android",
            "dark_mode" to true,
            "text_scale" to 1.5,
            "window" to jsonMapOf(
                "width" to 360.0,
                "height" to 640.0,
                "orientation" to "portrait",
                "size" to "small"
            ),
            "safe_window" to jsonMapOf(
                "width" to 360.0,
                "height" to 600.0
            ),
            "layout_direction" to "ltr",
            "locale" to jsonMapOf(
                "identifier" to "fr-CA",
                "language" to "fr",
                "region" to "CA"
            )
        ).toJsonValue()

        assertEquals(expected, environment.toJsonValue())
    }

    @Test
    public fun testTheFrameIsIncludedOnceItIsLaidOut() {
        val framed = environment.copy(frameWidth = 320.0, frameHeight = 200.0)

        assertEquals(
            jsonMapOf("width" to 320.0, "height" to 200.0).toJsonValue(),
            framed.toJsonValue().optMap().opt("frame")
        )
    }

    @Test
    public fun testAFrameAxisTheContentDecidesIsOmitted() {
        val widthOnly = environment.copy(frameWidth = 320.0)

        assertEquals(
            jsonMapOf("width" to 320.0).toJsonValue(),
            widthOnly.toJsonValue().optMap().opt("frame")
        )
    }

    @Test
    public fun testReadsTheFrameInDp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val density = context.resources.displayMetrics.density.toDouble()

        val read = ThomasEnvironment.from(context, EnvironmentFrame(width = 300, height = null))

        assertEquals(300 / density, read.frameWidth!!, 0.0)
        assertNull(read.frameHeight)
    }

    @Test
    public fun testTheTrackerReportsOnlyTheAxesTheHostFixed() {
        val reported = mutableListOf<EnvironmentFrame>()
        val tracker = EnvironmentFrameTracker { frame, _ -> reported.add(frame) }

        tracker.onMeasure(spec(300, MeasureSpec.EXACTLY), spec(1000, MeasureSpec.AT_MOST))
        tracker.onLayout(300, 120)
        tracker.onLayout(300, 120)
        tracker.onMeasure(spec(300, MeasureSpec.EXACTLY), spec(0, MeasureSpec.UNSPECIFIED))
        tracker.onLayout(300, 140)
        tracker.onMeasure(spec(400, MeasureSpec.EXACTLY), spec(200, MeasureSpec.EXACTLY))
        tracker.onLayout(400, 200)

        assertEquals(
            listOf(EnvironmentFrame(300, null), EnvironmentFrame(400, 200)),
            reported
        )
    }

    @Test
    public fun testTheTrackerReportsAfterAConfigurationChangeEvenAtTheSameFrame() {
        val reported = mutableListOf<EnvironmentFrame>()
        val tracker = EnvironmentFrameTracker { frame, _ -> reported.add(frame) }
        tracker.onMeasure(spec(300, MeasureSpec.EXACTLY), spec(200, MeasureSpec.EXACTLY))
        tracker.onLayout(300, 200)

        tracker.onConfigurationChanged()
        tracker.onLayout(300, 200)
        tracker.onLayout(300, 200)

        assertEquals(listOf(EnvironmentFrame(300, 200), EnvironmentFrame(300, 200)), reported)
    }

    @Test
    public fun testTheTrackerMarksOnlyItsFirstReport() {
        val firsts = mutableListOf<Boolean>()
        val tracker = EnvironmentFrameTracker { _, isFirst -> firsts.add(isFirst) }
        tracker.onMeasure(spec(300, MeasureSpec.EXACTLY), spec(200, MeasureSpec.EXACTLY))

        tracker.onLayout(300, 200)
        tracker.onLayout(320, 200)
        tracker.onConfigurationChanged()
        tracker.onLayout(320, 200)

        assertEquals(listOf(true, false, false), firsts)
    }

    @Test
    @Config(qualifiers = "ar-rEG-ldrtl")
    public fun testReadsTheLayoutDirectionAndLocale() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val json = ThomasEnvironment.from(context, null).toJsonValue().optMap()

        assertEquals("rtl", json.opt("layout_direction").string)
        assertEquals(
            jsonMapOf("identifier" to "ar-EG", "language" to "ar", "region" to "EG").toJsonValue(),
            json.opt("locale")
        )
    }

    @Test
    public fun testALocaleWithNoRegionOmitsIt() {
        val french = environment.copy(locale = Locale.FRENCH).toJsonValue().optMap().opt("locale")

        assertEquals(jsonMapOf("identifier" to "fr", "language" to "fr").toJsonValue(), french)
    }

    private fun spec(size: Int, mode: Int) = MeasureSpec.makeMeasureSpec(size, mode)

    @Test
    public fun testAWindowWiderThanItIsTallIsLandscape() {
        val landscape = environment.copy(windowWidth = 640.0, windowHeight = 360.0)

        assertEquals("landscape", landscape.toJsonValue().optMap().opt("window").optMap().opt("orientation").string)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-night", fontScale = 1.3f)
    public fun testReadsTheWindowAndConfiguration() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val read = ThomasEnvironment.from(context, null)

        assertTrue(read.darkMode)
        assertEquals(1.3, read.textScale, 0.0)
        assertEquals(360.0, read.windowWidth, 1.0)
        assertEquals(640.0, read.windowHeight, 1.0)
        assertTrue(read.safeWindowHeight <= read.windowHeight)
    }

    @Test
    public fun testIsInStateWithoutAStateController() {
        val json = state(layoutState = emptyMap()).toJsonValue()

        assertEquals(environment.toJsonValue(), json.optMap().opt("\$environment"))
    }

    @Test
    public fun testAValueWrittenToTheLayoutStateIsNotRead() {
        val written = mapOf("\$environment" to JsonValue.wrap("written"))

        assertEquals(environment.toJsonValue(), state(written).toJsonValue().optMap().opt("\$environment"))
        assertFalse(state(written, environment = null).toJsonValue().optMap().containsKey("\$environment"))
    }

    @Test
    public fun testAPredicateMatchesTheWindow() {
        val narrow = JsonPredicate.parse(
            JsonValue.parseString(
                """{"scope": ["${'$'}environment", "window", "width"], "value": {"at_most": 360}}"""
            )
        )

        assertTrue(narrow.apply(state(emptyMap())))
        assertFalse(narrow.apply(state(emptyMap(), environment.copy(windowWidth = 600.0))))
    }

    private fun state(
        layoutState: Map<String, JsonValue>,
        environment: ThomasEnvironment? = this.environment
    ): ThomasState = ThomasState(
        layout = State.Layout(identifier = "test-layout").apply { state = layoutState },
        form = null,
        pager = null,
        video = null,
        asyncView = null,
        capabilities = ThomasCapabilities(environment = environment)
    )
}
