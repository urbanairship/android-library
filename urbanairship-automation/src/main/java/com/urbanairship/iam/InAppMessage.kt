/* Copyright Airship and Contributors */
package com.urbanairship.iam

import com.urbanairship.android.layout.util.UrlInfo
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.iam.info.InAppMessageMediaInfo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import java.util.Objects

/**
 * Defines an in-app message.
 */
public class InAppMessage internal constructor(
    public val name: String,
    public val displayContent: InAppMessageDisplayContent,
    internal var source: Source?,
    public val extras: JsonMap? = null,
    public val actions: JsonMap? = null,
    public val isReportingEnabled: Boolean? = null,
    public val displayBehavior: DisplayBehavior? = null,
    public val renderedLocale: JsonValue? = null
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

    internal enum class Source(val json: String) : JsonSerializable {
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
            fun fromJson(value: JsonValue): Source {
                val content = value.requireString()
                return entries.firstOrNull { it.json == content }
                    ?: throw JsonException("Invalid message source value $content")
            }
        }

        override fun toJsonValue(): JsonValue = JsonValue.wrap(json)
    }

    public constructor(
        name: String,
        displayContent: InAppMessageDisplayContent,
        extras: JsonMap? = null,
        actions: JsonMap? = null,
        isReportingEnabled: Boolean? = null,
        displayBehavior: DisplayBehavior? = null
    ) : this(
        name = name,
        displayContent = displayContent,
        source = Source.APP_DEFINED,
        extras = extras,
        actions = actions,
        isReportingEnabled = isReportingEnabled,
        displayBehavior = displayBehavior,
        renderedLocale = null
    )

    /**
     * Creates a new `InAppMessage.Builder` with the values from this message.
     */
    public fun newBuilder(): Builder = Builder(this)

    /**
     * InAppMessage builder.
     */
    public class Builder internal constructor(
        message: InAppMessage
    ) {
        private var name: String = message.name
        private var displayContent: InAppMessageDisplayContent = message.displayContent
        private var source: Source? = message.source
        private var extras: JsonMap? = message.extras
        private var actions: JsonMap? = message.actions
        private var isReportingEnabled: Boolean? = message.isReportingEnabled
        private var displayBehavior: DisplayBehavior? = message.displayBehavior
        private var renderedLocale: JsonValue? = message.renderedLocale

        /**
         * Sets the in-app message name.
         *
         * @param name The in-app message name.
         * @return The builder.
         */
        public fun setName(name: String): Builder = apply {
            this.name = name
        }

        /**
         * Sets the in-app message display content.
         *
         * @param displayContent The in-app message display content.
         * @return The builder.
         */
        public fun setDisplayContent(displayContent: InAppMessageDisplayContent): Builder = apply {
            this.displayContent = displayContent
        }

        /**
         * Sets the in-app message extras.
         *
         * @param extras The in-app message extras.
         * @return The builder.
         */
        public fun setExtras(extras: JsonMap?): Builder = apply {
            this.extras = extras
        }

        /**
         * Sets the in-app message actions.
         *
         * @param actions The in-app message actions.
         * @return The builder.
         */
        public fun setActions(actions: JsonMap?): Builder = apply {
            this.actions = actions
        }

        /**
         * Sets if the in-app message reporting is enabled.
         *
         * @param isReportingEnabled `true` to enable reporting, `false` to disable reporting.
         * @return The builder.
         */
        public fun setReportingEnabled(isReportingEnabled: Boolean): Builder = apply {
            this.isReportingEnabled = isReportingEnabled
        }

        /**
         * Sets the display behavior.
         *
         * @param displayBehavior The display behavior.
         * @return The builder.
         */
        public fun setDisplayBehavior(displayBehavior: DisplayBehavior?): Builder = apply {
            this.displayBehavior = displayBehavior
        }

        /**
         * Builds the in-app message.
         *
         * @return The in-app message.
         */
        public fun build(): InAppMessage = InAppMessage(
            name = name,
            displayContent = displayContent,
            source = source,
            extras = extras,
            actions = actions,
            isReportingEnabled = isReportingEnabled,
            displayBehavior = displayBehavior,
            renderedLocale = renderedLocale
        )
    }

    internal companion object {

        private const val DISPLAY_TYPE_KEY = "display_type"
        private const val DISPLAY_CONTENT_KEY = "display"
        private const val NAME_KEY = "name"
        private const val EXTRA_KEY = "extra"
        private const val ACTIONS_KEY = "actions"
        private const val SOURCE_KEY = "source"
        private const val DISPLAY_BEHAVIOR_KEY = "display_behavior"
        private const val REPORTING_ENABLED_KEY = "reporting_enabled"
        private const val RENDERED_LOCALE_KEY = "rendered_locale"

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

            val type = InAppMessageDisplayContent.DisplayType.fromJson(content.require(
                DISPLAY_TYPE_KEY
            ))

            val name: String = content.optionalField<String>(NAME_KEY) ?: ""

            val renderLocale = content[RENDERED_LOCALE_KEY]

            return InAppMessage(
                name = name,
                displayContent = InAppMessageDisplayContent.fromJson(content.require(
                    DISPLAY_CONTENT_KEY
                ), type),
                source = content[SOURCE_KEY]?.let(Source::fromJson),
                extras = content.optionalField(EXTRA_KEY),
                actions = content.optionalField(ACTIONS_KEY),
                displayBehavior = content[DISPLAY_BEHAVIOR_KEY]?.let(DisplayBehavior::fromJson),
                isReportingEnabled = content.optionalField(REPORTING_ENABLED_KEY),
                renderedLocale = renderLocale,
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
            InAppMessageMediaInfo.MediaType.VIMEO -> listOf(UrlInfo(UrlInfo.UrlType.VIDEO, content.url))
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
