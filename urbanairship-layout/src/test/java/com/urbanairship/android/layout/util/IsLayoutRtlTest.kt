/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import android.content.pm.ApplicationInfo
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The one place direction is detected, so what it reads from is worth pinning: the view's own
 * resolved layout direction, which is what the platform resolves every relative API from.
 */
@RunWith(RobolectricTestRunner::class)
public class IsLayoutRtlTest {

    @Before
    public fun setUp() {
        // The platform gates all RTL resolution on the host app declaring android:supportsRtl,
        // and a library's own manifest can't. Without this, layoutDirection never leaves LTR.
        RuntimeEnvironment.getApplication().applicationInfo.also {
            it.flags = it.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        }
    }

    private fun view() = View(RuntimeEnvironment.getApplication())

    @Test
    public fun testFollowsTheViewsDirection() {
        val view = view()

        view.layoutDirection = View.LAYOUT_DIRECTION_RTL
        assertTrue(view.isLayoutRtl)

        view.layoutDirection = View.LAYOUT_DIRECTION_LTR
        assertFalse(view.isLayoutRtl)
    }

    /** Inherited, so a child agrees with the layout it sits in without being told. */
    @Test
    public fun testInheritsFromTheParent() {
        val parent = FrameLayout(RuntimeEnvironment.getApplication())
        val child = view()
        parent.addView(child)

        parent.layoutDirection = View.LAYOUT_DIRECTION_RTL
        assertTrue(child.isLayoutRtl)

        parent.layoutDirection = View.LAYOUT_DIRECTION_LTR
        assertFalse(child.isLayoutRtl)
    }

    /**
     * Not the locale. An Airship locale override selects which copy is delivered, not how it is
     * laid out — reading it here gave mirrored content inside an unmirrored layout.
     */
    @Test
    public fun testIgnoresTheLocale() {
        val view = view()
        view.layoutDirection = View.LAYOUT_DIRECTION_LTR

        // No Airship mock: asking this must not need the SDK to be running at all.
        assertFalse(view.isLayoutRtl)
    }
}
