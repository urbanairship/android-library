/* Copyright Airship and Contributors */

package com.urbanairship.util

import android.content.Context
import android.util.Base64
import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import java.io.UnsupportedEncodingException
import java.security.InvalidKeyException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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

/**
 * Builds a new string by repeating [this] with separator
 *
 * @param times The number of times to append the string.
 * @param separator The separator string.
 * @return The new string.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun String.repeat(times: Int, separator: String): String {
    return List(times) { this }.joinToString(separator)
}

/**
 * Returns the sha256 hex string.
 *
 * @return The sha256 hex string or null if the value is null or it failed encode the string.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun String.toSha256(): String? {
    val digest = getSha256Digest() ?: return null
    return digest.toHexString()
}

/**
 * Generates the sha256 digest.
 *
 * @return The sha256 digest for the string, or null if it failed to create the digest.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun String.getSha256Digest(): ByteArray? {
    try {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(this.toByteArray(charset("UTF-8")))
    } catch (e: NoSuchAlgorithmException) {
        UALog.e(e, "Failed to encode string: %s", this)
        return null
    } catch (e: UnsupportedEncodingException) {
        UALog.e(e, "Failed to encode string: %s", this)
        return null
    }
}

/**
 * Converts base64 encoded string to decoded byte array.
 *
 * @return The decoded bytes or null if it failed to be decoded.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun String.base64Decoded(): ByteArray? {
    try {
        return Base64.decode(this, Base64.DEFAULT)
    } catch (_: Exception) {
        UALog.v("Failed to decode string: %s", this)
        return null
    }
}

/**
 * Retrieves a string resource dynamically by its name.
 * If the resource is not found, it returns the provided default value.
 *
 * @param context The context to access resources.
 * @return The localized string or the default value if not found.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun String.stringResource(context: Context): String? {
    val resourceId = context.resources
        .getIdentifier(this, "string", context.applicationInfo.packageName)

    return if (resourceId == 0) {
        null
    } else {
        context.getString(resourceId)
    }
}

/**
 * Generates a base64 encoded HmacSHA256 signed value.
 * @param secret The secret
 * @param values A list of values that will be concatenated by ":"
 * @return A signed token.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Throws(
    NoSuchAlgorithmException::class,
    InvalidKeyException::class,
    UnsupportedEncodingException::class
)
public fun String.toSignedToken(values: List<String>): String {
    val hmac = Mac.getInstance("HmacSHA256")
    val key = SecretKeySpec(this.toByteArray(charset("UTF-8")), "HmacSHA256")
    hmac.init(key)
    val message = values.joinToString(":")
    val hashed = hmac.doFinal(message.toByteArray(charset("UTF-8")))
    return Base64.encodeToString(hashed, Base64.DEFAULT)
}
