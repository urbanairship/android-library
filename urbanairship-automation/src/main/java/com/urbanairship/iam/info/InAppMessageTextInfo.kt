/* Copyright Airship and Contributors */

package com.urbanairship.iam.info

import android.content.Context
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import java.util.Locale
import java.util.Objects

/**
 * Text display info.
 */
public class InAppMessageTextInfo(
    public val text: String,
    public val color: InAppMessageColor? = null,
    public val size: Float? = null,
    public val fontFamilies: List<String>? = null,
    public val alignment: Alignment? = null,
    public val style: List<Style>? = null,
    public val drawableName: String? = null
) : JsonSerializable {
    public enum class Style(internal val json: String) : JsonSerializable {
        /**
         * Bold text style.
         */
        BOLD("bold"),

        /**
         * Italic text style.
         */
        ITALIC("italic"),

        /**
         * Underline text style.
         */
        UNDERLINE("underline");

        internal companion object {

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): Style {
                val jsonString = value.requireString().lowercase(Locale.ROOT)
                return entries.firstOrNull { it.json == jsonString }
                    ?: throw JsonException("Invalid style: $value");
            }
        }

        override fun toJsonValue(): JsonValue = JsonValue.wrap(json)
    }

    public enum class Alignment(internal val json: String) : JsonSerializable {
        /**
         * Left text alignment.
         */
        LEFT("left"),

        /**
         * Center text alignment.
         */
        CENTER("center"),

        /**
         * Right text alignment.
         */
        RIGHT("right");

        internal companion object {

            @Throws(JsonException::class)
            fun fromJson(value: String): Alignment {
                return entries.firstOrNull { it.json == value }
                    ?: throw JsonException("Unsupported alignment value $value")
            }
        }

        override fun toJsonValue(): JsonValue = JsonValue.wrap(json)
    }

    public companion object {
        private const val TEXT_KEY = "text"
        private const val SIZE_KEY = "size"
        private const val COLOR_KEY = "color"
        private const val ALIGNMENT_KEY = "alignment"
        private const val STYLE_KEY = "style"
        private const val FONT_FAMILY_KEY = "font_family"
        private const val ANDROID_DRAWABLE_RES_NAME_KEY = "android_drawable_res_name"

        /**
         * Parses a [InAppMessageTextInfo] from a [JsonValue].
         *
         * @param value The json value.
         * @return The parsed text info.
         * @throws JsonException If the text info was unable to be parsed.
         */
        @Throws(JsonException::class)
        public fun fromJson(source: JsonValue): InAppMessageTextInfo {
            val content = source.optMap()

            if (content.containsKey(STYLE_KEY) && !content.opt(STYLE_KEY).isJsonList) {
                throw JsonException("Style must be an array: " + content.opt(STYLE_KEY));
            }

            if (content.containsKey(FONT_FAMILY_KEY) && !content.opt(FONT_FAMILY_KEY).isJsonList) {
                throw JsonException("Fonts must be an array: " + content.opt(STYLE_KEY));
            }

            return InAppMessageTextInfo(
                text = content.requireField(TEXT_KEY),
                color = content.get(COLOR_KEY)?.let(InAppMessageColor.Companion::fromJson),
                size = content.optionalField(SIZE_KEY),
                alignment = content.optionalField<String>(ALIGNMENT_KEY)?.let(Alignment.Companion::fromJson),
                style = content.get(STYLE_KEY)?.requireList()?.map(Style.Companion::fromJson),
                fontFamilies = content.opt(FONT_FAMILY_KEY).optList().map { it.requireString() },
                drawableName = content.optionalField(ANDROID_DRAWABLE_RES_NAME_KEY)
            )
        }
    }

    override fun toJsonValue(): JsonValue {
        return jsonMapOf(
            TEXT_KEY to text,
            COLOR_KEY to color,
            SIZE_KEY to size,
            ALIGNMENT_KEY to alignment,
            STYLE_KEY to style,
            FONT_FAMILY_KEY to fontFamilies,
            ANDROID_DRAWABLE_RES_NAME_KEY to drawableName
        ).toJsonValue()
    }



    /**
     * Returns the button icon.
     *
     * @param context The application context
     * @return The icon resource ID.
     */
    public fun getDrawable(context: Context): Int {
        val name = drawableName ?: return 0

        return try {
            context.resources.getIdentifier(name, "drawable", context.packageName)
        } catch (_: Exception) {
            UALog.d { "Drawable $name no longer exists." }
            0
        }
    }

    internal fun validate(): Boolean {
        if (text.isEmpty()) {
            UALog.d { "In-app text infos require nonempty text" }
            return false
        }

        return true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InAppMessageTextInfo

        if (text != other.text) return false
        if (color != other.color) return false
        if (size != other.size) return false
        if (fontFamilies != other.fontFamilies) return false
        if (alignment != other.alignment) return false
        if (style != other.style) return false
        return drawableName == other.drawableName
    }

    override fun hashCode(): Int {
        return Objects.hash(text, color, size, fontFamilies, alignment, style, drawableName)
    }

    override fun toString(): String = toJsonValue().toString()
}
