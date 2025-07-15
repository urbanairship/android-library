/* Copyright Airship and Contributors */
package com.urbanairship.config

import android.net.Uri
import androidx.annotation.RestrictTo

/**
 * Url builder.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class UrlBuilder public constructor(public val baseUrl: String?) {
    private var uriBuilder = baseUrl?.let { Uri.parse(it).buildUpon() }

    public fun appendEncodedPath(path: String): UrlBuilder {
        return this.also { uriBuilder?.appendEncodedPath(path) }
    }

    public fun appendPath(path: String): UrlBuilder {
        return this.also { uriBuilder?.appendPath(path) }
    }

    public fun appendQueryParameter(key: String, value: String): UrlBuilder {
        return this.also { uriBuilder?.appendQueryParameter(key, value) }
    }

    /**
     * Returns the Uri. If the Uri is malformed or the base Uri is null, null will be returned.
     *
     * @return The Uri or null.
     */
    public fun build(): Uri? = uriBuilder?.build()
}
