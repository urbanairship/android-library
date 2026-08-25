/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.model.ContainerLayoutModel
import com.urbanairship.android.layout.ui.LayoutViewModel
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Container items declared as `width: <percent>`, `height: auto`, `aspect_ratio: <n>`.
 *
 * These are routed to `frameHeightRatios` and deliberately excluded from `framePercentWidths`, so
 * `resolvePercentFrames` never sees them and nothing resolves their percentage. What sizes them
 * instead is the `"H,ratio:1"` dimension ratio the constraint set leaves on the view, which
 * ConstraintLayout resolves against the container *height*:
 *
 * ```
 * width = containerHeight / ratio        height = width / ratio
 * ```
 *
 * The declared percentage and the container width are both ignored, so items overflow and clip —
 * a 60%/1.0 item in a 300x500 box measures 499x499. Measured, not inferred; see the table in the
 * ignored cases below.
 *
 * The `.fit` clamp masks this whenever the derived height overflows a fixed container height,
 * which is why some ratio cases look right today.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class ContainerLayoutRatioTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val EXACTLY = View.MeasureSpec.EXACTLY
    private val AT_MOST = View.MeasureSpec.AT_MOST
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val mockEnv: ModelEnvironment = mockk(relaxed = true) {
        every { layoutState } returns LayoutState.EMPTY
        every { layoutEvents } returns emptyFlow()
        every { modelScope } returns testScope
    }
    private val viewEnv: ViewEnvironment = mockk(relaxed = true) {
        every { isIgnoringSafeAreas } returns false
    }

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }


    // ---- Cases that already work, kept as regression guards ----------------------------------

    /** A percent item with no ratio is resolved by `resolvePercentFrames` and is already correct. */
    @Test
    public fun testPlainPercentItemResolvesAgainstTheContainer() {
        val view = measure(item("60%", "40%"), 300, EXACTLY, 500, EXACTLY)
        assertFrame(view, 0, 180, 200)
    }

    /**
     * `width: auto` + fixed height + ratio never enters `frameHeightRatios`; the constraint set's
     * `"W,ratio:1"` branch handles it. Untouched by the percent work either way.
     */
    @Test
    public fun testAutoWidthFixedHeightRatioIsUnaffected() {
        val view = measure(item("auto", "80", ratio = 1.778), 300, EXACTLY, 500, EXACTLY)
        assertFrame(view, 0, 142, 80)
    }

    /**
     * When the derived height overflows a fixed container the `.fit` clamp reduces both axes to
     * the largest ratio-preserving box that fits. This holds today and must keep holding — it is
     * the one path where the ratio item is expected to end up narrower than its declared width.
     */
    @Test
    public fun testRatioIsClampedToFitAFixedContainerHeight() {
        val view = measure(item("100%", "auto", ratio = 0.5), 300, EXACTLY, 100, EXACTLY)
        assertFrame(view, 0, 50, 100)
    }

    // ---- The bug. Expectations are what the item declares; all fail today. --------------------

    /** Measures 499x499 today: `containerHeight / ratio`, overflowing the 300pt container. */
    @Test
    public fun testPercentRatioItemResolvesAgainstTheContainerWidth() {
        val view = measure(item("60%", "auto", ratio = 1.0), 300, EXACTLY, 500, EXACTLY)
        assertFrame(view, 0, 180, 180)
    }

    /**
     * The shape most likely to appear in real content — a full-width ratio box. Measures 250x125
     * today (`500 / 2.0`), so it is both too narrow and independent of the container width.
     */
    @Test
    public fun testFullWidthRatioItemFillsTheContainer() {
        val view = measure(item("100%", "auto", ratio = 2.0), 300, EXACTLY, 500, EXACTLY)
        assertFrame(view, 0, 300, 150)
    }

    /**
     * A percentage covers the space the item occupies, margins included — the rule
     * `resolvePercentFrames` applies to every other percent item, and the one iOS applies. Margins
     * have no effect at all today.
     */
    @Test
    public fun testPercentRatioItemTakesItsMarginsOutOfItsShare() {
        val view = measure(item("60%", "auto", ratio = 1.0, margin = 20), 300, EXACTLY, 500, EXACTLY)
        assertFrame(view, 0, 140, 140)
    }

    /**
     * An auto-width container takes its width from its non-percent items, so the 100pt sibling is
     * the base and the ratio item is 60% of that.
     */
    @Test
    public fun testPercentRatioItemResolvesAgainstAnAutoContainersContentBox() {
        val view = measure(
            item("60%", "auto", ratio = 1.0) + "," + item("100", "50"),
            300, AT_MOST, 500, EXACTLY
        )
        assertFrame(view, 0, 60, 60)
        assertFrame(view, 1, 100, 50)
    }

    /**
     * Nothing supplies a length, so there is no base to take a percentage of. A ratio item has to
     * fall back the same way its non-ratio sibling does — asserted against that sibling rather than
     * a fixed number, since what a container falls back *to* is a separate question from whether
     * the ratio path agrees with the rest.
     *
     * (Today both claim the whole `AT_MOST` rather than collapsing to content, unlike the stacks.
     * Pre-existing, and true of every percent item here; worth its own look.)
     */
    @Test
    public fun testPercentRatioItemFallsBackTheSameWayAsAPlainPercentItem() {
        val plain = measure(item("60%", "auto"), 300, AT_MOST, 500, EXACTLY)
        val ratio = measure(item("60%", "auto", ratio = 1.0), 300, AT_MOST, 500, EXACTLY)

        assertEquals(
            "ratio item fell back to a different width than a plain percent item",
            plain.getChildAt(0).measuredWidth,
            ratio.getChildAt(0).measuredWidth
        )
        // ...and the height follows from that width, rather than from the container height.
        assertFrame(ratio, 0, plain.getChildAt(0).measuredWidth, plain.getChildAt(0).measuredWidth)
    }

    // ---- harness ------------------------------------------------------------------------------


    private fun measure(
        itemsJson: String,
        width: Int,
        widthMode: Int,
        height: Int,
        heightMode: Int
    ): ContainerLayoutView = container(itemsJson).apply {
        measure(
            View.MeasureSpec.makeMeasureSpec(width, widthMode),
            View.MeasureSpec.makeMeasureSpec(height, heightMode)
        )
    }

    private fun assertFrame(view: ContainerLayoutView, index: Int, width: Int, height: Int) {
        val frame = view.getChildAt(index)
        assertEquals("frame[$index] width", width, frame.measuredWidth)
        assertEquals("frame[$index] height", height, frame.measuredHeight)
    }

    private fun item(
        width: String,
        height: String,
        ratio: Double? = null,
        margin: Int? = null
    ): String {
        val size = buildString {
            append("""{"width":${dim(width)},"height":${dim(height)}""")
            if (ratio != null) append(""","aspect_ratio":$ratio""")
            append("}")
        }
        val marginJson = margin?.let {
            ""","margin":{"top":$it,"bottom":$it,"start":$it,"end":$it}"""
        } ?: ""
        return """
            {
              "position": {"horizontal": "center", "vertical": "center"},
              "size": $size$marginJson,
              "view": {"type": "empty_view"}
            }
        """.trimIndent()
    }

    /** Percent and auto are strings in the schema; points are bare numbers. */
    private fun dim(value: String): String =
        if (value.endsWith("%") || value == "auto") "\"$value\"" else value

    private fun container(itemsJson: String): ContainerLayoutView {
        val json = JsonValue.parseString(
            """{"type":"container","items":[$itemsJson]}"""
        ).requireMap()
        val info = ViewInfo.viewInfoFromJson(json)
        val model = LayoutViewModel().getOrCreateModel(info, mockEnv) as ContainerLayoutModel
        return model.createView(context, viewEnv, null) as ContainerLayoutView
    }
}
