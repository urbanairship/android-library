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

/**
 * Returns the emoji flag for a given ISO 3166-1 alpha-2 country code.
 *
 * If the country code is not an alpha-2 country code, `null` will be returned.
 * If the country code is not a valid alpha-2 country code, the result will be a question-mark
 * flag, though this behavior may differ depending on Android version and OEM.
 */
public val String.airshipEmojiFlag: String?
    get() = countryFlag(this)


private fun countryFlag(code: String): String? {
    val sanitizedCode = code.uppercase().replace(Regex("[^A-Z]"), "")
    if (sanitizedCode.length != 2) {
        return null
    }

    return sanitizedCode.map { it.code + 0x1F1A5 }.joinToString("") {
        Character.toChars(it).concatToString()
    }
}
