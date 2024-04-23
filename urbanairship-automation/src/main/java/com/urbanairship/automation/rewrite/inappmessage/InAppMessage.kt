package com.urbanairship.automation.rewrite.inappmessage

import com.urbanairship.android.layout.util.UrlInfo
import com.urbanairship.automation.rewrite.inappmessage.content.InAppMessageDisplayContent
import com.urbanairship.automation.rewrite.inappmessage.info.InAppMessageMediaInfo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import java.util.Objects

/**
 * Defines an in-app message.
 */
public class InAppMessage internal constructor(
    public val name: String,
    public val displayContent: InAppMessageDisplayContent,
    internal var source: InAppMessageSource?,
    public val extras: JsonMap? = null,
    public val actions: Map<String, JsonValue>? = null,
    public val isReportingEnabled: Boolean? = null,
    public val displayBehavior: DisplayBehavior? = null,
    public val renderedLocale: Map<String, JsonValue>? = null
) : JsonSerializable {

    /** The in-app message display behavior. */
    public enum class DisplayBehavior(internal val json: String) : JsonSerializable {
        /**
         * The in-app message should be displayed ASAP.
         */
        IMMEDIATE("immediate"),

        /**
         * The in-app message default display behavior. Usually displayed using the default coordinator
         * that allows defining display interval.
         */
        STANDARD("default");

        internal companion object {

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): DisplayBehavior {
                val content = value.requireString()
                return entries.firstOrNull { it.json == content }
                    ?: throw JsonException("Invalid behavior value $content")
            }
        }

        override fun toJsonValue(): JsonValue = JsonValue.wrap(json)
    }

    internal enum class InAppMessageSource(val json: String) : JsonSerializable {
        /**
         * In-app message from the remote-data service.
         */
        REMOTE_DATA("remote-data"),

        /**
         * In-app message created programmatically by the application.
         */
        APP_DEFINED("app-defined"),

        /**
         * In-app message was generated from a push in the legacy in-app message manager.
         */
        LEGACY_PUSH("legacy-push");

        companion object {

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): InAppMessageSource {
                val content = value.requireString()
                return entries.firstOrNull { it.json == content }
                    ?: throw JsonException("Invalid message source value $content")
            }
        }

        override fun toJsonValue(): JsonValue = JsonValue.wrap(json)
    }

    public constructor(name: String, displayContent: InAppMessageDisplayContent,
                       extras: JsonMap? = null, actions: Map<String, JsonValue>? = null,
                       isReportingEnabled: Boolean? = null, displayBehavior: DisplayBehavior? = null)
            : this(
        name = name,
        displayContent = displayContent,
        source = InAppMessageSource.APP_DEFINED,
        extras = extras,
        actions = actions,
        isReportingEnabled = isReportingEnabled,
        displayBehavior = displayBehavior,
        renderedLocale = null)

    internal companion object {
        /**
         * Max message name length.
         */
        private const val MAX_NAME_LENGTH = 1024;

        private const val DISPLAY_TYPE_KEY = "display_type"
        private const val DISPLAY_CONTENT_KEY = "display"
        private const val NAME_KEY = "name"
        private const val EXTRA_KEY = "extra"
        private const val ACTIONS_KEY = "actions"
        private const val SOURCE_KEY = "source"
        private const val DISPLAY_BEHAVIOR_KEY = "display_behavior"
        private const val REPORTING_ENABLED_KEY = "reporting_enabled"
        private const val RENDERED_LOCALE_KEY = "rendered_locale"
        private const val RENDERED_LOCALE_LANGUAGE_KEY = "language"
        private const val RENDERED_LOCALE_COUNTRY_KEY = "country"

        /**
         * Parses a json value.
         *
         * @param value The json value.
         * @return The parsed [InAppMessage].
         * @throws JsonException If the json is invalid.
         */
        @Throws(JsonException::class)
        fun parseJson(value: JsonValue): InAppMessage {
            val content = value.requireMap()

            val type = InAppMessageDisplayContent.DisplayType.fromJson(content.require(DISPLAY_TYPE_KEY))
            val name: String = content.requireField(NAME_KEY)
            if (name.length > MAX_NAME_LENGTH) {
                throw JsonException("Invalid message name. Must be less than or equal to $MAX_NAME_LENGTH characters.")
            }

            val renderLocale = content.get(RENDERED_LOCALE_KEY)?.requireMap()
            if (renderLocale != null) {
                if (!renderLocale.containsKey(RENDERED_LOCALE_LANGUAGE_KEY) && !renderLocale.containsKey(
                        RENDERED_LOCALE_COUNTRY_KEY)) {
                    throw JsonException("Rendered locale must contain one of $RENDERED_LOCALE_LANGUAGE_KEY" +
                            "or $RENDERED_LOCALE_COUNTRY_KEY fields :$renderLocale")
                }

                if (renderLocale.get(RENDERED_LOCALE_LANGUAGE_KEY)?.isString == false) {
                    throw JsonException("Language must be a string: $renderLocale");
                }

                if (renderLocale.get(RENDERED_LOCALE_COUNTRY_KEY)?.isString == false) {
                    throw JsonException("Country must be a string:: $renderLocale");
                }
            }

            return InAppMessage(
                name = name,
                displayContent = InAppMessageDisplayContent.fromJson(content.require(DISPLAY_CONTENT_KEY), type),
                source = content.get(SOURCE_KEY)?.let(InAppMessageSource::fromJson),
                extras = content.optionalField(EXTRA_KEY),
                actions = content.get(ACTIONS_KEY)?.requireMap()?.map,
                displayBehavior = content.get(DISPLAY_BEHAVIOR_KEY)?.let(DisplayBehavior::fromJson),
                isReportingEnabled = content.optionalField(REPORTING_ENABLED_KEY),
                renderedLocale = renderLocale?.map,

            )
        }
    }

    internal fun isEmbedded(): Boolean = displayContent.isEmbedded()

    override fun toJsonValue(): JsonValue = jsonMapOf(
        NAME_KEY to name,
        EXTRA_KEY to extras,
        DISPLAY_CONTENT_KEY to displayContent,
        DISPLAY_TYPE_KEY to displayContent.displayType,
        ACTIONS_KEY to actions,
        SOURCE_KEY to source,
        DISPLAY_BEHAVIOR_KEY to displayBehavior,
        REPORTING_ENABLED_KEY to isReportingEnabled,
        RENDERED_LOCALE_KEY to renderedLocale
    ).toJsonValue()

    override fun toString(): String = toJsonValue().toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InAppMessage

        if (name != other.name) return false
        if (displayContent != other.displayContent) return false
        if (source != other.source) return false
        if (extras != other.extras) return false
        if (actions != other.actions) return false
        if (isReportingEnabled != other.isReportingEnabled) return false
        if (displayBehavior != other.displayBehavior) return false
        return renderedLocale == other.renderedLocale
    }

    override fun hashCode(): Int {
        return Objects.hash(name, displayContent, source, extras, actions, isReportingEnabled,
            displayContent, renderedLocale)
    }
}

internal fun InAppMessage.getUrlInfos(): List<UrlInfo> {

    fun convert(media: InAppMessageMediaInfo?): List<UrlInfo> {
        val content = media ?: return emptyList()
        return when(content.type) {
            InAppMessageMediaInfo.MediaType.YOUTUBE -> listOf(UrlInfo(UrlInfo.UrlType.VIDEO, content.url))
            InAppMessageMediaInfo.MediaType.VIDEO -> listOf(UrlInfo(UrlInfo.UrlType.VIDEO, content.url))
            InAppMessageMediaInfo.MediaType.IMAGE -> listOf(UrlInfo(UrlInfo.UrlType.IMAGE, content.url))
        }
    }

    return when(displayContent) {
        is InAppMessageDisplayContent.AirshipLayoutContent -> UrlInfo.from(displayContent.layout.layoutInfo.view).toList()
        is InAppMessageDisplayContent.BannerContent -> convert(displayContent.banner.media)
        is InAppMessageDisplayContent.CustomContent -> emptyList()
        is InAppMessageDisplayContent.FullscreenContent -> convert(displayContent.fullscreen.media)
        is InAppMessageDisplayContent.HTMLContent -> {
            listOf(UrlInfo(UrlInfo.UrlType.WEB_PAGE, displayContent.html.url, displayContent.html.requiresConnectivity != false))
        }
        is InAppMessageDisplayContent.ModalContent -> convert(displayContent.modal.media)
    }
}
