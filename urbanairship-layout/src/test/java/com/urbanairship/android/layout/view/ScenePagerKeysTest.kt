/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.app.Activity
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.PagerModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
public class ScenePagerKeysTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val viewEnvironment: ViewEnvironment = mockk(relaxed = true)
    private val pagerModel = pagerModel()
    private val root = FrameLayout(activity)
    private val pager = PagerView(activity, pagerModel, viewEnvironment)
    private val closeButton = focusableButton()

    @Before
    public fun setup() {
        root.addView(pager, MATCH_PARENT, MATCH_PARENT)
        root.addView(closeButton, FrameLayout.LayoutParams(100, 100, Gravity.TOP or Gravity.END))
    }

    @Test
    public fun testRightFromACloseButtonOverThePagerTurnsThePage() {
        show()
        closeButton.requestFocus()

        assertTrue(root.dispatchScenePagerKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
        verify(exactly = 1) { pagerModel.onDirectionalKey(isRight = true, isRtl = false) }
    }

    @Test
    public fun testAHeldKeyTurnsOnePageAndItsReleaseIsSwallowed() {
        show()
        closeButton.requestFocus()

        assertTrue(root.dispatchScenePagerKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)))
        assertTrue(root.dispatchScenePagerKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, repeat = 1)))
        assertTrue(root.dispatchScenePagerKey(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT)))

        verify(exactly = 1) { pagerModel.onDirectionalKey(isRight = false, isRtl = false) }
    }

    @Test
    public fun testWithNothingFocusedTheKeyTurnsThePage() {
        show()

        assertTrue(root.dispatchScenePagerKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
        verify(exactly = 1) { pagerModel.onDirectionalKey(isRight = true, isRtl = false) }
    }

    @Test
    public fun testFocusThatCanMoveThatWayKeepsTheKey() {
        val skipButton = focusableButton()
        root.addView(skipButton, FrameLayout.LayoutParams(100, 100, Gravity.TOP or Gravity.START))
        show()
        skipButton.requestFocus()

        assertFalse(root.dispatchScenePagerKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
        verify(exactly = 0) { pagerModel.onDirectionalKey(any(), any()) }
    }

    @Test
    public fun testATextFieldKeepsTheKey() {
        val field = EditText(activity)
        root.addView(field, FrameLayout.LayoutParams(300, 100, Gravity.BOTTOM))
        show()
        field.requestFocus()

        assertFalse(root.dispatchScenePagerKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
        verify(exactly = 0) { pagerModel.onDirectionalKey(any(), any()) }
    }

    @Test
    public fun testFocusInsideAPagerIsLeftToThatPager() {
        val pageButton = focusableButton()
        pager.addView(pageButton, FrameLayout.LayoutParams(100, 100, Gravity.CENTER))
        show()
        pageButton.requestFocus()

        assertFalse(root.dispatchScenePagerKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
        verify(exactly = 0) { pagerModel.onDirectionalKey(any(), any()) }
    }

    @Test
    public fun testTwoSeparatePagersTakeNoKeys() {
        val otherModel = pagerModel()
        val row = LinearLayout(activity)
        root.removeView(pager)
        row.addView(pager, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
        row.addView(PagerView(activity, otherModel, viewEnvironment), LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
        root.addView(row, 0, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        show()
        closeButton.requestFocus()

        assertFalse(root.dispatchScenePagerKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
        verify(exactly = 0) { pagerModel.onDirectionalKey(any(), any()) }
        verify(exactly = 0) { otherModel.onDirectionalKey(any(), any()) }
    }

    @Test
    public fun testAHiddenPagerIsNotTheScenePager() {
        val hidden = PagerView(activity, pagerModel(), viewEnvironment).apply { visibility = View.GONE }
        root.addView(hidden, 0, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        show()
        closeButton.requestFocus()

        assertTrue(root.dispatchScenePagerKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
        verify(exactly = 1) { pagerModel.onDirectionalKey(isRight = true, isRtl = false) }
    }

    private fun show() {
        activity.setContentView(root)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun focusableButton() = Button(activity).apply {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    private fun pagerModel(): PagerModel = mockk(relaxed = true) {
        every { viewInfo } returns mockk(relaxed = true)
        every { pages } returns emptyList()
        every { onDirectionalKey(any(), any()) } returns true
    }

    private fun key(action: Int, code: Int, repeat: Int = 0) = KeyEvent(0, 0, action, code, repeat)
}
