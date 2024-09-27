/* Copyright Airship and Contributors */

package com.urbanairship.iam.content

import android.graphics.Color
import com.urbanairship.iam.info.InAppMessageColor
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import org.jetbrains.annotations.VisibleForTesting

/** Display content for an HTML in-app message. */
public class HTML @VisibleForTesting internal constructor(
    /**
     * The url.
     */
    public val url: String,
    /**
     * The desired height when displaying the message as a dialog.
     */
    public val height: Long = 0,
    /**
     * The desired width when displaying the message as a dialog.
     */
    public val width: Long = 0,
    /**
     * The aspect lock when displaying the message as a dialog.
     */
    public val aspectLock: Boolean? = null,
    /**
     * Checks if the message can be displayed or not if connectivity is unavailable.
     */
    public val requiresConnectivity: Boolean? = null,
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
     * A flag indicating whether the dialog is allowed to be displayed as fullscreen.
     */
    public val allowFullscreenDisplay: Boolean
) : JsonSerializable {

    /** Returns a copy of the HTML display content with the provided changes. */
    @JvmOverloads
    public fun copy(
        url: String = this.url,
        height: Long = this.height,
        width: Long = this.width,
        aspectLock: Boolean? = this.aspectLock,
        requiresConnectivity: Boolean? = this.requiresConnectivity,
        backgroundColor: InAppMessageColor = this.backgroundColor,
        dismissButtonColor: InAppMessageColor = this.dismissButtonColor,
        borderRadius: Float = this.borderRadius,
        allowFullscreenDisplay: Boolean = this.allowFullscreenDisplay
    ): HTML = HTML(
        url = url,
        height = height,
        width = width,
        aspectLock = aspectLock,
        requiresConnectivity = requiresConnectivity,
        backgroundColor = backgroundColor,
        dismissButtonColor = dismissButtonColor,
        borderRadius = borderRadius,
        allowFullscreenDisplay = allowFullscreenDisplay
    )

    public companion object {
        private const val URL_KEY = "url"
        private const val WIDTH_KEY = "width"
        private const val HEIGHT_KEY = "height"
        private const val ASPECT_LOCK_KEY = "aspect_lock"
        private const val REQUIRE_CONNECTIVITY = "require_connectivity"
        private const val BACKGROUND_COLOR_KEY = "background_color"
        private const val BORDER_RADIUS_KEY = "border_radius"
        private const val DISMISS_BUTTON_COLOR_KEY = "dismiss_button_color"
        private const val ALLOW_FULLSCREEN_DISPLAY_KEY = "allow_fullscreen_display"

        /**
         * Parses HTML display JSON.
         *
         * @param value The json payload.
         * @return The parsed display content.
         * @throws JsonException If the json was unable to be parsed.
         */
        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): HTML {
            val content = value.requireMap()
            return HTML(
                url = content.requireField(URL_KEY),
                width = content.optionalField(WIDTH_KEY) ?: 0L,
                height = content.optionalField(HEIGHT_KEY) ?: 0L,
                aspectLock = content.optionalField(ASPECT_LOCK_KEY),
                requiresConnectivity = content.optionalField(REQUIRE_CONNECTIVITY),
                backgroundColor = content.get(BACKGROUND_COLOR_KEY)?.let(InAppMessageColor::fromJson)
                    ?: InAppMessageColor(Color.WHITE),
                borderRadius = content.opt(BORDER_RADIUS_KEY).getFloat(0F),
                dismissButtonColor = content.get(DISMISS_BUTTON_COLOR_KEY)?.let(InAppMessageColor::fromJson)
                    ?: InAppMessageColor(Color.BLACK),
                allowFullscreenDisplay = content.optionalField(ALLOW_FULLSCREEN_DISPLAY_KEY) ?: false
            )
        }
    }

    internal fun validate(): Boolean = url.isNotBlank()

    @Throws(JsonException::class)
    override fun toJsonValue(): JsonValue = jsonMapOf(
        URL_KEY to url,
        WIDTH_KEY to width,
        HEIGHT_KEY to height,
        ASPECT_LOCK_KEY to aspectLock,
        REQUIRE_CONNECTIVITY to requiresConnectivity,
        BACKGROUND_COLOR_KEY to backgroundColor,
        BORDER_RADIUS_KEY to borderRadius,
        DISMISS_BUTTON_COLOR_KEY to dismissButtonColor,
        ALLOW_FULLSCREEN_DISPLAY_KEY to allowFullscreenDisplay
    ).toJsonValue()


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HTML

        if (url != other.url) return false
        if (height != other.height) return false
        if (width != other.width) return false
        if (aspectLock != other.aspectLock) return false
        if (requiresConnectivity != other.requiresConnectivity) return false
        if (backgroundColor != other.backgroundColor) return false
        if (dismissButtonColor != other.dismissButtonColor) return false
        if (borderRadius != other.borderRadius) return false
        return allowFullscreenDisplay == other.allowFullscreenDisplay
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + height.hashCode()
        result = 31 * result + width.hashCode()
        result = 31 * result + (aspectLock?.hashCode() ?: 0)
        result = 31 * result + (requiresConnectivity?.hashCode() ?: 0)
        result = 31 * result + backgroundColor.hashCode()
        result = 31 * result + dismissButtonColor.hashCode()
        result = 31 * result + borderRadius.hashCode()
        result = 31 * result + allowFullscreenDisplay.hashCode()
        return result
    }

    override fun toString(): String {
        return "HTML(url='$url', height=$height, width=$width, aspectLock=$aspectLock, requiresConnectivity=$requiresConnectivity, backgroundColor=$backgroundColor, dismissButtonColor=$dismissButtonColor, borderRadius=$borderRadius, allowFullscreenDisplay=$allowFullscreenDisplay)"
    }
}
