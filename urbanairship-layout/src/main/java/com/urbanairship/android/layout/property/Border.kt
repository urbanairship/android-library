/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import android.os.Build
import androidx.annotation.Dimension
import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.widget.Clippable
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import kotlin.math.max
import com.google.android.material.shape.CornerFamily
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
        if (radius != null) {
            return FloatArray(8, { _ -> toPxConverter(radius) })
        }

        return cornerRadius?.getRadii(toPxConverter)
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun applyToShape(
        shape: ShapeAppearanceModel.Builder,
        toPxConverter: (Int) -> Int
    ): Boolean {
        radius?.let {
            shape.setAllCorners(CornerFamily.ROUNDED, toPxConverter(it).toFloat())
        }

        cornerRadius?.applyToShape(shape, toPxConverter)

        return radius != null || cornerRadius != null
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun applyToClippable(view: Clippable, toPxConverter: (Int) -> Float) {
        if (radius != null) {
            view.setClipPathBorderRadius(toPxConverter(radius))
        } else if (cornerRadius != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.setClipPathBorderRadius(cornerRadius.getRadii(toPxConverter))
            } else {
                val radius = cornerRadius.maxCornerRadius ?: 0
                view.setClipPathBorderRadius(radius.toFloat())
            }
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

        internal fun applyToShape(
            shape: ShapeAppearanceModel.Builder,
            toPxConverter: (Int) -> Int
        ) {
            topLeft?.let { shape.setTopLeftCornerSize(toPxConverter(it).toFloat()) }
            topRight?.let { shape.setTopRightCornerSize(toPxConverter(it).toFloat()) }
            bottomLeft?.let { shape.setBottomLeftCornerSize(toPxConverter(it).toFloat()) }
            bottomRight?.let { shape.setBottomRightCornerSize(toPxConverter(it).toFloat()) }
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

        internal val maxCornerRadius: Int?
            get() = listOf(topLeft, topRight, bottomLeft, bottomRight).mapNotNull { it }.maxOrNull()
    }

    public companion object {

        @JvmStatic
        @Throws(JsonException::class)
        public fun fromJson(json: JsonMap): Border {
            return Border(
                radius = json.opt("radius").integer,
                strokeWidth = json.opt("stroke_width").integer,
                strokeColor = json.get("stroke_color")?.requireMap()?.let { Color.fromJson(it) },
                cornerRadius = json.get("corner_radius")?.let(CornerRadius::fromJson)
            )
        }
    }
}
