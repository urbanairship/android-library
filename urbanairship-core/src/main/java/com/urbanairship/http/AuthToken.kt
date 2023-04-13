package com.urbanairship.http

import androidx.annotation.RestrictTo

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class AuthToken(
    val identifier: String,
    val token: String,
    val expirationTimeMS: Long
)
