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
 * A class containing utility methods related to strings.
 */
public object UAStringUtil {

    /**
     * Builds a string.
     *
     * @param repeater The string to build.
     * @param times The number of times to append the string.
     * @param separator The separator string.
     * @return The new string.
     */
    public fun repeat(repeater: String, times: Int, separator: String): String {
        val builder = StringBuilder()
        for (i in 0..<times) {
            builder.append(repeater)
            if (i + 1 != times) {
                builder.append(separator)
            }
        }
        return builder.toString()
    }

    /**
     * Checks if the string is empty.
     *
     * @param stringToCheck The string to check.
     * @return `true` if the string is null, `false` otherwise.
     */
    @JvmStatic
    public fun isEmpty(stringToCheck: String?): Boolean {
        return stringToCheck.isNullOrEmpty()
    }

    /**
     * Checks if the strings are equal.
     *
     * @param firstString The first string.
     * @param secondString The second string.
     * @return `true` if the strings are equal, `false` otherwise.
     */
    @JvmStatic
    public fun equals(firstString: String?, secondString: String?): Boolean {
        return firstString == secondString
    }

    /**
     * Append a collection of strings and delimiter.
     *
     * @param c A collection of strings.
     * @param delimiter A delimiter string.
     * @return The new string.
     */
    public fun join(c: Collection<String>, delimiter: String): String {
        return c.joinToString(delimiter)
    }

    /**
     * Returns the sha256 hex string for a given string.
     *
     * @param value The value.
     * @return The sha256 hex string or null if the value is null or it failed encode the string.
     */
    public fun sha256(value: String?): String? {
        if (value == null) {
            return null
        }

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(value.toByteArray(charset("UTF-8")))
            return byteToHex(hash)
        } catch (e: NoSuchAlgorithmException) {
            UALog.e(e, "Failed to encode string: %s", value)
            return null
        } catch (e: UnsupportedEncodingException) {
            UALog.e(e, "Failed to encode string: %s", value)
            return null
        }
    }

    /**
     * Generates the sha256 digest for a given string.
     *
     * @param value The string.
     * @return The sha256 digest for the string, or null if it failed
     * to create the digest.
     */
    public fun sha256Digest(value: String?): ByteArray? {
        if (value == null) {
            return null
        }

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(value.toByteArray(charset("UTF-8")))
        } catch (e: NoSuchAlgorithmException) {
            UALog.e(e, "Failed to encode string: %s", value)
            return null
        } catch (e: UnsupportedEncodingException) {
            UALog.e(e, "Failed to encode string: %s", value)
            return null
        }
    }

    /**
     * Converts the bytes into a hex string.
     *
     * @param bytes The byte array.
     * @return A hex string.
     */
    public fun byteToHex(bytes: ByteArray): String {
        val hexHash = StringBuilder()
        for (b in bytes) {
            hexHash.append(String.format("%02x", b))
        }
        return hexHash.toString()
    }

    /**
     * Base64 decodes a string.
     *
     * @param encoded The base64 encoded string.
     * @return The decoded bytes or null if it failed to be decoded.
     */
    public fun base64Decode(encoded: String?): ByteArray? {
        if (isEmpty(encoded)) {
            return null
        }

        // Decode it
        try {
            return Base64.decode(encoded, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            UALog.v("Failed to decode string: %s", encoded)
            return null
        }
    }

    /**
     * Generates a base 64 decoded string.
     *
     * @param encoded The encoded value.
     * @return The decoded string or null if it failed to be decoded.
     */
    public fun base64DecodedString(encoded: String?): String? {
        val decoded = base64Decode(encoded) ?: return null

        try {
            return String(decoded, charset("UTF-8"))
        } catch (e: UnsupportedEncodingException) {
            UALog.e(e, "Failed to create string")
            return null
        }
    }

    @JvmStatic
    public fun nullIfEmpty(value: String?): String? {
        if (isEmpty(value)) {
            return null
        }

        return value
    }

    /**
     * Retrieves a string resource dynamically by its name.
     * If the resource is not found, it returns the provided default value.
     *
     * @param context The context to access resources.
     * @param name The name of the string resource.
     * @param defaultValue The default value if the string resource is not found.
     * @return The localized string or the default value if not found.
     */
    public fun namedStringResource(context: Context, name: String, defaultValue: String): String {
        val resourceId = context.resources
            .getIdentifier(name, "string", context.applicationInfo.packageName)

        return if (resourceId == 0) {
            defaultValue
        } else {
            context.getString(resourceId)
        }
    }

    /**
     * Retrieves a string resource dynamically by its name.
     * If the resource is not found, it returns the provided default value.
     *
     * @param context The context to access resources.
     * @param name The name of the string resource.
     * @return The localized string or the default value if not found.
     */
    public fun namedStringResource(context: Context, name: String): String? {
        val resourceId = context.resources
            .getIdentifier(name, "string", context.applicationInfo.packageName)

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
    public fun generateSignedToken(secret: String, values: List<String>): String {
        val hmac = Mac.getInstance("HmacSHA256")
        val key = SecretKeySpec(secret.toByteArray(charset("UTF-8")), "HmacSHA256")
        hmac.init(key)
        val message = java.lang.String.join(":", values)
        val hashed = hmac.doFinal(message.toByteArray(charset("UTF-8")))
        return Base64.encodeToString(hashed, Base64.DEFAULT)
    }
}
