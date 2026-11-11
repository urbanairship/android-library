/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.ui.LayoutViewModel
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** A 300x200 scroll layout is a D-pad stop only when its content runs past that box. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class ScrollLayoutKeyFocusTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val testDispatcher = StandardTestDispatcher()

    private val mockEnv: ModelEnvironment = mockk(relaxed = true) {
        every { layoutState } returns LayoutState.EMPTY
        every { layoutEvents } returns emptyFlow()
        every { modelScope } returns TestScope(testDispatcher)
    }
    private val viewEnv: ViewEnvironment = mockk(relaxed = true) {
        every { isIgnoringSafeAreas } returns false
    }

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testAVerticalScrollWithRoomToScrollIsAFocusStop() {
        assertFocusStop(show("vertical", width = FULL, height = "900"))
    }

    @Test
    public fun testAVerticalScrollThatFitsIsNotAFocusStop() {
        assertNotFocusStop(show("vertical", width = FULL, height = "100"))
    }

    @Test
    public fun testAHorizontalScrollWithRoomToScrollIsAFocusStop() {
        assertFocusStop(show("horizontal", width = "900", height = FULL))
    }

    @Test
    public fun testAHorizontalScrollThatFitsIsNotAFocusStop() {
        assertNotFocusStop(show("horizontal", width = "100", height = FULL))
    }

    private fun assertFocusStop(scroll: View) {
        assertTrue(scroll in scroll.rootView.getFocusables(View.FOCUS_DOWN))
        assertTrue(scroll.requestFocus())
    }

    private fun assertNotFocusStop(scroll: View) {
        assertFalse(scroll in scroll.rootView.getFocusables(View.FOCUS_DOWN))
        assertFalse(scroll.requestFocus())
    }

    private fun show(direction: String, width: String, height: String): View {
        val json = JsonValue.parseString(
            """
            {
              "type": "scroll_layout",
              "direction": "$direction",
              "view": {
                "type": "linear_layout",
                "direction": "$direction",
                "items": [
                  {
                    "size": { "width": $width, "height": $height },
                    "view": { "type": "empty_view" }
                  }
                ]
              }
            }
            """.trimIndent()
        ).requireMap()

        val info = ViewInfo.viewInfoFromJson(json)
        val scroll = LayoutViewModel().getOrCreateModel(info, mockEnv).createView(activity, viewEnv, null)
        val root = FrameLayout(activity).apply { addView(scroll, FrameLayout.LayoutParams(300, 200)) }
        activity.setContentView(root)
        shadowOf(Looper.getMainLooper()).idle()
        return scroll
    }

    private companion object {
        const val FULL = "\"100%\""
    }
}
