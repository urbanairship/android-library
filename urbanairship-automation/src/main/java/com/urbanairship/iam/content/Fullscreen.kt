package com.urbanairship.iam.content

import android.graphics.Color
import com.urbanairship.iam.content.Fullscreen.Template
import com.urbanairship.iam.info.InAppMessageButtonInfo
import com.urbanairship.iam.info.InAppMessageButtonLayoutType
import com.urbanairship.iam.info.InAppMessageColor
import com.urbanairship.iam.info.InAppMessageMediaInfo
import com.urbanairship.iam.info.InAppMessageTextInfo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.util.Objects
import org.jetbrains.annotations.VisibleForTesting

/** Display content for a full screen in-app message. */
public class Fullscreen @VisibleForTesting internal constructor(
    /**
     * The optional heading [InAppMessageTextInfo].
     */
    public val heading: InAppMessageTextInfo? = null,
    /**
     * The optional body [InAppMessageTextInfo].
     */
    public val body: InAppMessageTextInfo? = null,
    /**
     * The optional media [InAppMessageMediaInfo].
     */
    public val media: InAppMessageMediaInfo? = null,
    /**
     * The optional footer button [InAppMessageButtonInfo].
     */
    public val footer: InAppMessageButtonInfo? = null,
    /**
     * The list of optional buttons.
     */
    public val buttons: List<InAppMessageButtonInfo> = emptyList(),
    /**
     * The optional button layout.
     */
    public val buttonLayoutType: InAppMessageButtonLayoutType = InAppMessageButtonLayoutType.SEPARATE,
    /**
     * The optional full screen [Template].
     */
    public val template: Template,
    /**
     * The optional banner background color.
     */
    public val backgroundColor: InAppMessageColor = InAppMessageColor(Color.WHITE),
    /**
     * The optional banner dismiss button color.
     */
    public val dismissButtonColor: InAppMessageColor = InAppMessageColor(Color.BLACK),
) : JsonSerializable {
    public enum class Template(internal val json: String) : JsonSerializable {
        /**
         * Template with display order of header, media, body, buttons, footer.
         */
        HEADER_MEDIA_BODY("header_media_body"),

        /**
         * Template with display order of media, header, body, buttons, footer.
         */
        MEDIA_HEADER_BODY("media_header_body"),

        /**
         * Template with display order of header, body, media, buttons, footer.
         */
        HEADER_BODY_MEDIA("header_body_media");

        internal companion object {

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): Template {
                val content = value.requireString()
                return entries.firstOrNull { it.json == content }
                    ?: throw JsonException("Invalid template value $content")
            }
        }

        override fun toJsonValue(): JsonValue = JsonValue.wrap(json)
    }

    public companion object {
        /**
         * Maximum number of buttons.
         */
        public const val MAX_BUTTONS: Int = 5

        private const val BODY_KEY = "body"
        private const val HEADING_KEY = "heading"
        private const val BACKGROUND_COLOR_KEY = "background_color"
        private const val BUTTON_LAYOUT_KEY = "button_layout"
        private const val BUTTONS_KEY = "buttons"
        private const val MEDIA_KEY = "media"
        private const val DISMISS_BUTTON_COLOR_KEY = "dismiss_button_color"
        private const val TEMPLATE_KEY = "template"
        private const val FOOTER_KEY = "footer"

        /**
         * Parses full screen display JSON.
         *
         * @param value The json payload.
         * @return The parsed display content.
         * @throws JsonException If the json was unable to be parsed.
         */
        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): Fullscreen {
            val content = value.requireMap()
            val buttons = content.get(BUTTONS_KEY)?.requireList()?.map(InAppMessageButtonInfo::fromJson) ?: listOf()
            val buttonType = if (buttons.size > 2) {
                InAppMessageButtonLayoutType.STACKED
            } else {
                content.get(BUTTON_LAYOUT_KEY)?.let(InAppMessageButtonLayoutType::fromJson)
                    ?: InAppMessageButtonLayoutType.SEPARATE
            }

            return Fullscreen(
                heading = content.get(HEADING_KEY)?.let(InAppMessageTextInfo::fromJson),
                body = content.get(BODY_KEY)?.let(InAppMessageTextInfo::fromJson),
                media = content.get(MEDIA_KEY)?.let(InAppMessageMediaInfo::fromJson),
                buttons = buttons,
                buttonLayoutType = buttonType,
                footer = content.get(FOOTER_KEY)?.let(InAppMessageButtonInfo::fromJson),
                template = content.get(TEMPLATE_KEY)?.let(Template::fromJson) ?: Template.HEADER_MEDIA_BODY,
                backgroundColor = content.get(BACKGROUND_COLOR_KEY)?.let(InAppMessageColor::fromJson)
                    ?: InAppMessageColor(Color.WHITE),
                dismissButtonColor = content.get(DISMISS_BUTTON_COLOR_KEY)?.let(InAppMessageColor::fromJson)
                    ?: InAppMessageColor(Color.BLACK),
            )
        }
    }

    public fun validate(): Boolean {
        return heading?.validate() == true || body?.validate() == true
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        HEADING_KEY to heading,
        BODY_KEY to body,
        MEDIA_KEY to media,
        BUTTONS_KEY to buttons,
        BUTTON_LAYOUT_KEY to buttonLayoutType,
        FOOTER_KEY to footer,
        TEMPLATE_KEY to template,
        BACKGROUND_COLOR_KEY to backgroundColor,
        DISMISS_BUTTON_COLOR_KEY to dismissButtonColor
    ).toJsonValue()

    override fun toString(): String = toJsonValue().toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Fullscreen

        if (heading != other.heading) return false
        if (body != other.body) return false
        if (media != other.media) return false
        if (footer != other.footer) return false
        if (buttons != other.buttons) return false
        if (buttonLayoutType != other.buttonLayoutType) return false
        if (template != other.template) return false
        if (backgroundColor != other.backgroundColor) return false
        return dismissButtonColor == other.dismissButtonColor
    }

    override fun hashCode(): Int {
        return Objects.hash(heading, body, media, footer,buttons, buttonLayoutType,
            template, backgroundColor, dismissButtonColor)
    }

}
