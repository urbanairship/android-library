package com.urbanairship.http

import androidx.annotation.RestrictTo

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AuthTokenProvider {
    public suspend fun fetchToken(identifier: String): Result<String>
    public suspend fun expireToken(token: String)
}
