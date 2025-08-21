/* Copyright Airship and Contributors */
package com.urbanairship.http

import android.net.Uri
import androidx.annotation.RestrictTo

/**
 * Http request.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class Request(
    val url: Uri?,
    val method: String,
    val auth: RequestAuth? = null,
    val body: RequestBody? = null,
    val headers: Map<String, String> = emptyMap(),
    val followRedirects: Boolean = true
) {
    public constructor(url: Uri?, method: String, followRedirects: Boolean) : this(
        url = url,
        auth = null,
        body = null,
        method = method,
        headers = emptyMap(),
        followRedirects = followRedirects
    )
}
