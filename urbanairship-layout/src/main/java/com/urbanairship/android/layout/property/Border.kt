/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import android.os.Build
import androidx.annotation.Dimension
import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.widget.Clippable
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import kotlin.math.max
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.CornerSize
import com.google.android.material.shape.ShapeAppearanceModel

public class Border public constructor(
    @JvmField public val radius: Int?,
    @JvmField public val strokeWidth: Int?,
    @JvmField public val strokeColor: Color?,
    @JvmField public val cornerRadius: CornerRadius? = null
) {
    @get:Dimension(unit = Dimension.DP)
    public val innerRadius: Int?
        get() {
            if (strokeWidth == null || strokeWidth <= 0) {
                return null
            }

            if (radius == null || radius <= strokeWidth) {
                return null
            }

            return radius - strokeWidth
        }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun innerRadii(toPxConverter: (Int) -> Float): FloatArray? {
        val radii = radii(toPxConverter) ?: return null

        if (strokeWidth == null || strokeWidth <= 0) {
            return null
        }

        val convertedWidth = toPxConverter(strokeWidth)
        for (index in radii.indices) {
            radii[index] = max(0f, radii[index] - convertedWidth)
        }

        return radii
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun radii(toPxConverter: (Int) -> Float): FloatArray? {
        return cornerRadius?.getRadii(toPxConverter) ?: radius?.let {
            FloatArray(8) { _ -> toPxConverter(it) }
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun applyToShape(
        shape: ShapeAppearanceModel.Builder,
        toPxConverter: (Int) -> Int
    ): Boolean {
        if (cornerRadius != null) {
            cornerRadius.applyToShape(shape, toPxConverter)
        } else if (radius != null) {
            shape.setAllCorners(CornerFamily.ROUNDED, toPxConverter(radius).toFloat())
        }

        return radius != null || cornerRadius != null
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun applyToClippable(view: Clippable, toPxConverter: (Int) -> Float) {
        if (cornerRadius != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.setClipPathBorderRadius(cornerRadius.getRadii(toPxConverter))
            } else {
                val radius = cornerRadius.maxCornerRadius ?: 0
                view.setClipPathBorderRadius(radius.toFloat())
            }
        } else if (radius != null) {
            view.setClipPathBorderRadius(toPxConverter(radius))
        }
    }

    public class CornerRadius(
        public val topLeft: Int?,
        public val topRight: Int?,
        public val bottomLeft: Int?,
        public val bottomRight: Int?
    ) {

        internal companion object {
            private const val TOP_LEFT = "top_left"
            private const val TOP_RIGHT = "top_right"
            private const val BOTTOM_LEFT = "bottom_left"
            private const val BOTTOM_RIGHT = "bottom_right"

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): CornerRadius {
                val content = value.requireMap()

                return CornerRadius(
                    topLeft = content.optionalField(TOP_LEFT),
                    topRight = content.optionalField(TOP_RIGHT),
                    bottomLeft = content.optionalField(BOTTOM_LEFT),
                    bottomRight = content.optionalField(BOTTOM_RIGHT)
                )
            }
        }

        private enum class Corner {
            TOP_LEFT,
            TOP_RIGHT,
            BOTTOM_LEFT,
            BOTTOM_RIGHT
        }

        private enum class CapSide {
            VERTICAL, HORIZONTAL
        }

        internal fun applyToShape(
            shape: ShapeAppearanceModel.Builder,
            toPxConverter: (Int) -> Int
        ) {

            optimalCornerSize(Corner.TOP_LEFT, toPxConverter)?.let(shape::setTopLeftCornerSize)
            optimalCornerSize(Corner.TOP_RIGHT, toPxConverter)?.let(shape::setTopRightCornerSize)
            optimalCornerSize(Corner.BOTTOM_LEFT, toPxConverter)?.let(shape::setBottomLeftCornerSize)
            optimalCornerSize(Corner.BOTTOM_RIGHT, toPxConverter)?.let(shape::setBottomRightCornerSize)
        }

        internal fun getRadii(toPxConverter: (Int) -> Float): FloatArray {
            return FloatArray(8).apply {

                val setCorner: (radius: Int?, index: Int) -> Unit = function@ { radius, index ->
                    if (radius == null) { return@function }

                    val inPixel = toPxConverter(radius)
                    set(index, inPixel)
                    set(index + 1, inPixel)
                }

                setCorner(topLeft, 0)
                setCorner(topRight, 2)
                setCorner(bottomRight, 4)
                setCorner(bottomLeft, 6)
            }
        }

        private fun optimalCornerSize(
            corner: Corner,
            toPxConverter: (Int) -> Int
        ): CornerSize? {
            val preferred = when(corner) {
                Corner.TOP_LEFT -> topLeft
                Corner.TOP_RIGHT -> topRight
                Corner.BOTTOM_LEFT -> bottomLeft
                Corner.BOTTOM_RIGHT -> bottomRight
            } ?: return null

            return CornerSize { bounds ->
                val side = if(bounds.width() > bounds.height()) {
                    CapSide.HORIZONTAL
                } else {
                    CapSide.VERTICAL
                }

                val counterpart = when(corner) {
                    Corner.TOP_LEFT -> when(side) {
                        CapSide.VERTICAL -> bottomLeft
                        CapSide.HORIZONTAL -> topRight
                    }
                    Corner.TOP_RIGHT -> when(side) {
                        CapSide.VERTICAL -> bottomRight
                        CapSide.HORIZONTAL -> topLeft
                    }
                    Corner.BOTTOM_LEFT -> when(side) {
                        CapSide.VERTICAL -> topLeft
                        CapSide.HORIZONTAL -> bottomRight
                    }
                    Corner.BOTTOM_RIGHT -> when(side) {
                        CapSide.VERTICAL -> topRight
                        CapSide.HORIZONTAL -> bottomLeft
                    }
                } ?: 0

                val preferredInPixel = toPxConverter(preferred).toFloat()
                val counterPartInPixel = toPxConverter(counterpart).toFloat()

                return@CornerSize listOf(
                    max(bounds.width() - counterPartInPixel, 0f),
                    max(bounds.height() - counterPartInPixel, 0f),
                    preferredInPixel)
                .min()
            }
        }

        internal val maxCornerRadius: Int?
            get() {
                return listOfNotNull(topLeft, topRight, bottomLeft, bottomRight).maxOrNull()
            }
    }

    public companion object {

        @JvmStatic
        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): Border {
            val content = json.requireMap()
            return Border(
                radius = content.opt("radius").integer,
                strokeWidth = content.opt("stroke_width").integer,
                strokeColor = content["stroke_color"]?.let { Color.fromJson(it) },
                cornerRadius = content["corner_radius"]?.let(CornerRadius::fromJson)
            )
        }
    }
}
