/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.text.Spanned
import android.text.style.ImageSpan
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
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * A start icon has to travel with the text, so a label wider than its text — a centred button
 * label — keeps the icon beside the words instead of against the view's edge. An end icon does
 * the opposite: the trailing edge is where it belongs once the label has room to spare.
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
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testStartIconIsInlineWithTheText() {
        val view = labelView(iconKey = "icon_start")

        assertNull(view.compoundDrawables[LEFT])
        assertNotNull(spans(view).singleOrNull())
    }

    @Test
    public fun testEndIconStaysOnTheTrailingEdge() {
        val view = labelView(iconKey = "icon_end")

        assertNotNull(view.compoundDrawables[RIGHT])
        assertEquals(0, spans(view).size)
    }

    /**
     * Without a callback an animated drawable's `invalidateSelf` goes nowhere, so the icon
     * renders frame one and stops. A compound drawable gets one from `TextView`; a span
     * drawable only has the one set here.
     */
    @Test
    public fun testStartIconCanInvalidateTheLabel() {
        val view = labelView(iconKey = "icon_start")
        val icon = spans(view).single().drawable

        assertNotNull(icon.callback)

        // And it reaches the view, rather than being dropped by verifyDrawable the way
        // registering the View itself would be.
        val shadow = org.robolectric.Shadows.shadowOf(view)
        shadow.clearWasInvalidated()
        icon.invalidateSelf()
        assertTrue(shadow.wasInvalidated())
    }

    @Test
    public fun testTextIsUntouchedWithoutIcons() {
        val view = labelView(iconKey = null)

        assertNull(view.compoundDrawables[LEFT])
        assertNull(view.compoundDrawables[RIGHT])
        assertEquals(0, spans(view).size)
    }

    private fun spans(view: LabelView): Array<out ImageSpan> {
        val text = view.text
        if (text !is Spanned) {
            return emptyArray()
        }
        return text.getSpans(0, text.length, ImageSpan::class.java)
    }

    private fun labelView(iconKey: String?): LabelView {
        val icon = iconKey?.let {
            """
            , "$it": {
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
        } ?: ""

        val info = LabelInfo(
            JsonValue.parseString(
                """
                {
                    "type": "label",
                    "text": "See Recommendations",
                    "text_appearance": {
                        "font_size": 16,
                        "alignment": "center",
                        "color": { "default": { "hex": "#000000", "alpha": 1 } }
                    }
                    $icon
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
        const val LEFT = 0
        const val RIGHT = 2
    }
}
