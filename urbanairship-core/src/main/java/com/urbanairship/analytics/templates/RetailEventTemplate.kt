/* Copyright Airship and Contributors */

package com.urbanairship.analytics.templates

import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

public sealed class RetailEventTemplate {

    public sealed class Type(internal val eventName: String) {
        public data object Browsed: Type("browsed")
        public data object AddedToCart: Type("added_to_cart")
        public data object Starred: Type("starred_product")
        public data object Purchased: Type("purchased")

        public class Shared(
            internal val source: String? = null,
            internal val medium: String? = null
        ): Type("shared_product")

        public class Wishlist(
            internal val id: String? = null,
            internal val name: String? = null
        ): Type("wishlist")
    }

    public class Properties(
        /** The event's ID. */
        public val id: String? = null,

        /** The event's category. */
        public val category: String? = null,

        /** The event's type. */
        public val type: String? = null,

        /** The event's description. */
        public val eventDescription: String? = null,

        /** The brand. */
        public val brand: String? = null,

        /** If its a new item or not. */
        public val isNewItem: Boolean? = null,

        /** The currency. */
        public val currency: String? = null,

        /** If the value is a lifetime value or not. */
        public val isLTV: Boolean = false
    ): JsonSerializable {

        internal var source: String? = null
        internal var medium: String? = null
        internal var wishlistName: String? = null
        internal var wishlistId: String? = null

        public companion object {
            private const val ID = "id"
            private const val CATEGORY = "category"
            private const val TYPE = "type"
            private const val EVENT_DESCRIPTION = "description"
            private const val BRAND = "brand"
            private const val IS_NEW_ITEM = "new_item"
            private const val CURRENCY = "currency"
            private const val IS_LTV = "ltv"
            private const val SOURCE = "source"
            private const val MEDIUM = "medium"
            private const val WISHLIST_NAME = "wishlist_name"
            private const val WISHLIST_ID = "wishlist_id"

            @JvmStatic
            public fun newBuilder(): Builder = Builder()
        }

        public class Builder(
            internal var id: String? = null,
            internal var category: String? = null,
            internal var type: String? = null,
            internal var eventDescription: String? = null,
            internal var brand: String? = null,
            internal var isNewItem: Boolean? = null,
            internal var currency: String? = null,
            internal var isLTV: Boolean = false,
            internal var source: String? = null,
            internal var medium: String? = null,
            internal var wishlistName: String? = null,
            internal var wishlistId: String? = null
        ) {

            internal constructor(properties: Properties): this(
                id = properties.id,
                category = properties.category,
                type = properties.type,
                eventDescription = properties.eventDescription,
                brand = properties.brand,
                isNewItem = properties.isNewItem,
                currency = properties.currency,
                isLTV = properties.isLTV,
                source = properties.source,
                medium = properties.medium,
                wishlistName = properties.wishlistName,
                wishlistId = properties.wishlistId
            )

            /**
             * Set the ID.
             *
             * @param id The ID as a string.
             * @return A [Builder].
             */
            public fun setId(id: String?): Builder {
                return this.also { it.id = id }
            }

            /**
             * Set the category.
             *
             * @param category The category as a string.
             * @return A [Builder].
             */
            public fun setCategory(category: String?): Builder {
                return this.also { it.category = category }
            }

            /**
             * Set the type.
             *
             * @param type The type as a string.
             * @return A [Builder].
             */
            public fun setType(type: String?): Builder {
                return this.also { it.type = type }
            }

            /**
             * Set the description.
             *
             * @param description The description as a string.
             * @return A [Builder].
             */
            public fun setDescription(description: String?): Builder {
                return this.also { it.eventDescription = description }
            }

            /**
             * Set the currency.
             *
             * @param currency The currency name.
             * @return A [Builder].
             */
            public fun setCurrency(currency: String?): Builder {
                return this.also { it.currency = currency }
            }

            /**
             * Set the brand.
             *
             * @param brand The brand as a string.
             * @return A [Builder].
             */
            public fun setBrand(brand: String?): Builder {
                return this.also { it.brand = brand }
            }

            /**
             * Set the newItem value.
             *
             * @param newItem A boolean value indicating if the item is new or not.
             * @return A [Builder].
             */
            public fun setNewItem(newItem: Boolean): Builder {
                return this.also { it.isNewItem = newItem }
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

            internal fun setSource(source: String?): Builder {
                return this.also { it.source = source }
            }

            internal fun setMedium(medium: String?): Builder {
                return this.also { it.medium = medium }
            }

            internal fun setWhishlistName(wishlistName: String?): Builder {
                return this.also { it.wishlistName = wishlistName }
            }

            internal fun setWishlistId(wishlistId: String?): Builder {
                return this.also { it.wishlistId = wishlistId }
            }

            public fun build(): Properties = Properties(
                id = this.id,
                category = this.category,
                type = this.type,
                eventDescription = this.eventDescription,
                brand = this.brand,
                isNewItem = this.isNewItem,
                currency = this.currency,
                isLTV = this.isLTV,
            ).also {
                it.source = this.source
                it.medium = this.medium
                it.wishlistName = this.wishlistName
                it.wishlistId = this.wishlistId
            }
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            ID to id,
            CATEGORY to category,
            TYPE to type,
            EVENT_DESCRIPTION to eventDescription,
            BRAND to brand,
            IS_NEW_ITEM to isNewItem,
            CURRENCY to currency,
            IS_LTV to isLTV,
            SOURCE to source,
            MEDIUM to medium,
            WISHLIST_NAME to wishlistName,
            WISHLIST_ID to wishlistId
        ).toJsonValue()
    }

    internal companion object {
        const val TEMPLATE_NAME = "retail"
    }
}
