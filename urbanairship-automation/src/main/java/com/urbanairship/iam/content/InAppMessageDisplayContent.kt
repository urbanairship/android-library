/* Copyright Airship and Contributors */

package com.urbanairship.iam.content

import android.os.Parcel
import android.os.Parcelable
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

/**
 * In-App message display content
 */
public sealed class InAppMessageDisplayContent : JsonSerializable, Parcelable {

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
        @Throws(JsonException::class)
        override fun toJsonValue(): JsonValue = modal.toJsonValue()
    }

    /**
     * Html messages
     */
    public data class HTMLContent(public val html: HTML): InAppMessageDisplayContent() {
        override fun validate(): Boolean = html.validate()
        override val displayType: DisplayType = DisplayType.HTML
        @Throws(JsonException::class)
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

    internal companion object CREATOR : Parcelable.Creator<InAppMessageDisplayContent> {

        private const val PARCEL_CONTENT = "content"
        private const val PARCEL_DISPLAY_TYPE = "display_type"

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

        override fun createFromParcel(parcel: Parcel): InAppMessageDisplayContent? {
            try {
                val content = parcel.readString()?.let(JsonValue::parseString)?.requireMap() ?: return null
                val type = DisplayType.fromJson(content.require(PARCEL_DISPLAY_TYPE))

                return fromJson(content.require(PARCEL_CONTENT), type)
            } catch (ex: Exception) {
                UALog.e(ex) { "Failed to restore message display content" }
                return null
            }
        }

        override fun newArray(size: Int): Array<InAppMessageDisplayContent?> = arrayOfNulls(size)
    }


    override fun writeToParcel(destination: Parcel, flags: Int) {

        try {
            val json = jsonMapOf(
                PARCEL_DISPLAY_TYPE to displayType, PARCEL_CONTENT to toJsonValue()
            )
            destination.writeString(json.toString())
        } catch (e: JsonException) {
            UALog.e(e) { "Failed to write in-app message display content to parcel!" }
        }
    }

    override fun describeContents(): Int = 0
}
