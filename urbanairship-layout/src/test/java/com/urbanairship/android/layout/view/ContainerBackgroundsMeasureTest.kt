/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.model.ContainerLayoutModel
import com.urbanairship.android.layout.ui.LayoutViewModel
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
 * `backgrounds` is load-bearing on never affecting the host's own measured size -- see
 * `BackgroundsPainter`. These confirm a `container` measures identically whether or not a
 * `backgrounds` array is present, including a layer that would balloon to the full box on offer
 * if it were (wrongly) folded into the measure pass instead of only ever drawn in `dispatchDraw`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class ContainerBackgroundsMeasureTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val AT_MOST = android.view.View.MeasureSpec.AT_MOST

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

    @Test
    public fun emptyContainerMeasuresTheSameWithOrWithoutBackgrounds() {
        val without = measure(items = "", backgrounds = null)
        val with = measure(items = "", backgrounds = FULL_BLEED_COLOR)

        assertEquals("width", without.measuredWidth, with.measuredWidth)
        assertEquals("height", without.measuredHeight, with.measuredHeight)
    }

    @Test
    public fun containerWithRealContentMeasuresTheSameWithOrWithoutBackgrounds() {
        val without = measure(items = FIXED_ITEM, backgrounds = null)
        val with = measure(items = FIXED_ITEM, backgrounds = FULL_BLEED_COLOR)

        assertEquals("width", without.measuredWidth, with.measuredWidth)
        assertEquals("height", without.measuredHeight, with.measuredHeight)
    }

    @Test
    public fun aBackgroundNeverBallonsAWrapContentContainerToTheOfferedCeiling() {
        // A 100%/100% layer would fill this 1000x1000 ceiling if it were (wrongly) measured as an
        // ordinary child instead of only ever drawn in `dispatchDraw`.
        val view = measure(items = FIXED_ITEM, backgrounds = FULL_BLEED_COLOR, offered = 1000)

        assertTrueMessage("measuredWidth (${view.measuredWidth}) reached the 1000 ceiling") {
            view.measuredWidth < 1000
        }
        assertTrueMessage("measuredHeight (${view.measuredHeight}) reached the 1000 ceiling") {
            view.measuredHeight < 1000
        }
    }

    private fun assertTrueMessage(message: String, condition: () -> Boolean) {
        org.junit.Assert.assertTrue(message, condition())
    }

    private fun measure(
        items: String,
        backgrounds: String?,
        offered: Int = 300
    ): ContainerLayoutView = container(items, backgrounds).apply {
        measure(
            android.view.View.MeasureSpec.makeMeasureSpec(offered, AT_MOST),
            android.view.View.MeasureSpec.makeMeasureSpec(offered, AT_MOST)
        )
    }

    private fun container(itemsJson: String, backgroundsJson: String?): ContainerLayoutView {
        val backgroundsField = backgroundsJson?.let { ""","backgrounds":[$it]""" } ?: ""
        val json = JsonValue.parseString(
            """{"type":"container","items":[$itemsJson]$backgroundsField}"""
        ).requireMap()
        val info = ViewInfo.viewInfoFromJson(json)
        val model = LayoutViewModel().getOrCreateModel(info, mockEnv) as ContainerLayoutModel
        return model.createView(context, viewEnv, null) as ContainerLayoutView
    }

    private companion object {
        const val FULL_BLEED_COLOR = """
            {
              "type": "color",
              "color": { "default": { "hex": "#000000", "alpha": 1 } }
            }
        """

        const val FIXED_ITEM = """
            {
              "position": {"horizontal": "center", "vertical": "center"},
              "size": {"width": 100, "height": 50},
              "view": {"type": "empty_view"}
            }
        """
    }
}
