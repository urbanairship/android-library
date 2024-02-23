package com.urbanairship.android.layout.gestures

import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.view.MotionEvent
import androidx.core.graphics.toRegion
import com.urbanairship.UALog
import com.urbanairship.android.layout.property.GestureDirection
import com.urbanairship.android.layout.property.GestureLocation
import com.urbanairship.android.layout.property.GestureLocation.BOTTOM
import com.urbanairship.android.layout.property.GestureLocation.END
import com.urbanairship.android.layout.property.GestureLocation.LEFT
import com.urbanairship.android.layout.property.GestureLocation.RIGHT
import com.urbanairship.android.layout.property.GestureLocation.START
import com.urbanairship.android.layout.property.GestureLocation.TOP
import com.urbanairship.android.layout.view.PagerView
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Helper for determining [GestureLocation] and [GestureDirection] for [PagerView] tap/swipe events.
 */
internal class PagerGestureMapper(
    private var rect: RectF,
    private var isRtl: Boolean
) {
    private var topRegion = TopRegion(rect)
    private var bottomRegion = BottomRegion(rect)
    private var leftRegion = LeftRegion(rect)
    private var rightRegion = RightRegion(rect)

    fun onLayoutChanged(rect: RectF, isRtl: Boolean) {
        if (this.rect == rect && isRtl == this.isRtl) return

        this.rect = rect
        this.isRtl = isRtl

        topRegion = TopRegion(rect)
        bottomRegion = BottomRegion(rect)
        leftRegion = LeftRegion(rect)
        rightRegion = RightRegion(rect)
    }

    fun mapTap(x: Float, y: Float): List<GestureLocation>? = when {
        topRegion.contains(x.toInt(), y.toInt()) -> listOf(TOP)
        bottomRegion.contains(x.toInt(), y.toInt()) -> listOf(BOTTOM)
        leftRegion.contains(x.toInt(), y.toInt()) -> listOf(LEFT, if (isRtl) END else START)
        rightRegion.contains(x.toInt(), y.toInt()) -> listOf(RIGHT, if (isRtl) START else END)
        else -> null
    }

    fun mapSwipe(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): GestureDirection? {
        // Ignore null start events. A null event indicates an incomplete event stream or error state.
        if (e1 == null) return null

        UALog.w("PagerGestureMapper - mapSwipe: $e1, $e2, $velocityX, $velocityY")

        // Ignore multi-pointer gestures.
        if (e1.pointerCount > 1 || e2.pointerCount > 1) return null

        val (x1, y1) = e1.x to e1.y
        val (x2, y2) = e2.x to e2.y

        // Ignore swipes that are too short.
        if (getSwipeDistance(x1, y1, x2, y2) < SWIPE_MIN_DISTANCE) return null

        // TODO: check for min velocity? can we even get this callback if too slow?

        // Calculate the swipe angle and determine if the swipe was UP or DOWN.
        return when (getSwipeAngle(x1, y1, x2, y2)) {
            in UP_RANGE -> GestureDirection.UP
            in DOWN_RANGE -> GestureDirection.DOWN
            else -> null
        }
    }

    /** Calculates the angle between ([x1], [y1]) and ([x2], [y2]), in degrees. */
    private fun getSwipeAngle(x1: Float, y1: Float, x2: Float, y2: Float): Double {
        val rad = atan2(y1 - y2, x2 - x1) + Math.PI
        return (rad * 180 / Math.PI + 180) % 360
    }

    /** Calculates the distance between ([x1], [y1]) and ([x2], [y2]). */
    private fun getSwipeDistance(x1: Float, y1: Float, x2: Float, y2: Float): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt((dx.pow(2) + dy.pow(2)).toDouble())
    }

    private companion object {
        // TODO: take screen density into account? could use a percentage of the view w/h?
        private const val SWIPE_MIN_DISTANCE = 120
        private const val SWIPE_MIN_VELOCITY = 200

        private const val UP_ANGLE = 90.0
        private const val DOWN_ANGLE = 270.0

        /** Max allowed variance in the swipe angle (on each side of the ideal up/down angle). */
        private const val ANGLE_SLOP = 15.0
        /** Up angle with slop. */
        private val UP_RANGE = (UP_ANGLE - ANGLE_SLOP)..(UP_ANGLE + ANGLE_SLOP)
        /** Down angle with slop. */
        private val DOWN_RANGE = (DOWN_ANGLE - ANGLE_SLOP)..(DOWN_ANGLE + ANGLE_SLOP)
    }
}

private abstract class TrapezoidalRegion(rect: RectF) : Region() {
    protected val width = rect.width() * REGION_FRACTION
    protected val height = rect.height() * REGION_FRACTION

    protected fun Path.toRegion(): Region =
        RectF().apply {
            computeBounds(this, true)
        }.toRegion()

    companion object {
        /** The fraction of the view width/height that will be used as the region height/width. */
        private const val REGION_FRACTION = .3f
    }
}

private class TopRegion(rect: RectF) : TrapezoidalRegion(rect) {
    init {
        val path = Path().apply {
            moveTo(rect.left, rect.top)
            lineTo(rect.right, rect.top)
            lineTo(rect.right - width, rect.top + height)
            lineTo(rect.left + width, rect.top + height)
            close()
        }
        setPath(path, path.toRegion())
    }
}

private class BottomRegion(rect: RectF) : TrapezoidalRegion(rect) {
    init {
        val path = Path().apply {
            moveTo(rect.left, rect.bottom)
            lineTo(rect.right, rect.bottom)
            lineTo(rect.right - width, rect.bottom - height)
            lineTo(rect.left + width, rect.bottom - height)
            close()
        }
        setPath(path, path.toRegion())
    }
}

private class RightRegion(rect: RectF) : TrapezoidalRegion(rect) {
    init {
        val path = Path().apply {
            moveTo(rect.right, rect.top)
            lineTo(rect.right, rect.bottom)
            lineTo(rect.right - width, rect.bottom - height)
            lineTo(rect.right - width, rect.top + height)
            close()
        }
        setPath(path, path.toRegion())
    }
}

private class LeftRegion(rect: RectF) : TrapezoidalRegion(rect) {
    init {
        val path = Path().apply {
            moveTo(rect.left, rect.top)
            lineTo(rect.left, rect.bottom)
            lineTo(rect.left + width, rect.bottom - height)
            lineTo(rect.left + width, rect.top + height)
            close()
        }
        setPath(path, path.toRegion())
    }
}
