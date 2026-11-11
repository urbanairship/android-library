/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.Button
import android.widget.LinearLayout
import androidx.test.platform.app.InstrumentationRegistry
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.AnyModel
import com.urbanairship.android.layout.model.PagerModel
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
public class PagerRecyclerViewFocusTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val hostButton = focusableButton()

    @Before
    public fun setup() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    @Test
    public fun testTurningAwayFromAFocusedControlKeepsFocusInThePager() {
        val pageButton = focusableButton()
        val pager = show(listOf(pageButton, View(activity)))
        pageButton.requestFocus()

        turnToPage(pager, 1)

        assertSame(pager, activity.window.decorView.findFocus())
        assertEquals(ViewGroup.FOCUS_AFTER_DESCENDANTS, pager.descendantFocusability)
    }

    @Test
    public fun testAPageWithNothingFocusableDoesNotPullInTheNextPagesControl() {
        val firstButton = focusableButton()
        val pager = show(listOf(firstButton, View(activity), focusableButton()))
        firstButton.requestFocus()

        pager.scrollTo(1, animate = true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

        assertEquals(1, pager.getDisplayedItemPosition())
        assertSame(pager, activity.window.decorView.findFocus())
    }

    @Test
    public fun testTurningToAPageWithAControlFocusesIt() {
        val firstButton = focusableButton()
        val secondButton = focusableButton()
        val pager = show(listOf(firstButton, secondButton))
        firstButton.requestFocus()

        turnToPage(pager, 1)

        assertTrue(secondButton.isFocused)
    }

    @Test
    public fun testASwipeDisabledPagerKeepsFocusThroughAnAnimatedTurn() {
        val firstButton = focusableButton()
        val secondButton = focusableButton()
        val pager = show(listOf(firstButton, secondButton), handlesDirectionalKeys = false)
        firstButton.requestFocus()

        pager.scrollTo(1, animate = true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

        assertTrue(secondButton.isFocused)
    }

    @Test
    public fun testASwipeDisabledPagerKeepsFocusThroughAnInstantTurn() {
        val firstButton = focusableButton()
        val secondButton = focusableButton()
        val pager = show(listOf(firstButton, secondButton), handlesDirectionalKeys = false)
        firstButton.requestFocus()

        turnToPage(pager, 1)

        assertTrue(secondButton.isFocused)
    }

    @Test
    public fun testASwipeDisabledPagerHoldsFocusOnAnEmptyPageWithoutBecomingAStop() {
        val pageButton = focusableButton()
        val pager = show(listOf(pageButton, View(activity)), handlesDirectionalKeys = false)
        pageButton.requestFocus()

        turnToPage(pager, 1)

        assertSame(pager, activity.window.decorView.findFocus())
        assertEquals(ViewGroup.FOCUS_AFTER_DESCENDANTS, pager.descendantFocusability)

        hostButton.requestFocus()
        assertFalse(pager in activity.window.decorView.getFocusables(View.FOCUS_DOWN))
    }

    @Test
    public fun testArrowsLeaveAPagerHoldingFocusWithNothingBeyondItsEdge() {
        val pageButton = focusableButton()
        val pager = show(listOf(pageButton, View(activity)), handlesDirectionalKeys = false)
        pageButton.requestFocus()
        pager.scrollTo(1, animate = true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertTrue(pager.isFocused)
        assertTrue(pager in activity.window.decorView.getFocusables(View.FOCUS_FORWARD))

        // Nothing lies below the pager, so Down goes on in focus order and wraps to the top.
        assertSame(hostButton, pager.focusSearch(View.FOCUS_DOWN))
    }

    @Test
    public fun testAControlOnAnOffScreenPageIsNotFocusable() {
        val firstButton = focusableButton()
        val offScreenButton = focusableButton()
        val pager = show(listOf(firstButton, View(activity), offScreenButton), handlesDirectionalKeys = false)
        firstButton.requestFocus()

        pager.scrollTo(1, animate = true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertTrue(offScreenButton.isAttachedToWindow)
        assertSame(pager, activity.window.decorView.findFocus())

        assertFalse(offScreenButton in activity.window.decorView.getFocusables(View.FOCUS_RIGHT))
        assertNotSame(offScreenButton, pager.focusSearch(View.FOCUS_RIGHT))
        assertFalse(pager.requestFocus(View.FOCUS_DOWN) && offScreenButton.isFocused)
    }

    @Test
    public fun testInitialFocusMissedBeforeTheFirstLayoutMovesIntoThePage() {
        val pageButton = focusableButton()
        show(listOf(pageButton, View(activity)), handlesDirectionalKeys = false) { pager ->
            assertFalse(pager.requestFocus(View.FOCUS_DOWN))
            hostButton.requestFocus()
        }

        assertTrue(pageButton.isFocused)
    }

    @Test
    public fun testAPagerThatTurnsWithTheArrowsIsAFocusStop() {
        val pager = show(listOf(View(activity), focusableButton()))
        hostButton.requestFocus()

        assertTrue(pager in activity.window.decorView.getFocusables(View.FOCUS_DOWN))
    }

    @Test
    public fun testAModalsPagerIsNotAFocusStop() {
        val pager = show(listOf(View(activity), focusableButton()), receivesKeysWithoutFocus = true)
        hostButton.requestFocus()

        assertFalse(pager in activity.window.decorView.getFocusables(View.FOCUS_DOWN))
    }

    @Test
    public fun testAModalPagingWithNothingFocusedFocusesTheFirstControlItReaches() {
        val lastButton = focusableButton()
        val pager = show(
            listOf(View(activity), View(activity), lastButton),
            receivesKeysWithoutFocus = true,
            hostFocusable = false
        )
        assertNull(activity.window.decorView.findFocus())

        turnToPage(pager, 1)
        assertNull(activity.window.decorView.findFocus())

        pager.scrollTo(2, animate = true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertTrue(lastButton.isFocused)
    }

    @Test
    public fun testFocusOutsideThePagerIsLeftAlone() {
        val pager = show(listOf(focusableButton(), View(activity)))
        hostButton.requestFocus()

        turnToPage(pager, 1)

        assertTrue(hostButton.isFocused)
    }

    private fun show(
        pageViews: List<View>,
        handlesDirectionalKeys: Boolean = true,
        receivesKeysWithoutFocus: Boolean = false,
        hostFocusable: Boolean = true,
        beforeLayout: (PagerRecyclerView) -> Unit = {}
    ): PagerRecyclerView {
        hostButton.isFocusable = hostFocusable
        val pages = pageViews.map { page ->
            mockk<AnyModel>(relaxed = true) {
                every { createView(any(), any(), any()) } returns page
            }
        }
        val model: PagerModel = mockk(relaxed = true) {
            every { isSinglePage } returns false
            every { viewInfo } returns mockk(relaxed = true) {
                every { isSwipeDisabled } returns !handlesDirectionalKeys
            }
            every { this@mockk.pages } returns pages
            every { this@mockk.handlesDirectionalKeys } returns handlesDirectionalKeys
            every { getPageViewId(any()) } answers { View.generateViewId() }
        }

        val environment = mockk<ViewEnvironment>(relaxed = true) {
            every { this@mockk.receivesKeysWithoutFocus } returns receivesKeysWithoutFocus
        }
        val pager = PagerRecyclerView(activity, model, environment)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(hostButton, MATCH_PARENT, 100)
            addView(pager, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
        activity.setContentView(root)
        beforeLayout(pager)
        idle()
        return pager
    }

    private fun turnToPage(pager: PagerRecyclerView, position: Int) {
        pager.scrollTo(position, animate = false)
        idle()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun focusableButton() = Button(activity).apply {
        isFocusable = true
    }
}
