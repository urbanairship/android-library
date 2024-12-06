/* Copyright Airship and Contributors */

package com.urbanairship.util

import androidx.annotation.RestrictTo

/**
 * Helper method that checks if a [String] is a valid email address.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun String.airshipIsValidEmail(): Boolean {
    val emailRegex = "^[^@\\s]+@[^@\\s]+\\.[^@\\s.]+$".toRegex()
    return emailRegex.matches(this)
}
