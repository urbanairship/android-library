/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.android.layout.util.PercentUtils
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

// Note: If a parent defines `auto` for a dimension, children must have either `auto` or `points` for the same dimension
public open class Size public constructor(
    width: String,
    height: String
) {

    @JvmField
    public val width: Dimension
    @JvmField
    public val height: Dimension

    init {
        this.width = Dimension.Companion.of(width)
        this.height = Dimension.Companion.of(height)
    }

    override fun toString(): String {
        return "Size { width=$width, height=$height }"
    }

    public enum class DimensionType {
        AUTO,
        PERCENT,
        ABSOLUTE
    }

    public sealed class Dimension (
        internal val value: String,
        @JvmField public val type: DimensionType
    ) {

        public abstract fun getFloat(): Float

        public abstract fun getInt(): Int

        public val isAuto: Boolean = type == DimensionType.AUTO
        public val isPercent: Boolean = type == DimensionType.PERCENT
        public val isAbsolute: Boolean = type == DimensionType.ABSOLUTE

        public companion object {

            public fun of(value: String): Dimension {
                return if (value == SIZE_AUTO) {
                    AutoDimension()
                } else if (PercentUtils.isPercent(value)) {
                    PercentDimension(value)
                } else {
                    AbsoluteDimension(value)
                }
            }
        }
    }

    public class AutoDimension internal constructor() : Dimension(SIZE_AUTO, DimensionType.AUTO) {

        override fun getFloat(): Float {
            return -1f
        }

        override fun getInt(): Int {
            return -1
        }

        override fun toString(): String {
            return value
        }
    }

    public class PercentDimension internal constructor(
        value: String
    ) : Dimension(value, DimensionType.PERCENT) {

        override fun getFloat(): Float {
            return PercentUtils.parse(value)
        }

        override fun getInt(): Int {
            return getFloat().toInt()
        }

        override fun toString(): String {
            return (getFloat() * 100).toInt().toString() + "%"
        }
    }

    public class AbsoluteDimension internal constructor(
        value: String
    ) : Dimension(value, DimensionType.ABSOLUTE) {

        override fun getFloat(): Float {
            return value.toFloat()
        }

        override fun getInt(): Int {
            return getFloat().toInt()
        }

        override fun toString(): String {
            return getInt().toString() + "dp"
        }
    }

    public companion object {

        private const val SIZE_AUTO = "auto"
        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"


        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): Size {
            val content = json.requireMap()

            val width = content[KEY_WIDTH]?.coerceString()
            val height = content[KEY_HEIGHT]?.coerceString()

            if (width == null || height == null) {
                throw JsonException("Size requires both width and height!")
            }

            return Size(width, height)
        }
    }
}
