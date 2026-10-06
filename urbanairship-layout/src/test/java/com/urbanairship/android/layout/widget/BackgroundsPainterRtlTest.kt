/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.property.Direction
import com.urbanairship.android.layout.property.ParallaxBackground
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Where a parallaxing layer sits, and which way it travels, in each layout direction.
 *
 * The one invariant that matters is coverage: a layer is drawn larger than its host's box so that
 * it still fills it at every point of its own travel. Three separate right-to-left bugs shipped
 * from getting only part of that right -- the layer resting on the wrong side, then travelling
 * the wrong way once the resting side was mirrored, each leaving a strip of whatever sits
 * underneath showing through. So this asserts the invariant itself at both ends of the travel,
 * in both directions and for both signs of `factor`, rather than pinning particular numbers.
 */
@RunWith(RobolectricTestRunner::class)
public class BackgroundsPainterRtlTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val viewEnvironment: ViewEnvironment = mockk(relaxed = true) {
        every { imageCache() } returns null
    }

    @Test
    public fun aLayerCoversItsBoxThroughoutItsTravelInEitherDirection() {
        for (rtl in listOf(false, true)) {
            for (factor in listOf(0.5f, -0.5f)) {
                for (offset in listOf(0f, RANGE)) {
                    val painter = painterFor(factor, rtl)
                    painter.offsetPx = offset

                    val (dx, _) = painter.parallaxTranslation(parallaxOf(factor), BOX, BOX)
                    val (oversizeX, _) = painter.parallaxOversize(parallaxOf(factor), BOX, BOX)
                    val (leadingX, _) = painter.parallaxLeadingOversize(parallaxOf(factor), BOX, BOX)

                    // Exactly how `drawLayer` places the rect, and how `placeInto` places a real
                    // child view -- both take the layer's origin as `dx - leading`.
                    val start = dx - leadingX
                    val end = start + BOX + oversizeX
                    val where = "rtl=$rtl factor=$factor offset=$offset -> [$start, $end]"

                    assertTrue("layer starts after the box's leading edge: $where", start <= 0f)
                    assertTrue("layer ends before the box's trailing edge: $where", end >= BOX)
                }
            }
        }
    }

    /** The slack is on the side the layer travels away from, which RTL puts on the right. */
    @Test
    public fun theSlackSitsOnTheOppositeSideInRightToLeft() {
        val ltr = painterFor(FACTOR, rtl = false).parallaxLeadingOversize(parallaxOf(FACTOR), BOX, BOX).first
        val rtl = painterFor(FACTOR, rtl = true).parallaxLeadingOversize(parallaxOf(FACTOR), BOX, BOX).first
        val total = painterFor(FACTOR, rtl = false).parallaxOversize(parallaxOf(FACTOR), BOX, BOX).first

        assertEquals("left-to-right rests flush at the left, all slack trailing", 0, ltr)
        assertEquals("right-to-left rests flush at the right, all slack leading", total, rtl)
    }

    /** Mirroring where it rests without mirroring where it goes is what opened the seam. */
    @Test
    public fun theLayerTravelsTowardItsOwnSlack() {
        fun travelOf(rtl: Boolean): Float {
            val painter = painterFor(FACTOR, rtl)
            painter.offsetPx = RANGE
            return painter.parallaxTranslation(parallaxOf(FACTOR), BOX, BOX).first
        }

        val ltr = travelOf(rtl = false)
        val rtl = travelOf(rtl = true)

        assertTrue("left-to-right consumes slack on its right, so it moves left: $ltr", ltr < 0f)
        assertTrue("right-to-left consumes slack on its left, so it moves right: $rtl", rtl > 0f)
        assertEquals("and by the same distance either way", -ltr, rtl, 0.5f)
    }

    /** A host that scrolls vertically has no direction to mirror. */
    @Test
    public fun theVerticalAxisIsUntouchedByLayoutDirection() {
        fun verticalTravel(rtl: Boolean): Pair<Float, Float> {
            val painter = BackgroundsPainter(context, viewEnvironment) {}
            painter.setParallaxBackgrounds(layers(FACTOR), Direction.VERTICAL)
            painter.isRtl = rtl
            painter.offsetBoundsPx = 0f..RANGE
            painter.offsetPx = RANGE
            return painter.parallaxTranslation(parallaxOf(FACTOR), BOX, BOX)
        }

        assertEquals(verticalTravel(rtl = false), verticalTravel(rtl = true))
    }

    private fun painterFor(factor: Float, rtl: Boolean): BackgroundsPainter =
        BackgroundsPainter(context, viewEnvironment) {}.apply {
            setParallaxBackgrounds(layers(factor), Direction.HORIZONTAL)
            isRtl = rtl
            offsetBoundsPx = 0f..RANGE
        }

    private fun layers(factor: Float): List<ParallaxBackground> = ParallaxBackground.fromJsonList(
        JsonValue.parseString(
            """
            [
              {
                "type": "color", "color": { "default": { "hex": "#FF0000", "alpha": 1 } },
                "parallax": { "factor": $factor }
              }
            ]
            """.trimIndent()
        ).optList()
    )

    private fun parallaxOf(factor: Float) = layers(factor).first().parallax

    private companion object {
        private const val BOX = 1000
        private const val RANGE = 2000f
        private const val FACTOR = 0.5f
    }
}
