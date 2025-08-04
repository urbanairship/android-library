/* Copyright Airship and Contributors */
package com.urbanairship.wallet

import android.text.TextUtils
import androidx.annotation.Size
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import kotlin.jvm.Throws

/**
 * Defines a field that can be sent up when fetching a [Pass].
 */
public class Field internal constructor(
    @JvmField public val name: String,
    private val label: String?,
    private val value: Any?
) : JsonSerializable {

    override fun toString(): String = toJsonValue().toString()

    override fun toJsonValue(): JsonValue = jsonMapOf(
        LABEL_KEY to label,
        VALUE_KEY to value
    ).toJsonValue()

    /**
     * Builds the [Field] object.
     */
    public class Builder public constructor() {

        internal var name: String? = null
        public var label: String? = null
        public var value: Any? = null

        /**
         * Sets the field's name.
         *
         * @param name The field's name.
         * @return Builder object.
         */
        public fun setName(@Size(min = 1) name: String): Builder {
            this.name = name
            return this
        }

        /**
         * Sets the field's label.
         *
         * @param label The field's label.
         * @return Builder object.
         */
        public fun setLabel(label: String?): Builder {
            this.label = label
            return this
        }

        /**
         * Sets the field's value.
         *
         * @param value The field's value.
         * @return Builder object.
         */
        public fun setValue(value: String?): Builder {
            this.value = value
            return this
        }

        /**
         * Sets the field's value.
         *
         * @param value The field's value.
         * @return Builder object.
         */
        public fun setValue(value: Int): Builder {
            this.value = value
            return this
        }

        /**
         * Builds the field.
         *
         * @return A field instance.
         * @throws IllegalStateException if the name or both the value and label are missing.
         */
        @Throws(IllegalArgumentException::class)
        public fun build(): Field {
            val name = name ?: throw IllegalStateException("The field must have a name.")
            check(!(value == null && label.isNullOrEmpty())) { "The field must have either a value or label." }

            return Field(
                name = name,
                label = label,
                value = value
            )
        }
    }

    public companion object {

        private const val VALUE_KEY = "value"
        private const val LABEL_KEY = "label"

        /**
         * Creates a new Builder instance.
         *
         * @return The new Builder instance.
         */
        @JvmStatic
        public fun newBuilder(): Builder {
            return Builder()
        }
    }
}
