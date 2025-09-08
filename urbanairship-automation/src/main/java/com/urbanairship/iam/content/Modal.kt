/* Copyright Airship and Contributors */

package com.urbanairship.iam.content

import android.graphics.Color
import androidx.annotation.FloatRange
import com.urbanairship.iam.content.Modal.Template
import com.urbanairship.iam.info.InAppMessageButtonInfo
import com.urbanairship.iam.info.InAppMessageButtonLayoutType
import com.urbanairship.iam.info.InAppMessageColor
import com.urbanairship.iam.info.InAppMessageMediaInfo
import com.urbanairship.iam.info.InAppMessageTextInfo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import org.jetbrains.annotations.VisibleForTesting

/** Display content for a modal in-app message. */
public class Modal @VisibleForTesting internal constructor(
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
    public val buttons: List<InAppMessageButtonInfo>,
    /**
     * The optional button layout.
     */
    public val buttonLayoutType: InAppMessageButtonLayoutType = InAppMessageButtonLayoutType.SEPARATE,
    /**
     * The optional banner [Template].
     */
    public val template: Template,
    /**
     * The optional banner background color.
     */
    public val backgroundColor: InAppMessageColor = InAppMessageColor(Color.WHITE),
    /**
     * The optional border radius. Defaults to 0.
     */
    @FloatRange(from = 0.0)
    public val borderRadius: Float = 0f,
    /**
     * The optional banner dismiss button color.
     */
    public val dismissButtonColor: InAppMessageColor = InAppMessageColor(Color.BLACK),
    /**
     * A flag indicating whether the dialog is allowed to be displayed as fullscreen.
     */
    public val allowFullscreenDisplay: Boolean = false
) : JsonSerializable {

    /** Returns a copy of the Modal display content with the provided changes. */
    @JvmOverloads
    public fun copy(
        heading: InAppMessageTextInfo? = this.heading,
        body: InAppMessageTextInfo? = this.body,
        media: InAppMessageMediaInfo? = this.media,
        footer: InAppMessageButtonInfo? = this.footer,
        buttons: List<InAppMessageButtonInfo> = this.buttons,
        buttonLayoutType: InAppMessageButtonLayoutType = this.buttonLayoutType,
        template: Template = this.template,
        backgroundColor: InAppMessageColor = this.backgroundColor,
        @FloatRange(from = 0.0) borderRadius: Float = this.borderRadius,
        dismissButtonColor: InAppMessageColor = this.dismissButtonColor,
        allowFullscreenDisplay: Boolean = this.allowFullscreenDisplay
    ): Modal = Modal(
        heading = heading,
        body = body,
        media = media,
        footer = footer,
        buttons = buttons,
        buttonLayoutType = buttonLayoutType,
        template = template,
        backgroundColor = backgroundColor,
        borderRadius = borderRadius,
        dismissButtonColor = dismissButtonColor,
        allowFullscreenDisplay = allowFullscreenDisplay
    )

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
        public const val MAX_BUTTONS: Int = 2

        private const val BODY_KEY = "body"
        private const val HEADING_KEY = "heading"
        private const val BACKGROUND_COLOR_KEY = "background_color"
        private const val BORDER_RADIUS_KEY = "border_radius"
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
                heading = content[HEADING_KEY]?.let(InAppMessageTextInfo::fromJson),
                body = content[BODY_KEY]?.let(InAppMessageTextInfo::fromJson),
                media = content[MEDIA_KEY]?.let(InAppMessageMediaInfo::fromJson),
                footer = content[FOOTER_KEY]?.let(InAppMessageButtonInfo::fromJson),
                buttons = content[BUTTONS_KEY]?.requireList()?.map(InAppMessageButtonInfo::fromJson)
                    ?: emptyList(),
                buttonLayoutType = content[BUTTON_LAYOUT_KEY]?.let(InAppMessageButtonLayoutType::fromJson)
                    ?: InAppMessageButtonLayoutType.SEPARATE,
                template = content[TEMPLATE_KEY]?.let(Template.Companion::fromJson)
                    ?: Template.HEADER_MEDIA_BODY,
                backgroundColor = content[BACKGROUND_COLOR_KEY]?.let(InAppMessageColor::fromJson)
                    ?: InAppMessageColor(Color.WHITE),
                borderRadius = content.opt(BORDER_RADIUS_KEY).getFloat(0F),
                dismissButtonColor = content[DISMISS_BUTTON_COLOR_KEY]?.let(InAppMessageColor::fromJson)
                    ?: InAppMessageColor(Color.BLACK),
                allowFullscreenDisplay = content.optionalField(ALLOW_FULLSCREEN_DISPLAY_KEY)
                    ?: false
            )
        }
    }

    public fun validate(): Boolean {
        return heading?.validate() == true || body?.validate() == true
    }

    @Throws(JsonException::class)
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
        BORDER_RADIUS_KEY to borderRadius,
        ALLOW_FULLSCREEN_DISPLAY_KEY to allowFullscreenDisplay
    ).toJsonValue()

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
        if (borderRadius != other.borderRadius) return false
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
        result = 31 * result + buttons.hashCode()
        result = 31 * result + buttonLayoutType.hashCode()
        result = 31 * result + template.hashCode()
        result = 31 * result + backgroundColor.hashCode()
        result = 31 * result + dismissButtonColor.hashCode()
        result = 31 * result + borderRadius.hashCode()
        result = 31 * result + allowFullscreenDisplay.hashCode()
        return result
    }

    override fun toString(): String {
        return "Modal(heading=$heading, body=$body, media=$media, footer=$footer, buttons=$buttons, buttonLayoutType=$buttonLayoutType, template=$template, backgroundColor=$backgroundColor, dismissButtonColor=$dismissButtonColor, borderRadius=$borderRadius, allowFullscreenDisplay=$allowFullscreenDisplay)"
    }

}
