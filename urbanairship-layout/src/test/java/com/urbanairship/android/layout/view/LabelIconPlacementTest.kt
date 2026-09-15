/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.text.Spanned
import android.content.pm.ApplicationInfo
import android.text.TextPaint
import android.text.style.ImageSpan
import android.text.style.ReplacementSpan
import android.view.View
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.info.LabelInfo
import com.urbanairship.android.layout.model.ItemProperties
import com.urbanairship.android.layout.model.LabelModel
import com.urbanairship.android.layout.model.ModelProperties
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Both icons travel with the text, so a label wider than its text — a centred button label —
 * keeps them beside the words rather than against its edges, and the authored `space` between
 * icon and text means the same thing on both sides. This matches the web renderer; a compound
 * drawable can do neither.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class LabelIconPlacementTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockEnv: ModelEnvironment = mockk(relaxed = true) {
        every { modelScope } returns TestScope(testDispatcher)
        every { layoutState } returns LayoutState.EMPTY
    }

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // The platform gates all RTL resolution on the host app declaring android:supportsRtl,
        // and a library's own manifest can't. Without this, setTextDirection is a silent no-op.
        RuntimeEnvironment.getApplication().applicationInfo.also {
            it.flags = it.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        }
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testStartIconIsInlineWithTheText() {
        val view = labelView(startIcon = true)

        assertNoCompoundDrawables(view)
        assertEquals(1, spans(view).size)
    }

    @Test
    public fun testEndIconIsInlineWithTheText() {
        val view = labelView(endIcon = true)

        assertNoCompoundDrawables(view)
        assertEquals(1, spans(view).size)
    }

    /** The start icon leads the text and the end icon trails it. */
    @Test
    public fun testBothIconsBracketTheText() {
        val view = labelView(startIcon = true, endIcon = true)
        val text = view.text as Spanned

        assertEquals(2, spans(view).size)
        assertEquals(0, text.getSpanStart(spans(view).first()))
        assertEquals(text.length, text.getSpanEnd(spans(view).last()))
    }

    @Test
    public fun testTextIsUntouchedWithoutIcons() {
        val view = labelView()

        assertNoCompoundDrawables(view)
        assertEquals(0, spans(view).size)
    }

    /**
     * Without a callback an animated drawable's `invalidateSelf` goes nowhere, so the icon
     * renders frame one and stops. A compound drawable gets one from `TextView`; a span
     * drawable only has the one set here.
     */
    @Test
    public fun testIconsCanInvalidateTheLabel() {
        val view = labelView(startIcon = true, endIcon = true)

        spans(view).forEach { span ->
            assertNotNull(span.drawable.callback)

            // And it reaches the view, rather than being dropped by verifyDrawable the way
            // registering the View itself would be.
            val shadow = org.robolectric.Shadows.shadowOf(view)
            shadow.clearWasInvalidated()
            span.drawable.invalidateSelf()
            assertTrue(shadow.wasInvalidated())
        }
    }

    /**
     * The gap is a span in the text run, not padding on the drawable, so the bidi algorithm
     * puts it between icon and words in either direction. In the string the start icon leads
     * its gap and the end icon trails its own — two neutrals beside a directional run reorder
     * together, so that string order is all this has to get right.
     */
    @Test
    public fun testStartIconLeadsItsGap() {
        val text = labelView(startIcon = true).text as Spanned

        assertEquals(0, text.getSpanStart(imageSpans(text).single()))
        assertEquals(1, text.getSpanStart(gapSpans(text).single()))
    }

    @Test
    public fun testEndIconTrailsItsGap() {
        val text = labelView(endIcon = true).text as Spanned

        assertEquals(text.length - 1, text.getSpanStart(imageSpans(text).single()))
        assertEquals(text.length - 2, text.getSpanStart(gapSpans(text).single()))
    }

    /** Whatever the text's own direction is: string order is the same, bidi does the rest. */
    @Test
    public fun testStringOrderIsTheSameForRtlText() {
        val text = labelView(startIcon = true, endIcon = true, text = ARABIC).text as Spanned

        assertEquals(0, text.getSpanStart(imageSpans(text).first()))
        assertEquals(text.length - 1, text.getSpanStart(imageSpans(text).last()))
    }

    /**
     * `icon_start` means the layout's start, not the text's: a leading icon is chrome, so in an
     * RTL layout it belongs on the right even when the words are Latin.
     */
    @Test
    public fun testParagraphFollowsTheLayoutDirection() {
        val view = labelView(startIcon = true)

        // Driven through the real property, not by calling the hook: setTextDirection re-enters
        // onRtlPropertiesChanged with the view's own direction, so a hand-called hook would just
        // be overwritten.
        view.layoutDirection = View.LAYOUT_DIRECTION_RTL
        assertEquals(View.TEXT_DIRECTION_RTL, view.textDirection)

        view.layoutDirection = View.LAYOUT_DIRECTION_LTR
        assertEquals(View.TEXT_DIRECTION_LTR, view.textDirection)
    }

    /** The gap is the authored space, so it survives as a measurable width. */
    @Test
    public fun testGapCarriesTheAuthoredSpace() {
        val text = labelView(startIcon = true).text as Spanned
        val gap = gapSpans(text).single()

        assertTrue(gap.getSize(TextPaint(), text, 1, 2, null) > 0)
    }

    private fun assertNoCompoundDrawables(view: LabelView) {
        view.compoundDrawables.forEach { assertNull(it) }
    }

    private fun spans(view: LabelView): List<ImageSpan> {
        val text = view.text
        return if (text is Spanned) imageSpans(text) else emptyList()
    }

    private fun imageSpans(text: Spanned): List<ImageSpan> =
        text.getSpans(0, text.length, ImageSpan::class.java).sortedBy { text.getSpanStart(it) }

    private fun gapSpans(text: Spanned): List<ReplacementSpan> =
        text.getSpans(0, text.length, ReplacementSpan::class.java)
            .filterNot { it is ImageSpan }
            .sortedBy { text.getSpanStart(it) }

    private fun labelView(
        startIcon: Boolean = false,
        endIcon: Boolean = false,
        text: String = LATIN
    ): LabelView {
        fun icon(key: String) = """
            , "$key": {
                "type": "floating",
                "space": 8,
                "icon": {
                    "type": "icon",
                    "icon": "progress_spinner",
                    "color": { "default": { "hex": "#FFFFFF", "alpha": 1 } },
                    "scale": 1
                }
            }
        """

        val info = LabelInfo(
            JsonValue.parseString(
                """
                {
                    "type": "label",
                    "text": "$text",
                    "text_appearance": {
                        "font_size": 16,
                        "alignment": "center",
                        "color": { "default": { "hex": "#000000", "alpha": 1 } }
                    }
                    ${if (startIcon) icon("icon_start") else ""}
                    ${if (endIcon) icon("icon_end") else ""}
                }
                """
            ).requireMap()
        )

        val model = LabelModel(info, mockEnv, ModelProperties(pagerPageId = null))
        return model.createView(
            RuntimeEnvironment.getApplication(),
            mockk(relaxed = true),
            ItemProperties(size = null)
        ) as LabelView
    }

    private companion object {
        const val LATIN = "See Recommendations"
        const val ARABIC = "شاهد التوصيات"
    }
}
