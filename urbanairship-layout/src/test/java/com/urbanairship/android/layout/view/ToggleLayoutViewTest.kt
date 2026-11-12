/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.RadioButton
import androidx.test.core.app.ApplicationProvider
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class ToggleLayoutViewTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun environment(): ModelEnvironment = ModelEnvironment(
        layoutState = LayoutState.EMPTY,
        reporter = mockk(relaxUnitFun = true),
        actionsRunner = mockk(relaxUnitFun = true),
        displayTimer = mockk(relaxed = true),
        modelScope = testScope
    )

    private val viewEnv: ViewEnvironment = mockk(relaxed = true) {
        every { isIgnoringSafeAreas } returns false
        every { imageCache() } returns null
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
    public fun testTappingASelectedRadioKeepsItSelected() {
        val toggle = toggles("radio_input_controller", "radio_input_toggle_layout").first()

        click(toggle)
        assertTrue(toggle.isChecked)

        click(toggle)
        assertTrue(toggle.isChecked)
    }

    @Test
    public fun testTappingASelectedScoreKeepsItSelected() {
        val toggle = toggles("score_controller", "score_toggle_layout").first()

        click(toggle)
        assertTrue(toggle.isChecked)

        click(toggle)
        assertTrue(toggle.isChecked)
    }

    @Test
    public fun testTappingACheckedCheckboxUnchecksIt() {
        val toggle = toggles("checkbox_controller", "checkbox_toggle_layout").first()

        click(toggle)
        assertTrue(toggle.isChecked)

        click(toggle)
        assertFalse(toggle.isChecked)
    }

    @Test
    public fun testAccessibilityClassNames() {
        assertEquals(
            CheckBox::class.java.name,
            toggles("checkbox_controller", "checkbox_toggle_layout").first().accessibilityClassName
        )
        assertEquals(
            RadioButton::class.java.name,
            toggles("radio_input_controller", "radio_input_toggle_layout").first().accessibilityClassName
        )
        assertEquals(
            RadioButton::class.java.name,
            toggles("score_controller", "score_toggle_layout").first().accessibilityClassName
        )
    }

    private fun click(toggle: ToggleLayoutView<*>) {
        toggle.performClick()
        idle()
    }

    private fun idle() {
        repeat(4) {
            shadowOf(Looper.getMainLooper()).idle()
            testScope.testScheduler.advanceUntilIdle()
        }
    }

    /** Builds a form holding one controller of `controllerType` with two `toggleType` rows. */
    private fun toggles(controllerType: String, toggleType: String): List<ToggleLayoutView<*>> {
        val rows = (1..2).joinToString { value ->
            """
            {
              "size": {"width": "100%", "height": 44},
              "view": {
                "type": "$toggleType",
                "identifier": "row_$value",
                "reporting_value": $value,
                "on_toggle_on": {"state_actions": []},
                "on_toggle_off": {"state_actions": []},
                "view": {"type": "container", "items": []}
              }
            }
            """
        }
        val json = """
            {
              "type": "form_controller",
              "identifier": "form",
              "submit": "submit_event",
              "view": {
                "type": "$controllerType",
                "identifier": "controller",
                "view": {"type": "linear_layout", "direction": "vertical", "items": [$rows]}
              }
            }
        """.trimIndent()

        val info = ViewInfo.viewInfoFromJson(JsonValue.parseString(json).requireMap())
        val view = LayoutViewModel().getOrCreateModel(info, environment())
            .createView(ApplicationProvider.getApplicationContext(), viewEnv, null)
        Robolectric.buildActivity(Activity::class.java).setup().get()
            .setContentView(FrameLayout(view.context).apply { addView(view) })
        idle()

        return collect(view)
    }

    private fun collect(view: View): List<ToggleLayoutView<*>> = when (view) {
        is ToggleLayoutView<*> -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { collect(view.getChildAt(it)) }
        else -> emptyList()
    }
}
