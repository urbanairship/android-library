package com.urbanairship.automation.rewrite.inappmessage.content

import android.graphics.Color
import com.urbanairship.automation.rewrite.inappmessage.info.InAppMessageButtonInfo
import com.urbanairship.automation.rewrite.inappmessage.info.InAppMessageButtonLayoutType
import com.urbanairship.automation.rewrite.inappmessage.info.InAppMessageColor
import com.urbanairship.automation.rewrite.inappmessage.info.InAppMessageMediaInfo
import com.urbanairship.automation.rewrite.inappmessage.info.InAppMessageTextInfo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.util.ColorUtils
import org.jetbrains.annotations.VisibleForTesting

/**
 * Display content for a [com.urbanairship.automation.rewrite.inappmessage.InAppMessage#TYPE_MODAL] in-app message.
 */
public class Modal @VisibleForTesting internal constructor(
    /**
     * The optional heading [InAppMessageTextInfo].
     */
    public val heading: InAppMessageTextInfo?,
    /**
     * The optional body [InAppMessageTextInfo].
     */
    public val body: InAppMessageTextInfo?,
    /**
     * The optional media [InAppMessageMediaInfo].
     */
    public val media: InAppMessageMediaInfo?,
    /**
     * The optional footer button [InAppMessageButtonInfo].
     */
    public val footer: InAppMessageButtonInfo? = null,
    /**
     * The list of optional buttons.
     */
    public val buttons: List<InAppMessageButtonInfo>?,
    /**
     * The optional button layout.
     */
    public val buttonLayoutType: InAppMessageButtonLayoutType?,
    /**
     * The optional banner template. [Template]
     */
    public val template: Template?,
    /**
     * The optional banner background color.
     */
    public val backgroundColor: InAppMessageColor?,
    /**
     * The optional banner dismiss button color.
     */
    public val dismissButtonColor: InAppMessageColor?,
    /**
     * Returns `true` if the modal dialog is allowed to be displayed as fullscreen, otherwise
     * `false`
     */
    public val allowFullscreenDisplay: Boolean
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
         * Maximum number of button supported by a modal.
         */
        public const val MAX_BUTTONS: Int = 2;

        private const val BODY_KEY = "body"
        private const val HEADING_KEY = "heading"
        private const val BACKGROUND_COLOR_KEY = "background_color"
        private const val BUTTON_LAYOUT_KEY = "button_layout"
        private const val BUTTONS_KEY = "buttons"
        private const val MEDIA_KEY = "media"
        private const val DISMISS_BUTTON_COLOR_KEY = "dismiss_button_color"
        private const val TEMPLATE_KEY = "template"
        private const val FOOTER_KEY = "footer"
        private const val ALLOW_FULLSCREEN_DISPLAY_KEY = "allow_fullscreen_display"

        /**
         * Parses modal display JSON.
         *
         * @param value The json payload.
         * @return The parsed display content.
         * @throws JsonException If the json was unable to be parsed.
         */
        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): Modal {
            val content = value.requireMap()
            return Modal(
                heading = content.get(HEADING_KEY)?.let(InAppMessageTextInfo::fromJson),
                body = content.get(BODY_KEY)?.let(InAppMessageTextInfo::fromJson),
                media = content.get(MEDIA_KEY)?.let(InAppMessageMediaInfo::fromJson),
                footer = content.get(FOOTER_KEY)?.let(InAppMessageButtonInfo::fromJson),
                buttons = content.get(BUTTONS_KEY)?.requireList()?.map(InAppMessageButtonInfo::fromJson),
                buttonLayoutType = content.get(BUTTON_LAYOUT_KEY)?.let(InAppMessageButtonLayoutType::fromJson),
                template = content.get(TEMPLATE_KEY)?.let(Template.Companion::fromJson),
                backgroundColor = content.get(BACKGROUND_COLOR_KEY)?.let(InAppMessageColor::fromJson) ?: InAppMessageColor(Color.WHITE),
                dismissButtonColor = content.get(DISMISS_BUTTON_COLOR_KEY)?.let(InAppMessageColor::fromJson) ?: InAppMessageColor(Color.BLACK),
                allowFullscreenDisplay = content.optionalField(ALLOW_FULLSCREEN_DISPLAY_KEY) ?: false
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
        FOOTER_KEY to footer,
        BUTTONS_KEY to buttons,
        BUTTON_LAYOUT_KEY to buttonLayoutType,
        TEMPLATE_KEY to template,
        BACKGROUND_COLOR_KEY to backgroundColor,
        DISMISS_BUTTON_COLOR_KEY to dismissButtonColor,
        ALLOW_FULLSCREEN_DISPLAY_KEY to allowFullscreenDisplay
    ).toJsonValue()

    override fun toString(): String = toJsonValue().toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Modal

        if (heading != other.heading) return false
        if (body != other.body) return false
        if (media != other.media) return false
        if (footer != other.footer) return false
        if (buttons != other.buttons) return false
        if (buttonLayoutType != other.buttonLayoutType) return false
        if (template != other.template) return false
        if (backgroundColor != other.backgroundColor) return false
        if (dismissButtonColor != other.dismissButtonColor) return false
        return allowFullscreenDisplay == other.allowFullscreenDisplay
    }

    override fun hashCode(): Int {
        var result = heading?.hashCode() ?: 0
        result = 31 * result + (body?.hashCode() ?: 0)
        result = 31 * result + (media?.hashCode() ?: 0)
        result = 31 * result + (footer?.hashCode() ?: 0)
        result = 31 * result + (buttons?.hashCode() ?: 0)
        result = 31 * result + (buttonLayoutType?.hashCode() ?: 0)
        result = 31 * result + (template?.hashCode() ?: 0)
        result = 31 * result + (backgroundColor?.hashCode() ?: 0)
        result = 31 * result + (dismissButtonColor?.hashCode() ?: 0)
        result = 31 * result + allowFullscreenDisplay.hashCode()
        return result
    }

}
