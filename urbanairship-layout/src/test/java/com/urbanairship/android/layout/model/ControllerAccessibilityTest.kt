/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RadioGroup
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** How a score or radio controller is named for TalkBack. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class ControllerAccessibilityTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

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
    public fun testAnUnnamedControllerFallsBackToRadioGroup() {
        for (type in CONTROLLERS) {
            assertEquals(type, "Radio group", node(type).contentDescription?.toString())
        }
    }

    @Test
    public fun testAControllerReadsItsOwnDescription() {
        for (type in CONTROLLERS) {
            assertEquals(type, "Rate it", node(type, description = "Rate it").contentDescription?.toString())
        }
    }

    @Test
    public fun testALabelledControllerIsNamedByItsLabel() {
        for (type in CONTROLLERS) {
            assertEquals(type, "How was your stay?", node(type, label = "How was your stay?").contentDescription?.toString())
        }
    }

    @Test
    public fun testAControllersOwnDescriptionWinsOverItsLabel() {
        for (type in CONTROLLERS) {
            assertEquals(
                type,
                "Rate it",
                node(type, description = "Rate it", label = "How was your stay?").contentDescription?.toString()
            )
        }
    }

    @Test
    public fun testALinearLayoutChildKeepsTheRadioGroupRole() {
        for (type in CONTROLLERS) {
            assertEquals(type, "Radio group", node(type, child = LINEAR_CHILD).contentDescription?.toString())
        }
    }

    private fun node(
        type: String,
        description: String? = null,
        label: String? = null,
        child: String = GRID_CHILD
    ): AccessibilityNodeInfoCompat {
        val labelItem = label?.let {
            """
            {
              "size": {"width": "100%", "height": "auto"},
              "view": {
                "type": "label", "text": "$it",
                "text_appearance": {"font_size": 14, "color": {"default": {"type": "hex", "hex": "#000000", "alpha": 1}}},
                "labels": {"type": "labels", "view_id": "controller", "view_type": "$type"}
              }
            },
            """
        } ?: ""
        val json = """
            {
              "type": "form_controller",
              "identifier": "form",
              "submit": "submit_event",
              "view": {
                "type": "linear_layout",
                "direction": "vertical",
                "items": [
                  $labelItem
                  {
                    "size": {"width": "100%", "height": "auto"},
                    "view": {
                      "type": "$type",
                      "identifier": "controller",
                      ${description?.let { """"content_description": "$it",""" } ?: ""}
                      "view": $child
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val environment = ModelEnvironment(
            layoutState = LayoutState.EMPTY,
            reporter = mockk(relaxUnitFun = true),
            actionsRunner = mockk(relaxUnitFun = true),
            displayTimer = mockk(relaxed = true),
            modelScope = testScope
        )
        val info = ViewInfo.viewInfoFromJson(JsonValue.parseString(json).requireMap())
        val view = LayoutViewModel().getOrCreateModel(info, environment)
            .createView(ApplicationProvider.getApplicationContext(), viewEnv, null)
        Robolectric.buildActivity(Activity::class.java).setup().get()
            .setContentView(FrameLayout(view.context).apply { addView(view) })
        repeat(4) {
            shadowOf(Looper.getMainLooper()).idle()
            testScope.testScheduler.advanceUntilIdle()
        }

        return descendants(view).map { child ->
            AccessibilityNodeInfoCompat.obtain().also { ViewCompat.onInitializeAccessibilityNodeInfo(child, it) }
        }.single { it.className == RadioGroup::class.java.name }
    }

    private fun descendants(view: View): List<View> =
        listOf(view) + ((view as? ViewGroup)?.let { group ->
            (0 until group.childCount).flatMap { descendants(group.getChildAt(it)) }
        } ?: emptyList())

    private companion object {
        val CONTROLLERS = listOf("score_controller", "radio_input_controller")
        const val GRID_CHILD =
            """{"type": "grid_layout", "identifier": "grid", "config": {"columns": {"type": "fixed", "count": 1}}, "items": []}"""
        const val LINEAR_CHILD = """{"type": "linear_layout", "direction": "vertical", "items": []}"""
    }
}
