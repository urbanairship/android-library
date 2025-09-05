/* Copyright Airship and Contributors */
package com.urbanairship.channel

import androidx.annotation.RestrictTo
import androidx.annotation.Size
import com.urbanairship.UALog
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.util.Clock
import com.urbanairship.util.DateUtils
import java.util.Date
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * Interface used for modifying attributes.
 */
public abstract class AttributeEditor protected constructor(private val clock: Clock) {

    private val partialMutations: MutableList<PartialAttributeMutation> = mutableListOf()

    /**
     * Sets a string attribute.
     *
     * @param attribute The attribute. Must be greater must be greater than 1 character and not contain `#`.
     * @param string The attribute string. It must be greater than 1 character and less than 1024 characters in length.
     * @return The [AttributeEditor].
     * @throws IllegalArgumentException if:
     *  - The key is empty or contains `#`.
     *  - The string is empty or greater than 1024 characters in length.
     */
    @Throws(IllegalArgumentException::class)
    public fun setAttribute(
        @Size(min = 1) attribute: String,
        @Size(min = 1, max = MAX_ATTRIBUTE_FIELD_LENGTH) string: String
    ): AttributeEditor {
        require(string.isNotEmpty() && string.length <= MAX_ATTRIBUTE_FIELD_LENGTH) {
            "Attribute string value must be less than or equal to $MAX_ATTRIBUTE_FIELD_LENGTH characters in length: $string"
        }

        addMutation(attribute = attribute, value = JsonValue.wrap(string)).getOrThrow()
        return this
    }

    /**
     * Sets an integer number attribute.
     *
     *@param attribute The attribute. Must be greater must be greater than 1 character and not contain `#`.
     * @param number The number attribute.
     * @return The [AttributeEditor].
     * @throws IllegalArgumentException if:
     *  - The key is empty or contains `#`.
     */
    @Throws(IllegalArgumentException::class)
    public fun setAttribute(@Size(min = 1) attribute: String, number: Int): AttributeEditor {
        addMutation(attribute = attribute, value = JsonValue.wrap(number)).getOrThrow()
        return this
    }

    /**
     * Sets a long number attribute.
     *
     *@param attribute The attribute. Must be greater must be greater than 1 character and not contain `#`.
     * @param number The number attribute.
     * @return The [AttributeEditor].
     * @throws IllegalArgumentException if:
     *  - The key is empty or contains `#`.
     */
    @Throws(IllegalArgumentException::class)
    public fun setAttribute(@Size(min = 1) attribute: String, number: Long): AttributeEditor {
        addMutation(attribute = attribute, value = JsonValue.wrap(number)).getOrThrow()
        return this
    }

    /**
     * Sets a float number attribute.
     *
     *@param attribute The attribute. Must be greater must be greater than 1 character and not contain `#`.
     * @param number The number attribute.
     * @return The [AttributeEditor].
     * @throws IllegalArgumentException if:
     *  - The key is empty or contains `#`.
     * @throws NumberFormatException if:
     *  - The number is NaN or infinite.
     */
    @Throws(NumberFormatException::class, IllegalArgumentException::class)
    public fun setAttribute(@Size(min = 1) attribute: String, number: Float): AttributeEditor {
        if (number.isNaN() || number.isInfinite()) {
            throw NumberFormatException("Infinity or NaN: $number")
        }
        addMutation(attribute = attribute, value = JsonValue.wrap(number)).getOrThrow()
        return this
    }

    /**
     * Sets a double number attribute.
     *
     *@param attribute The attribute. Must be greater must be greater than 1 character and not contain `#`.
     * @param number The number attribute.
     * @return The [AttributeEditor].
     * @throws IllegalArgumentException if:
     *  - The key is empty or contains `#`.
     * @throws NumberFormatException if:
     *  - The number is NaN or infinite.
     */
    @Throws(NumberFormatException::class, IllegalArgumentException::class)
    public fun setAttribute(@Size(min = 1) attribute: String, number: Double): AttributeEditor {
        if (number.isNaN() || number.isInfinite()) {
            throw NumberFormatException("Infinity or NaN: $number")
        }
        addMutation(attribute = attribute, value = JsonValue.wrap(number)).getOrThrow()
        return this
    }

    /**
     * Sets a date attribute.
     *
     *@param attribute The attribute. Must be greater must be greater than 1 character and not contain `#`.
     * @param date The date attribute.
     * @return The [AttributeEditor].
     * @throws IllegalArgumentException if:
     *  - The key is empty or contains `#`.
     */
    @Throws(IllegalArgumentException::class)
    public fun setAttribute(@Size(min = 1) attribute: String, date: Date): AttributeEditor {
        val dateString = DateUtils.createIso8601TimeStamp(date.time)
        addMutation(attribute = attribute, value = JsonValue.wrap(dateString)).getOrThrow()
        return this
    }

    /**
     * Removes an attribute.
     *
     * @param attribute The attribute. Must be greater must be greater than 1 character and not contain `#`.
     * @return The [AttributeEditor].
     * @throws IllegalArgumentException if:
     *  - The key is empty or contains `#`.
     */
    @Throws(IllegalArgumentException::class)
    public fun removeAttribute(@Size(min = 1) attribute: String): AttributeEditor {
        addMutation(attribute = attribute, value = null).getOrThrow()
        return this
    }

    /**
     * Removes a JSON attribute for the given instance.
     *
     * @param attribute The attribute.
     * @param instanceId The instance identifier.
     * @return The [AttributeEditor].
     * @throws IllegalArgumentException if:
     *  - The key is empty or contains `#`.
     */
    @Throws(IllegalArgumentException::class)
    public fun removeAttribute(@Size(min = 1) attribute: String, @Size(min = 1) instanceId: String): AttributeEditor {
        addMutation(attribute = attribute, instanceId = instanceId, value = null).getOrThrow()
        return this
    }

    /**
     * Sets a custom attribute with a JSON payload and optional expiration.
     *
     * @param attribute The attribute.
     * @param instanceId The instance identifier.
     * @param json A JsonMap representing the custom payload.
     * @return The [AttributeEditor].
     * @throws IllegalArgumentException if:
     *  - The key is empty or contains `#`.
     *  - The expiration is invalid (in the past or > 731 days from now.
     *  - The payload is empty or contains a reserved key `exp`.
     */
    @Throws(IllegalArgumentException::class)
    @JvmOverloads
    public fun setAttribute(
        @Size(min = 1) attribute: String,
        @Size(min = 1) instanceId: String,
        expiration: Date? = null,
        json: JsonMap
    ): AttributeEditor {
        val now = clock.currentTimeMillis().milliseconds
        if (expiration != null) {
            val expMillis = expiration.time.milliseconds
            val maxMillis = now + 731.days
            require(!(expMillis <= now || expMillis > maxMillis)) {
                "The expiration is invalid (more than 731 days or not in the future)."
            }
        }

        require(!json.isEmpty) { "The input `json` payload is empty." }
        require(!json.containsKey(JSON_EXPIRY_KEY)) { "The JSON contains a top-level `$JSON_EXPIRY_KEY` key (reserved for expiration)." }

        // Build JSON payload with optional expiration
        val builder = JsonMap.newBuilder().putAll(json)
        if (expiration != null) {
            val expSeconds = expiration.time.milliseconds.inWholeSeconds
            builder.put(JSON_EXPIRY_KEY, expSeconds)
        }
        val finalJson = builder.build().toJsonValue()

        addMutation(attribute = attribute, instanceId = instanceId, value = finalJson).getOrThrow()
        return this
    }

    /**
     * Apply the attribute changes.
     */
    public fun apply() {
        if (partialMutations.size == 0) {
            return
        }

        val timestamp = clock.currentTimeMillis()
        val mutations: MutableList<AttributeMutation> = ArrayList()
        for (partial in partialMutations) {
            try {
                mutations.add(partial.toMutation(timestamp))
            } catch (e: IllegalArgumentException) {
                UALog.e(e, "Invalid attribute mutation.")
            }
        }

        onApply(AttributeMutation.collapseMutations(mutations))
    }

    /**
     * @param collapsedMutations
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected abstract fun onApply(collapsedMutations: List<AttributeMutation>)

    private fun addMutation(attribute: String, instanceId: String? = null, value: JsonValue?): Result<Unit> {
        if (attribute.isEmpty() || attribute.contains("#")) {
            return Result.failure(IllegalArgumentException("Attribute must not be empty or contain '#'"))
        }

        if (instanceId != null && (instanceId.isEmpty() || instanceId.contains("#"))) {
            return Result.failure(IllegalArgumentException("Instance ID must not be empty or contain '#'"))
        }

        val key = if (instanceId != null) {
            "$attribute#$instanceId"
        } else {
            attribute
        }

        partialMutations.add(PartialAttributeMutation(key, value))
        return Result.success(Unit)
    }


    private inner class PartialAttributeMutation(var key: String, var value: Any?) {

        fun toMutation(timestamp: Long): AttributeMutation {
            return if (value != null) {
                AttributeMutation.newSetAttributeMutation(
                    key, JsonValue.wrapOpt(value), timestamp
                )
            } else {
                AttributeMutation.newRemoveAttributeMutation(key, timestamp)
            }
        }
    }

    public companion object {

        private const val MAX_ATTRIBUTE_FIELD_LENGTH: Long = 1024

        /**
         * Reserved key for JSON attribute expiration.
         */
        private const val JSON_EXPIRY_KEY = "exp"
    }
}
