/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.Thomas
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.AnyModel
import com.urbanairship.android.layout.model.VerticalScrollLayoutModel
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A version 1 scene predates handing a scrolled percentage the scroll's own viewport: it was laid
 * out with that percentage falling back to its own content instead, and an empty `100%` at the end
 * of a scroll drew nothing. So the viewport is withheld from it, same as before there was one to
 * offer; a scene stating version 2 gets it.
 */
@RunWith(RobolectricTestRunner::class)
public class VerticalScrollLayoutViewTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    public fun testViewportWithheldAtVersionOne() {
        val view = scrollView(layoutVersion = Thomas.MIN_SUPPORTED_VERSION)

        view.measure(exactly(300), exactly(800))

        assertEquals(0, view.percentBaseHeight)
    }

    @Test
    public fun testViewportOfferedAboveVersionOne() {
        val view = scrollView(layoutVersion = Thomas.MIN_SUPPORTED_VERSION + 1)

        view.measure(exactly(300), exactly(800))

        assertEquals(800, view.percentBaseHeight)
    }

    private fun scrollView(layoutVersion: Int): VerticalScrollLayoutView {
        val childModel: AnyModel = mockk(relaxed = true)
        every { childModel.createView(any(), any(), any()) } returns View(context)

        val model: VerticalScrollLayoutModel = mockk(relaxed = true)
        every { model.view } returns childModel

        val viewEnvironment: ViewEnvironment = mockk(relaxed = true)
        every { viewEnvironment.layoutVersion } returns layoutVersion

        return VerticalScrollLayoutView(context, model, viewEnvironment)
    }

    private fun exactly(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
}
