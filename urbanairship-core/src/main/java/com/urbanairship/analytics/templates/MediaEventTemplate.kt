/* Copyright Airship and Contributors */

package com.urbanairship.analytics.templates

import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

public sealed class MediaEventTemplate {

    public sealed class Type(internal val eventName: String) {
        public data object Browsed: Type("browsed_content")
        public data object Consumed: Type("consumed_content")
        public data object Starred: Type("starred_content")

        public class Shared(
            internal val source: String? = null,
            internal val medium: String? = null
        ) : Type("shared_content")
    }

    public class Properties (
        /** The event's ID. */
        public val id: String? = null,

        /** The event's category. */
        public val category: String? = null,

        /** The event's type. */
        public val type: String? = null,

        /** The event's description. */
        public val eventDescription: String? = null,

        /** The event's author. */
        public val author: String? = null,

        /** The event's published date. */
        public val publishedDate: String? = null,

        /** If the event is a feature. */
        public val isFeature: Boolean? = null,

        /** If the value is a lifetime value or not. */
        public val isLTV: Boolean = false,

        internal val source: String? = null,
        internal val medium: String? = null
    ) : JsonSerializable {

        public companion object {
            private const val IS_LTV = "ltv"
            private const val IS_FEATURE = "feature"
            private const val ID = "id"
            private const val CATEGORY = "category"
            private const val TYPE = "type"
            private const val SOURCE = "source"
            private const val MEDIUM = "medium"
            private const val EVENT_DESCRIPTION = "description"
            private const val AUTHOR = "author"
            private const val PUBLISHED_DATE = "published_date"

            @JvmStatic
            public fun newBuilder(): Builder = Builder()
        }

        public class Builder internal constructor(
            private var id: String? = null,
            private var category: String? = null,
            private var type: String? = null,
            private var eventDescription: String? = null,
            private var author: String? = null,
            private var publishedDate: String? = null,
            private var isFeature: Boolean? = null,
            private var isLTV: Boolean = false,
            private var source: String? = null,
            private var medium: String? = null
        ) {

            internal constructor(properties: Properties): this(
                id = properties.id,
                category = properties.category,
                type = properties.type,
                eventDescription = properties.eventDescription,
                author = properties.author,
                publishedDate = properties.publishedDate,
                isFeature = properties.isFeature,
                isLTV = properties.isLTV,
                source = properties.source,
                medium = properties.medium,
            )

            /**
             * Set the ID.
             * If the ID exceeds 255 characters it will cause the event to be invalid.
             *
             * @param id The ID as a string.
             * @return A [MediaEventTemplate.Properties.Builder].
             */
            public fun setId(id: String?): Builder {
                return this.also { it.id = id }
            }

            /**
             * Set the category.
             * <p>
             * If the category exceeds 255 characters it will cause the event to be invalid.
             *
             * @param category The category as a string.
             * @return A [MediaEventTemplate.Properties.Builder].
             */
            public fun setCategory(category: String?): Builder {
                return this.also { it.category = category }
            }

            /**
             * Set the type.
             * <p>
             * If the type exceeds 255 characters it will cause the event to be invalid.
             *
             * @param type The type as a string.
             * @return A [MediaEventTemplate.Properties.Builder].
             */
            public fun setType(type: String?): Builder {
                return this.also { it.type = type }
            }

            /**
             * Set the description.
             * <p>
             * If the description exceeds 255 characters it will cause the event to be invalid.
             *
             * @param description The description as a string.
             * @return A [MediaEventTemplate.Properties.Builder].
             */
            public fun setDescription(description: String?): Builder {
                return this.also { it.eventDescription = description }
            }

            /**
             * Set the feature.
             *
             * @param feature The feature as a boolean.
             * @return A [MediaEventTemplate.Properties.Builder].
             */
            public fun setFeature(feature: Boolean): Builder {
                return this.also { it.isFeature = feature }
            }

            /**
             * Set the author.
             * <p>
             * If the author exceeds 255 characters it will cause the event to be invalid.
             *
             * @param author The author as a string.
             * @return A [MediaEventTemplate.Properties.Builder].
             */
            public fun setAuthor(author: String?): Builder {
                return this.also { it.author = author }
            }

            /**
             * Set the publishedDate.
             * <p>
             * If the publishedDate exceeds 255 characters it will cause the event to be invalid.
             *
             * @param date The publishedDate as a string.
             * @return A [MediaEventTemplate.Properties.Builder].
             */
            public fun setPublishedDate(date: String?): Builder {
                return this.also { it.publishedDate = date }
            }

            /**
             * Set the life time value.
             *
             * @param value is life time value
             * @return A [MediaEventTemplate.Properties.Builder].
             */
            public fun setIsLifetimeValue(value: Boolean): Builder {
                return this.also { it.isLTV = value }
            }

            internal fun setSource(source: String?): Builder {
                return this.also { it.source = source }
            }

            internal fun setMedium(medium: String?): Builder {
                return this.also { it.medium = medium }
            }

            /**
             * Makes properties instance with the configured parameters
             *
             * @return [Properties]
             */
            public fun build(): Properties = Properties(
                id = this.id,
                category = this.category,
                type = this.type,
                eventDescription = this.eventDescription,
                author = this.author,
                publishedDate = this.publishedDate,
                isFeature = this.isFeature,
                isLTV = this.isLTV,
                source = this.source,
                medium = this.medium,
            )
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            IS_LTV to isLTV,
            IS_FEATURE to isFeature,
            ID to id,
            CATEGORY to category,
            TYPE to type,
            SOURCE to source,
            MEDIUM to medium,
            EVENT_DESCRIPTION to eventDescription,
            AUTHOR to author,
            PUBLISHED_DATE to publishedDate,
        ).toJsonValue()
    }

    internal companion object {
        internal const val TEMPLATE_NAME = "media"
    }
}

/**
 * Creates a new [MediaEventTemplate.Properties].
 *
 * @param block A lambda function that configures the `Builder`.
 * @return A new `MediaEventTemplate.Properties` instance.
 */
@JvmSynthetic
public fun mediaEventProperties(
    block: MediaEventTemplate.Properties.Builder.() -> Unit
): MediaEventTemplate.Properties {
    val builder = MediaEventTemplate.Properties.newBuilder()
    builder.block()
    return builder.build()
}
