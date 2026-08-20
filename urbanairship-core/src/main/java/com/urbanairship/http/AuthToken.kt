package com.urbanairship.http

import androidx.annotation.RestrictTo
import java.time.Instant

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class AuthToken(
    val identifier: String,
    val token: String,
    val expiration: Instant
)
