/* Copyright Airship and Contributors */

package com.urbanairship.analytics.templates

import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

/**
 * Search event template. Use with [com.urbanairship.analytics.CustomEvent.newBuilder]
 * or [com.urbanairship.analytics.customEvent] to create a custom event with search template properties.
 *
 * Example (Kotlin):
 * ```
 * customEvent(
 *     SearchEventTemplate.Type.SEARCH,
 *     SearchEventTemplate.Properties(
 *         category = "hotels",
 *         query = "beach resort",
 *         totalResults = 53
 *     )
 * ) {
 *     setEventValue(1.0)
 * }.track()
 * ```
 *
 * Example (Java):
 * ```
 * SearchEventTemplate.Properties properties = SearchEventTemplate.Properties.newBuilder()
 *     .setCategory("hotels")
 *     .setQuery("beach resort")
 *     .setTotalResults(53)
 *     .build();
 *
 * CustomEvent.newBuilder(SearchEventTemplate.Type.SEARCH, properties)
 *     .setEventValue(1.0)
 *     .build()
 *     .track();
 * ```
 *
 * @see com.urbanairship.analytics.CustomEvent.newBuilder
 * @see com.urbanairship.analytics.customEvent
 */
public sealed class SearchEventTemplate {

    public enum class Type(internal val eventName: String) {
        SEARCH("search")
    }

    public class Properties(
        /** The event's ID. */
        public val id: String? = null,

        /** The search query. */
        public val query: String? = null,

        /** The total search results */
        public val totalResults: Long? = null,

        /** The event's category. */
        public val category: String? = null,

        /** The event's type. */
        public val type: String? = null,

        /** If the value is a lifetime value or not. */
        public val isLTV: Boolean = false
    ): JsonSerializable {

        public companion object {
            private const val ID = "id"
            private const val QUERY = "query"
            private const val TOTAL_RESULTS = "total_results"
            private const val CATEGORY = "category"
            private const val TYPE = "type"
            private const val IS_LTV = "ltv"

            @JvmStatic
            public fun newBuilder(): Builder = Builder()
        }

        public class Builder(
            private var id: String? = null,
            private var query: String? = null,
            private var totalResults: Long? = null,
            private var category: String? = null,
            private var type: String? = null,
            private var isLTV: Boolean = false
        ) {

            /**
             * Set the type.
             *
             * @param type The type as a string.
             * @return An [Builder].
             */
            public fun setType(type: String?): Builder {
                return this.also { it.type = type }
            }

            /**
             * Set the query.
             *
             * @param query The query as a string.
             * @return An [Builder].
             */
            public fun setQuery(query: String?): Builder {
                return this.also { it.query = query }
            }

            /**
             * Set the category.
             *
             * @param category The category as a string.
             * @return An [Builder].
             */
            public fun setCategory(category: String?): Builder {
                return this.also { it.category = category }
            }

            /**
             * Set the ID.
             *
             * @param id The ID as a string.
             * @return An [Builder].
             */
            public fun setId(id: String?): Builder {
                return this.also { it.id = id }
            }

            /**
             * Set the total results.
             *
             * @param totalResults The total results as a string.
             * @return An [Builder].
             */
            public fun setTotalResults(totalResults: Long): Builder {
                return this.also { it.totalResults = totalResults }
            }

            /**
             * Set the life time value.
             *
             * @param value is life time value
             * @return A [Builder].
             */
            public fun setIsLifetimeValue(value: Boolean): Builder {
                return this.also { it.isLTV = value }
            }

            public fun build(): Properties = Properties(
                id = this.id,
                query = this.query,
                totalResults = this.totalResults,
                category = this.category,
                type = this.type,
                isLTV = this.isLTV
            )
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            ID to id,
            QUERY to query,
            TOTAL_RESULTS to totalResults,
            CATEGORY to category,
            TYPE to type,
            IS_LTV to isLTV
        ).toJsonValue()
    }

    internal companion object {
        const val TEMPLATE_NAME = "search"
    }
}

/**
 * Creates a new [SearchEventTemplate.Properties].
 *
 * @param block A lambda function that configures the `Builder`.
 * @return A new `SearchEventTemplate.Properties` instance.
 */
@JvmSynthetic
public fun searchEventProperties(
    block: SearchEventTemplate.Properties.Builder.() -> Unit
): SearchEventTemplate.Properties {
    val builder = SearchEventTemplate.Properties.newBuilder()
    builder.block()
    return builder.build()
}
