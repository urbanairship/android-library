/* Copyright Airship and Contributors */

package com.urbanairship.analytics.templates

import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

public sealed class AccountEventTemplate {

    public enum class Type(internal val eventName: String) {
        REGISTERED("registered_account"),
        LOGGED_IN("logged_in"),
        LOGGED_OUT("logged_out")
    }

    public class Properties(
        /** User ID. */
        public val userId: String? = null,

        /** The event's category. */
        public val category: String? = null,

        /** The event's type. */
        public val type: String? = null,

        /** If the value is a lifetime value or not. */
        public val isLTV: Boolean = false
    ): JsonSerializable {

        public companion object {
            private const val USER_ID = "user_id"
            private const val CATEGORY = "category"
            private const val TYPE = "type"
            private const val IS_LTV = "ltv"

            @JvmStatic
            public fun newBuilder(): Builder = Builder()
        }

        public class Builder(
            private var userId: String? = null,
            private var category: String? = null,
            private var type: String? = null,
            private var isLTV: Boolean = false
        ) {

            /**
             * Set the category.
             *
             * If the category exceeds 255 characters it will cause the event to be invalid.
             *
             * @param category The category as a string.
             * @return An [Builder].
             */
            public fun setCategory(category: String?): Builder {
                return this.also { it.category = category }
            }

            /**
             * Set the user id.
             *
             * @param userId The user id as a string.
             * @return An [Builder].
             */
            public fun setUserId(userId: String?): Builder {
                return this.also { it.userId = userId }
            }

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
             * Set the life time value.
             *
             * @param value is life time value
             * @return A [Builder].
             */
            public fun setIsLifetimeValue(value: Boolean): Builder {
                return this.also { it.isLTV = value }
            }

            /**
             * Makes properties instance with the configured parameters
             *
             * @return [Properties]
             */
            public fun build(): Properties = Properties(
                userId = this.userId,
                category = this.category,
                type = this.type,
                isLTV = this.isLTV
            )

        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            USER_ID to userId,
            CATEGORY to category,
            TYPE to type,
            IS_LTV to isLTV
        ).toJsonValue()
    }

    internal companion object {
        internal const val TEMPLATE_NAME = "account"
    }
}

/**
 * Creates a new [AccountEventTemplate.Properties].
 *
 * @param block A lambda function that configures the `Builder`.
 * @return A new `AccountEventTemplate.Properties` instance.
 */
@JvmSynthetic
public fun accountEventProperties(
    block: AccountEventTemplate.Properties.Builder.() -> Unit
): AccountEventTemplate.Properties {
    val builder = AccountEventTemplate.Properties.newBuilder()
    builder.block()
    return builder.build()
}
