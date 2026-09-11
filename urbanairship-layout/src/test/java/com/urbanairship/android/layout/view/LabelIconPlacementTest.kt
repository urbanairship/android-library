/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.graphics.drawable.InsetDrawable
import android.text.Spanned
import android.view.View
import android.text.style.ImageSpan
import com.urbanairship.Airship
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.info.LabelInfo
import com.urbanairship.android.layout.model.ItemProperties
import com.urbanairship.android.layout.model.LabelModel
import com.urbanairship.android.layout.model.ModelProperties
import com.urbanairship.json.JsonValue
import com.urbanairship.locale.LocaleManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.util.Locale
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

    private var airshipLocale: Locale = Locale.US

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // The platform gates every bit of RTL resolution on the host app declaring
        // android:supportsRtl, and a library's own manifest can't. Without this,
        // setTextDirection is inert and layoutDirection never leaves LTR.
        RuntimeEnvironment.getApplication().applicationInfo.also {
            it.flags = it.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        }
        // isLayoutRtl reads the Airship locale, which is the override message content honours.
        val localeManager: LocaleManager = mockk {
            every { locale } answers { airshipLocale }
        }
        mockkObject(Airship)
        every { Airship.isFlyingOrTakingOff } returns true
        every { Airship.localeManager } returns localeManager
    }

    @After
    public fun tearDown() {
        unmockkObject(Airship)
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
     * The gap belongs between the icon and the text, so it sits on whichever side the text is:
     * the start icon leads, the end icon trails, and RTL swaps which physical side that is.
     */
    @Test
    public fun testGapSidesInLtr() {
        assertEquals(0, insetBounds(startIcon = true).left)
        assertTrue(insetBounds(endIcon = true).left > 0)
    }

    @Test
    public fun testGapSidesInRtl() {
        airshipLocale = ARABIC_LOCALE

        assertTrue(insetBounds(startIcon = true).left > 0)
        assertEquals(0, insetBounds(endIcon = true).left)
    }

    /**
     * The locale decides, not the content. Left to the platform's first-strong default the
     * paragraph would follow the characters, so Latin copy on an RTL device would lay out
     * left-to-right while the rest of the layout mirrored — and the icons riding in that text
     * would disagree with their own gap.
     */
    @Test
    public fun testParagraphFollowsTheLocaleNotTheText() {
        assertEquals(View.TEXT_DIRECTION_LTR, labelView(text = ARABIC).textDirection)

        airshipLocale = ARABIC_LOCALE
        assertEquals(View.TEXT_DIRECTION_RTL, labelView(text = LATIN).textDirection)
    }

    /** Which follows for the gap: the content can't pull it out from between icon and text. */
    @Test
    public fun testGapIgnoresTheTextsOwnDirection() {
        assertEquals(0, insetBounds(startIcon = true, text = ARABIC).left)

        airshipLocale = ARABIC_LOCALE
        assertTrue(insetBounds(startIcon = true, text = LATIN).left > 0)
    }

    /** The child rect inside the icon's [InsetDrawable], which reveals which side the gap is on. */
    private fun insetBounds(
        startIcon: Boolean = false,
        endIcon: Boolean = false,
        text: String = LATIN
    ): Rect {
        val view = labelView(startIcon = startIcon, endIcon = endIcon, text = text)
        val icon = spans(view).single().drawable
        return (icon as InsetDrawable).drawable!!.bounds
    }

    private fun assertNoCompoundDrawables(view: LabelView) {
        view.compoundDrawables.forEach { assertNull(it) }
    }

    private fun spans(view: LabelView): List<ImageSpan> {
        val text = view.text
        if (text !is Spanned) {
            return emptyList()
        }
        return text.getSpans(0, text.length, ImageSpan::class.java)
            .sortedBy { text.getSpanStart(it) }
    }

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
        val ARABIC_LOCALE: Locale = Locale.forLanguageTag("ar-EG")
    }
}
