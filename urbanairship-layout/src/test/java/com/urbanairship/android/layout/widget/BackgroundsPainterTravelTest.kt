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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A parallaxing layer is exactly used up at the end of its host's travel.
 *
 * Its spare room is `factor * range`, so the two ends of the travel have to line up with the two
 * ends of the layer: flush at the box's leading edge before the host has moved, flush at the
 * trailing edge once the host has gone as far as it can. Coverage alone
 * ([BackgroundsPainterRtlTest]) is satisfied by a layer that stops short, which is what a host
 * overstating its range produces -- a strip that can never be scrolled into view, and a
 * `media_fit` crop tighter than the travel needs. A host that overstates it by a share that
 * depends on its own content, as a branching pager's page count does, also moves the same
 * position to a different place depending on that content.
 */
@RunWith(RobolectricTestRunner::class)
public class BackgroundsPainterTravelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val viewEnvironment: ViewEnvironment = mockk(relaxed = true) {
        every { imageCache() } returns null
    }

    @Test
    public fun aLayerRestsFlushAgainstTheBoxBeforeTheHostMoves() {
        for (factor in FACTORS) {
            val painter = painterFor(factor)
            painter.offsetPx = 0f

            assertEquals("factor=$factor", 0f, painter.layerStart(factor), 0.5f)
        }
    }

    @Test
    public fun aLayerEndsFlushWithTheBoxWhenTheHostHasTravelledAsFarAsItCan() {
        for (factor in FACTORS) {
            val painter = painterFor(factor)
            painter.offsetPx = RANGE

            assertEquals(
                "no part of a layer should be unreachable at the end of the travel: factor=$factor",
                BOX.toFloat(), painter.layerEnd(factor), 0.5f
            )
        }
    }

    /**
     * Two hosts with different travel show the same share of their layer at the same share of
     * the way through it -- which is what makes one route through a branching pager land a page
     * where another route lands it.
     */
    @Test
    public fun progressThroughTheTravelDecidesThePositionWhateverTheRange() {
        val factor = 0.5f
        val shortRange = painterFor(factor, range = BOX.toFloat())
        val longRange = painterFor(factor, range = BOX * 4f)

        shortRange.offsetPx = BOX.toFloat()
        longRange.offsetPx = BOX * 4f

        assertEquals(
            "both are fully travelled, so both should show their layer's far end",
            BOX.toFloat(), shortRange.layerEnd(factor), 0.5f
        )
        assertEquals(BOX.toFloat(), longRange.layerEnd(factor), 0.5f)
    }

    /** Where the layer's leading edge sits, exactly as `drawLayer` and `placeInto` place it. */
    private fun BackgroundsPainter.layerStart(factor: Float): Float =
        parallaxTranslation(parallaxOf(factor), BOX, BOX).first -
            parallaxLeadingOversize(parallaxOf(factor), BOX, BOX).first

    private fun BackgroundsPainter.layerEnd(factor: Float): Float =
        layerStart(factor) + BOX + parallaxOversize(parallaxOf(factor), BOX, BOX).first

    private fun painterFor(factor: Float, range: Float = RANGE): BackgroundsPainter =
        BackgroundsPainter(context, viewEnvironment) {}.apply {
            setParallaxBackgrounds(layers(factor), Direction.HORIZONTAL)
            offsetBoundsPx = 0f..range
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
        private const val RANGE = 3000f
        private val FACTORS = floatArrayOf(0.25f, 0.5f, 1f)
    }
}
