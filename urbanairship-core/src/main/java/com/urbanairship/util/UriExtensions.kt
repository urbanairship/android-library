/* Copyright Airship and Contributors */

package com.urbanairship.util

import android.net.Uri
import androidx.annotation.RestrictTo
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL

/**
 * Converts a [Uri] to a [URI].
 * @hide
 * @throws URISyntaxException If the `Uri` is not a valid `URI`.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Throws(URISyntaxException::class)
public fun Uri.toURI(): URI = URI(toString())

/**
 * Helper method that converts a [Uri] to a [URL].
 * @hide
 * @throws MalformedURLException If the `Uri` is not a valid `URL`.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Throws(MalformedURLException::class)
public fun Uri.toURL(): URL = URL(toString())

/**
 * Helper method that converts a [Uri] to a [URI], returning null if the conversion fails.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun Uri.toURIOrNull(): URI? = try {
    toURI()
} catch (e: URISyntaxException) {
    null
}

/**
 * Helper method that converts a [Uri] to a [URL], returning null if the conversion fails.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun Uri.toURLOrNull(): URL? = try {
    toURL()
} catch (e: MalformedURLException) {
    null
}
