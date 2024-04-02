package com.urbanairship.automation.rewrite.inappmessage.content

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

/**
 * In-App message display content
 */
public sealed class InAppMessageDisplayContent : JsonSerializable {

    public abstract fun validate(): Boolean
    public open fun isEmbedded(): Boolean = false
    internal abstract val displayType: DisplayType

    /**
     * Banner messages
     */
    public data class BannerContent(public val banner: Banner) : InAppMessageDisplayContent() {
        override fun validate(): Boolean = banner.validate()
        override val displayType: DisplayType = DisplayType.BANNER
        override fun toJsonValue(): JsonValue = banner.toJsonValue()
    }

    /**
     * Fullscreen messages
     */
    public data class FullscreenContent(public val fullscreen: Fullscreen) : InAppMessageDisplayContent() {
        override fun validate(): Boolean = fullscreen.validate()
        override val displayType: DisplayType = DisplayType.FULLSCREEN
        override fun toJsonValue(): JsonValue = fullscreen.toJsonValue()
    }

    /**
     * Modal messages
     */
    public data class ModalContent(public val modal: Modal): InAppMessageDisplayContent() {
        override fun validate(): Boolean = modal.validate()
        override val displayType: DisplayType = DisplayType.MODAL
        override fun toJsonValue(): JsonValue = modal.toJsonValue()
    }

    /**
     * Html messages
     */
    public data class HTMLContent(public val html: HTML): InAppMessageDisplayContent() {
        override fun validate(): Boolean = html.validate()
        override val displayType: DisplayType = DisplayType.HTML
        override fun toJsonValue(): JsonValue = html.toJsonValue()
    }

    /**
     * Custom messages
     */
    public data class CustomContent(public val custom: Custom): InAppMessageDisplayContent() {
        override fun validate(): Boolean = true
        override val displayType: DisplayType = DisplayType.CUSTOM
        override fun toJsonValue(): JsonValue = custom.toJsonValue()
    }

    /**
     * Airship layout messages
     */
    public data class AirshipLayoutContent(public val layout: AirshipLayout): InAppMessageDisplayContent() {
        override fun validate(): Boolean = layout.validate()
        override fun isEmbedded(): Boolean = layout.isEmbedded()
        override val displayType: DisplayType = DisplayType.LAYOUT
        override fun toJsonValue(): JsonValue = layout.toJsonValue()
    }

    internal enum class DisplayType(val json: String) : JsonSerializable {
        /**
         * Banner in-app message.
         */
        BANNER("banner"),

        /**
         * Custom in-app message.
         */
        CUSTOM("custom"),

        /**
         * Fullscreen in-app message.
         */
        FULLSCREEN("fullscreen"),

        /**
         * Modal in-app message.
         */
        MODAL("modal"),

        /**
         * HTML in-app message.
         */
        HTML("html"),

        /**
         * An Airship layout type. These should be handled internally and not overridden.
         */
        LAYOUT("layout");

        companion object {

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): DisplayType {
                val content = value.requireString()
                return entries.firstOrNull { it.json == content }
                    ?: throw JsonException("Unsupported display type $content")
            }
        }

        override fun toJsonValue(): JsonValue = JsonValue.wrap(json)
    }

    internal companion object {

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue, type: DisplayType): InAppMessageDisplayContent {
            return when (type) {
                DisplayType.BANNER -> BannerContent(Banner.fromJson(value))
                DisplayType.CUSTOM -> CustomContent(Custom.fromJson(value))
                DisplayType.FULLSCREEN -> FullscreenContent(Fullscreen.fromJson(value))
                DisplayType.MODAL -> ModalContent(Modal.fromJson(value))
                DisplayType.HTML -> HTMLContent(HTML.fromJson(value))
                DisplayType.LAYOUT -> AirshipLayoutContent(AirshipLayout.fromJson(value))
            }
        }
    }
}
