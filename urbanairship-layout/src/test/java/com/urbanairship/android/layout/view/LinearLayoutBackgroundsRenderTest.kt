/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View.MeasureSpec
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.model.LinearLayoutModel
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner

/**
 * Renders a *real* `LinearLayoutView` end to end (built from JSON, not `BackgroundsPainter`
 * called directly) to check whether `dispatchDraw` is actually reached in a real render pass --
 * `BackgroundsPainterBlendModeTest` already proves the painter's own blend math is correct in
 * isolation, so if this fails where that one passes, the bug is in the View wiring, not the paint
 * math.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
public class LinearLayoutBackgroundsRenderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
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
    @Config(sdk = [30])
    public fun dispatchDrawActuallyPaintsTheBlendedBackground() {
        val json = JsonValue.parseString(
            """
            {
              "type": "linear_layout",
              "direction": "vertical",
              "items": [],
              "backgrounds": [
                { "type": "color", "color": { "default": { "hex": "#FF0000", "alpha": 1 } } },
                {
                  "type": "color",
                  "color": { "default": { "hex": "#00FF00", "alpha": 1 } },
                  "blend_mode": "multiply"
                }
              ]
            }
            """.trimIndent()
        ).requireMap()

        val info = ViewInfo.viewInfoFromJson(json)
        val model = LayoutViewModel().getOrCreateModel(info, mockEnv) as LinearLayoutModel
        val view = model.createView(context, viewEnv, null) as LinearLayoutView

        view.measure(
            MeasureSpec.makeMeasureSpec(10, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(10, MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 10, 10)

        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))

        val pixel = bitmap.getPixel(5, 5)
        assertEquals(
            "multiply(green, red) should read as black -- got #%08X".format(pixel),
            String.format("#%08X", Color.BLACK),
            String.format("#%08X", pixel)
        )
    }
}
