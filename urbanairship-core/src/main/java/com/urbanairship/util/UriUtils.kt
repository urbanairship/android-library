/* Copyright Airship and Contributors */
package com.urbanairship.util

import android.net.Uri
import androidx.annotation.RestrictTo
import com.urbanairship.util.UAStringUtil.isEmpty
import java.net.URL

/**
 * A class containing utility methods related to URI data.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object UriUtils {

    /**
     * Gets all of the query parameters and values as a map from a
     * given uri.
     *
     * @param uri The uri to parse
     * @return A map of query parameter name to values
     */
    public fun getQueryParameters(uri: Uri): Map<String, List<String?>> {
        val parameters: MutableMap<String, MutableList<String?>> = mutableMapOf()

        val query = uri.encodedQuery ?: return parameters.toMap()

        query
            .split("&".toRegex())
            .dropLastWhile { it.isEmpty() }
            .forEach { param ->
                val keyValuePair = param
                    .split("=".toRegex())
                    .dropLastWhile { it.isEmpty() }

                val name = if (keyValuePair.size >= 1) Uri.decode(keyValuePair[0]) else return@forEach
                val value = if (keyValuePair.size >= 2) Uri.decode(keyValuePair[1]) else null

                val saved = parameters.getOrPut(name) { mutableListOf() }
                saved.add(value)
            }

        return parameters.toMap()
    }

    /**
     * Helper method that parses an object as a Uri
     *
     * @param value The value to parse
     * @return A Uri representation of the value, or `null` if the value
     * is not able to be parsed to a Uri.
     */
    public fun parse(value: Any?): Uri? {
        return when (value) {
            is String, is Uri, is URL -> Uri.parse(value.toString())
            else -> null
        }
    }
}
