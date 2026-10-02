/* Copyright Airship and Contributors */
package com.urbanairship.iam.view

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.urbanairship.iam.content.Banner
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class BannerDismissLayoutTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val layout = BannerDismissLayout(context, null)

    @Test
    public fun topBannerYFractionSpansTheBannerNotTheLayout() {
        addBanner(Banner.Placement.TOP, bannerHeight = 300)
        layOut(width = 100, height = 2400)

        layout.yFraction = -1f
        assertThat(layout.translationY).isEqualTo(-300f)

        layout.yFraction = -0.5f
        assertThat(layout.translationY).isEqualTo(-150f)
        assertThat(layout.yFraction).isEqualTo(-0.5f)
    }

    @Test
    public fun bottomBannerYFractionSpansTheBannerNotTheLayout() {
        addBanner(Banner.Placement.BOTTOM, bannerHeight = 300)
        layOut(width = 100, height = 2400)

        layout.yFraction = 1f
        assertThat(layout.translationY).isEqualTo(300f)

        layout.yFraction = 0.5f
        assertThat(layout.translationY).isEqualTo(150f)
        assertThat(layout.yFraction).isEqualTo(0.5f)
    }

    @Test
    public fun yFractionSetBeforeLayoutAppliesOnPreDraw() {
        addBanner(Banner.Placement.TOP, bannerHeight = 300)

        layout.yFraction = -1f
        assertThat(layout.translationY).isEqualTo(0f)

        layOut(width = 100, height = 2400)
        layout.viewTreeObserver.dispatchOnPreDraw()

        assertThat(layout.translationY).isEqualTo(-300f)
    }

    @Test
    public fun xFractionTranslatesLaidOutViewByItsWidth() {
        layOut(width = 100, height = 200)

        layout.xFraction = 0.5f

        assertThat(layout.translationX).isEqualTo(50f)
        assertThat(layout.xFraction).isEqualTo(0.5f)
    }

    private fun addBanner(placement: Banner.Placement, bannerHeight: Int) {
        layout.placement = placement
        val gravity = if (placement == Banner.Placement.TOP) Gravity.TOP else Gravity.BOTTOM
        layout.addView(View(context), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, bannerHeight, gravity))
    }

    private fun layOut(width: Int, height: Int) {
        layout.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        layout.layout(0, 0, width, height)
    }
}
