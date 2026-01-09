/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.android.layout.util.PercentUtils
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

public class ConstrainedSize public constructor(
    width: String,
    height: String,
    minWidth: String?,
    minHeight: String?,
    maxWidth: String?,
    maxHeight: String?
) : Size(width, height) {

    @JvmField
    public val minWidth: ConstrainedDimension?
    @JvmField
    public val minHeight: ConstrainedDimension?
    @JvmField
    public val maxWidth: ConstrainedDimension?
    @JvmField
    public val maxHeight: ConstrainedDimension?

    init {
        this.minWidth = ConstrainedDimension.Companion.of(minWidth)
        this.minHeight = ConstrainedDimension.Companion.of(minHeight)
        this.maxWidth = ConstrainedDimension.Companion.of(maxWidth)
        this.maxHeight = ConstrainedDimension.Companion.of(maxHeight)
    }

    override fun toString(): String {
        return "ConstrainedSize { " +
                "width=$width, height=$height, " +
                "minWidth=$minWidth, minHeight=$minHeight, " +
                "maxWidth=$maxWidth, maxHeight=$maxHeight }"
    }

    public enum class ConstrainedDimensionType {
        PERCENT, ABSOLUTE
    }

    public abstract class ConstrainedDimension internal constructor(
        protected val value: String,
        @JvmField public val type: ConstrainedDimensionType
    ) {

        public abstract fun getFloat(): Float

        public abstract fun getInt(): Int

        public fun isPercent(): Boolean {
            return type == ConstrainedDimensionType.PERCENT
        }

        public fun isAbsolute(): Boolean {
            return type == ConstrainedDimensionType.ABSOLUTE
        }

        public companion object {

            public fun of(value: String?): ConstrainedDimension? {
                val content = value ?: return null

                return if (PercentUtils.isPercent(content)) {
                    PercentConstrainedDimension(content)
                } else {
                    AbsoluteConstrainedDimension(content)
                }
            }
        }
    }

    public class PercentConstrainedDimension internal constructor(
        value: String
    ) : ConstrainedDimension(value, ConstrainedDimensionType.PERCENT) {

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

    public class AbsoluteConstrainedDimension internal constructor(
        value: String
    ) : ConstrainedDimension(value, ConstrainedDimensionType.ABSOLUTE) {

        override fun getFloat(): Float {
            return value.toFloat()
        }

        override fun getInt(): Int {
            return value.toInt()
        }

        override fun toString(): String {
            return getInt().toString() + "dp"
        }
    }

    public companion object {

        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"
        private const val KEY_MIN_WIDTH = "min_width"
        private const val KEY_MIN_HEIGHT = "min_height"
        private const val KEY_MAX_WIDTH = "max_width"
        private const val KEY_MAX_HEIGHT = "max_height"

        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): ConstrainedSize {
            val content = json.requireMap()

            val width = content[KEY_WIDTH]?.coerceString()
            val height = content[KEY_HEIGHT]?.coerceString()
            if (width == null || height == null) {
                throw JsonException("Size requires both width and height!")
            }

            return ConstrainedSize(
                width = width,
                height = height,
                minWidth = content[KEY_MIN_WIDTH]?.coerceString(),
                minHeight = content[KEY_MIN_HEIGHT]?.coerceString(),
                maxWidth = content[KEY_MAX_WIDTH]?.coerceString(),
                maxHeight = content[KEY_MAX_HEIGHT]?.coerceString()
            )
        }
    }
}
