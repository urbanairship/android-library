/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.app.Activity
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import androidx.test.platform.app.InstrumentationRegistry
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.AnyModel
import com.urbanairship.android.layout.model.PagerModel
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
public class PagerViewKeyTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val listener = slot<PagerModel.Listener>()

    @Before
    public fun setUp() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    @Test
    public fun testRightPagesWhenNothingInThePageLiesThatWay() {
        val button = focusable(Button(activity))
        val (pager, model) = show(row(button))
        button.requestFocus()

        assertTrue(pager.dispatchKeyEvent(down(KeyEvent.KEYCODE_DPAD_RIGHT)))
        verify(exactly = 1) { model.onDirectionalKey(isRight = true, isRtl = false) }
    }

    @Test
    public fun testArrowsMoveBetweenControlsInARowFirst() {
        val left = focusable(Button(activity))
        val right = focusable(Button(activity))
        val (pager, model) = show(row(left, right))
        left.requestFocus()

        assertFalse(pager.dispatchKeyEvent(down(KeyEvent.KEYCODE_DPAD_RIGHT)))
        assertTrue(pager.dispatchKeyEvent(down(KeyEvent.KEYCODE_DPAD_LEFT)))
        verify(exactly = 0) { model.onDirectionalKey(isRight = true, isRtl = any()) }
        verify(exactly = 1) { model.onDirectionalKey(isRight = false, isRtl = false) }
    }

    @Test
    public fun testAHeldKeyTurnsOnePage() {
        val button = focusable(Button(activity))
        val (pager, model) = show(row(button))
        button.requestFocus()

        assertTrue(pager.dispatchKeyEvent(down(KeyEvent.KEYCODE_DPAD_RIGHT)))
        assertTrue(pager.dispatchKeyEvent(down(KeyEvent.KEYCODE_DPAD_RIGHT, repeat = 1)))
        assertTrue(pager.dispatchKeyEvent(down(KeyEvent.KEYCODE_DPAD_RIGHT, repeat = 2)))
        assertTrue(pager.dispatchKeyEvent(up(KeyEvent.KEYCODE_DPAD_RIGHT)))
        verify(exactly = 1) { model.onDirectionalKey(any(), any()) }

        // The release was swallowed, so the next press turns another page.
        assertTrue(pager.dispatchKeyEvent(down(KeyEvent.KEYCODE_DPAD_RIGHT)))
        verify(exactly = 2) { model.onDirectionalKey(any(), any()) }
    }

    @Test
    public fun testAnUnhandledKeyIsNotSwallowed() {
        val button = focusable(Button(activity))
        val (pager, model) = show(row(button))
        every { model.onDirectionalKey(any(), any()) } returns false
        button.requestFocus()

        assertFalse(pager.dispatchKeyEvent(down(KeyEvent.KEYCODE_DPAD_RIGHT)))
        assertFalse(pager.dispatchKeyEvent(up(KeyEvent.KEYCODE_DPAD_RIGHT)))
    }

    @Test
    public fun testATextFieldKeepsTheArrows() {
        val field = focusable(EditText(activity))
        val (pager, model) = show(row(field))
        field.requestFocus()

        pager.dispatchKeyEvent(down(KeyEvent.KEYCODE_DPAD_RIGHT))
        verify(exactly = 0) { model.onDirectionalKey(any(), any()) }
    }

    @Test
    public fun testAPagerWithNothingFocusedPages() {
        val (pager, model) = show(View(activity))

        assertTrue(pager.dispatchKeyEvent(down(KeyEvent.KEYCODE_DPAD_RIGHT)))
        verify(exactly = 1) { model.onDirectionalKey(isRight = true, isRtl = false) }
    }

    @Test
    public fun testAKeyMidTurnIsSwallowed() {
        val button = focusable(Button(activity))
        val (pager, model) = show(row(button))
        button.requestFocus()

        val handledMidTurn = mutableListOf<Boolean>()
        val recycler = pager.children.filterIsInstance<RecyclerView>().single()
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
                if (handledMidTurn.isEmpty()) {
                    handledMidTurn += pager.dispatchKeyEvent(down(KeyEvent.KEYCODE_DPAD_RIGHT))
                }
            }
        })

        listener.captured.scrollTo(1, animate = true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

        assertEquals(listOf(true), handledMidTurn)
        verify(exactly = 0) { model.onDirectionalKey(any(), any()) }
    }

    @Test
    public fun testOtherKeysPassThrough() {
        val button = focusable(Button(activity))
        val (pager, model) = show(row(button))
        button.requestFocus()

        assertFalse(pager.dispatchKeyEvent(down(KeyEvent.KEYCODE_DPAD_UP)))
        verify(exactly = 0) { model.onDirectionalKey(any(), any()) }
    }

    private fun <T : View> focusable(view: T): T = view.apply { isFocusable = true }

    private fun row(vararg views: View): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach { addView(it, LinearLayout.LayoutParams(0, 100, 1f)) }
    }

    private fun show(firstPage: View): Pair<PagerView, PagerModel> {
        val pages = listOf(firstPage, View(activity)).map { page ->
            mockk<AnyModel>(relaxed = true) {
                every { createView(any(), any(), any()) } returns page
            }
        }
        val model: PagerModel = mockk(relaxed = true) {
            every { isSinglePage } returns false
            every { viewInfo } returns mockk(relaxed = true) {
                every { isSwipeDisabled } returns false
                every { backgrounds } returns null
            }
            every { this@mockk.pages } returns pages
            every { videoBackgroundModels } returns emptyMap()
            every { handlesDirectionalKeys } returns true
            every { getPageViewId(any()) } answers { View.generateViewId() }
            every { onDirectionalKey(any(), any()) } returns true
            every { listener = capture(this@PagerViewKeyTest.listener) } just Runs
        }

        val pager = PagerView(activity, model, mockk<ViewEnvironment>(relaxed = true))
        val root = FrameLayout(activity).apply { addView(pager, MATCH_PARENT, MATCH_PARENT) }
        activity.setContentView(root)
        shadowOf(Looper.getMainLooper()).idle()
        return pager to model
    }

    private fun down(keyCode: Int, repeat: Int = 0) = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, repeat)

    private fun up(keyCode: Int) = KeyEvent(KeyEvent.ACTION_UP, keyCode)
}
