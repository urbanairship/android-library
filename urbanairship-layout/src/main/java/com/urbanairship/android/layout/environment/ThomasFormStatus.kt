/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.environment

import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

/** Represents the possible statuses of a form during its lifecycle. */
internal enum class ThomasFormStatus(private val type: String): JsonSerializable {
    /** The form is valid and all its fields are correctly filled out. */
    VALID("valid"),

    /** The form is invalid, possibly due to incorrect or missing information. */
    INVALID("invalid"),

    /** An error occurred during form validation or submission. */
    ERROR("error"),

    /** The form is currently being validated. */
    VALIDATING("validating"),

    /** The form is awaiting validation to be processed. */
    PENDING_VALIDATION("pending_validation"),

    /** The form has been submitted. */
    SUBMITTED("submitted");

    val isSubmitted: Boolean
        get() = this == SUBMITTED

    override fun toJsonValue(): JsonValue = JsonValue.wrap(type)
}
