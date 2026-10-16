/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.app.Activity
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.ui.LayoutViewModel
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An `auto` child is offered the stack's cross axis less its own margins, once.
 *
 * Measured at mdpi so a pixel is a point. An `auto` empty view takes all it is offered, so its
 * size is the offer itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(qualifiers = "mdpi")
@RunWith(RobolectricTestRunner::class)
public class StackCrossAxisMarginsTest {

    private val testScope = TestScope(StandardTestDispatcher())

    private val mockEnv: ModelEnvironment = mockk(relaxed = true) {
        every { layoutState } returns LayoutState.EMPTY
        every { layoutEvents } returns emptyFlow()
        every { modelScope } returns testScope
    }
    private val viewEnv: ViewEnvironment = mockk(relaxed = true) {
        every { isIgnoringSafeAreas } returns false
        every { imageCache() } returns null
    }

    @Test
    public fun testAVerticalStackOffersItsWidthLessTheMarginsOnce() {
        val child = child(
            stack(direction = "vertical", size = """{"width": "auto", "height": 40}""",
                margin = """{"start": 16, "end": 16}"""),
            width = 300, height = 100
        )
        assertEquals(268, child.width)
        assertEquals(16, child.left)
    }

    @Test
    public fun testAHorizontalStackOffersItsHeightLessTheMarginsOnce() {
        val child = child(
            stack(direction = "horizontal", size = """{"width": 40, "height": "auto"}""",
                margin = """{"top": 10, "bottom": 10}"""),
            width = 300, height = 100
        )
        assertEquals(80, child.height)
        assertEquals(10, child.top)
    }

    private fun stack(direction: String, size: String, margin: String) = """
        {"type": "linear_layout", "direction": "$direction",
         "items": [{"size": $size, "margin": $margin, "view": {"type": "empty_view"}}]}
    """.trimIndent()

    private fun child(json: String, width: Int, height: Int): View {
        val info = ViewInfo.viewInfoFromJson(JsonValue.parseString(json).requireMap())
        val stack = LayoutViewModel().getOrCreateModel(info, mockEnv)
            .createView(ApplicationProvider.getApplicationContext(), viewEnv, null) as ViewGroup
        Robolectric.buildActivity(Activity::class.java).setup().get()
            .setContentView(FrameLayout(stack.context).apply { addView(stack) })
        stack.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        stack.layout(0, 0, width, height)
        return stack.getChildAt(0)
    }
}
