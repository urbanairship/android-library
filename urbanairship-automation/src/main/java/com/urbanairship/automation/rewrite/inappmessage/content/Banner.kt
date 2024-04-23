package com.urbanairship.automation.rewrite.inappmessage.content

import android.graphics.Color
import com.urbanairship.automation.rewrite.inappmessage.content.Banner.Placement
import com.urbanairship.automation.rewrite.inappmessage.content.Banner.Template
import com.urbanairship.automation.rewrite.inappmessage.info.InAppMessageButtonInfo
import com.urbanairship.automation.rewrite.inappmessage.info.InAppMessageButtonLayoutType
import com.urbanairship.automation.rewrite.inappmessage.info.InAppMessageColor
import com.urbanairship.automation.rewrite.inappmessage.info.InAppMessageMediaInfo
import com.urbanairship.automation.rewrite.inappmessage.info.InAppMessageTextInfo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import org.jetbrains.annotations.VisibleForTesting

/** Display content for banner in-app message. */
public class Banner @VisibleForTesting internal constructor(
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
     * The list of optional buttons.
     */
    public val buttons: List<InAppMessageButtonInfo> = emptyList(),
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
     * The optional banner dismiss button color.
     */
    public val dismissButtonColor: InAppMessageColor = InAppMessageColor(Color.BLACK),
    /**
     * The optional border radius in dps.
     */
    public val borderRadius: Float = 0f,
    /**
     * The banner display duration. Default to 15 seconds
     */
    public val duration: Long = DEFAULT_DURATION_MS,
    /**
     * The optional banner placement. [Placement]
     */
    public val placement: Placement,
    /**
     * The action names and values to be run when the banner is clicked.
     */
    public val actions: Map<String, JsonValue> = mapOf()
) : JsonSerializable {
    public enum class Template(internal val json: String) : JsonSerializable {
        /**
         * Template to display the optional media on the left.
         */
        MEDIA_LEFT("media_left"),

        /**
         * Template to display the optional media on the right.
         */
        MEDIA_RIGHT("media_right");

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

    public enum class Placement(internal val json: String) : JsonSerializable {
        /**
         * Display the message on top of the screen.
         */
        TOP("top"),

        /**
         * Display the message on bottom of the screen.
         */
        BOTTOM("bottom");

        internal companion object {

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): Placement {
                val content = value.requireString()
                return entries.firstOrNull { it.json == content }
                    ?: throw JsonException("Invalid placement value $content")
            }
        }

        override fun toJsonValue(): JsonValue = JsonValue.wrap(json)
    }

    public companion object {
        /**
         * Default duration in milliseconds.
         */
        public const val DEFAULT_DURATION_MS: Long = 15000L

        /**
         * Maximum number of buttons supported by a banner.
         */
        public const val MAX_BUTTONS: Int = 2

        private const val ACTIONS_KEY = "actions"
        private const val BODY_KEY = "body"
        private const val HEADING_KEY = "heading"
        private const val BACKGROUND_COLOR_KEY = "background_color"
        private const val PLACEMENT_KEY = "placement"
        private const val BORDER_RADIUS_KEY = "border_radius"
        private const val BUTTON_LAYOUT_KEY = "button_layout"
        private const val BUTTONS_KEY = "buttons"
        private const val MEDIA_KEY = "media"
        private const val DISMISS_BUTTON_COLOR_KEY = "dismiss_button_color"
        private const val TEMPLATE_KEY = "template"
        private const val DURATION_KEY = "duration"

        /**
         * Parses banner display JSON.
         *
         * @param value The json payload.
         * @return The parsed banner display content.
         * @throws JsonException If the json was unable to be parsed.
         */
        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): Banner {
            val content = value.requireMap()
            return Banner(
                heading = content.get(HEADING_KEY)?.let(InAppMessageTextInfo::fromJson),
                body = content.get(BODY_KEY)?.let(InAppMessageTextInfo::fromJson),
                media = content.get(MEDIA_KEY)?.let(InAppMessageMediaInfo::fromJson),
                buttons = content.get(BUTTONS_KEY)?.requireList()?.map(InAppMessageButtonInfo::fromJson)
                    ?: emptyList(),
                buttonLayoutType = content.get(BUTTON_LAYOUT_KEY)?.let(InAppMessageButtonLayoutType::fromJson)
                    ?: InAppMessageButtonLayoutType.SEPARATE,
                placement = content.get(PLACEMENT_KEY)?.let(Placement::fromJson) ?: Placement.BOTTOM,
                template = content.get(TEMPLATE_KEY)?.let(Template::fromJson)
                    ?: Template.MEDIA_LEFT,
                duration = content.opt(DURATION_KEY).getLong(DEFAULT_DURATION_MS),
                backgroundColor = content.get(BACKGROUND_COLOR_KEY)?.let(InAppMessageColor::fromJson)
                    ?: InAppMessageColor(Color.WHITE),
                dismissButtonColor = content.get(DISMISS_BUTTON_COLOR_KEY)?.let(InAppMessageColor::fromJson)
                    ?: InAppMessageColor(Color.BLACK),
                borderRadius = content.opt(BORDER_RADIUS_KEY).getFloat(0F),
                actions = content.opt(ACTIONS_KEY).optMap().map
            )
        }
    }

    internal fun validate(): Boolean {
        if (heading?.validate() != true && body?.validate() != true) {
            return false
        }

        if (buttons.size > MAX_BUTTONS) {
            return false
        }
        return true
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        HEADING_KEY to heading,
        BODY_KEY to body,
        MEDIA_KEY to media,
        BUTTONS_KEY to buttons,
        BUTTON_LAYOUT_KEY to buttonLayoutType,
        PLACEMENT_KEY to placement,
        TEMPLATE_KEY to template,
        DURATION_KEY to duration,
        BACKGROUND_COLOR_KEY to backgroundColor,
        DISMISS_BUTTON_COLOR_KEY to dismissButtonColor,
        BORDER_RADIUS_KEY to borderRadius,
        ACTIONS_KEY to actions
    ).toJsonValue()

    override fun toString(): String = toJsonValue().toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Banner

        if (heading != other.heading) return false
        if (body != other.body) return false
        if (media != other.media) return false
        if (buttons != other.buttons) return false
        if (buttonLayoutType != other.buttonLayoutType) return false
        if (template != other.template) return false
        if (backgroundColor != other.backgroundColor) return false
        if (dismissButtonColor != other.dismissButtonColor) return false
        if (borderRadius != other.borderRadius) return false
        if (duration != other.duration) return false
        if (placement != other.placement) return false
        return actions == other.actions
    }

    override fun hashCode(): Int {
        var result = heading?.hashCode() ?: 0
        result = 31 * result + (body?.hashCode() ?: 0)
        result = 31 * result + (media?.hashCode() ?: 0)
        result = 31 * result + buttons.hashCode()
        result = 31 * result + buttonLayoutType.hashCode()
        result = 31 * result + template.hashCode()
        result = 31 * result + backgroundColor.hashCode()
        result = 31 * result + dismissButtonColor.hashCode()
        result = 31 * result + borderRadius.hashCode()
        result = 31 * result + duration.hashCode()
        result = 31 * result + placement.hashCode()
        result = 31 * result + actions.hashCode()
        return result
    }
}
