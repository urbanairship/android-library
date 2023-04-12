package com.urbanairship.http

import androidx.annotation.RestrictTo

/**
 * Parses a response.
 *
 * @param <T> The result type.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface RequestSession {

    public var channelAuthTokenProvider: AuthTokenProvider?
    public var contactAuthTokenProvider: AuthTokenProvider?

    @Throws(RequestException::class)
    public fun execute(request: Request): Response<Unit>

    /**
     * Executes the request.
     *
     * @return The request response.
     */
    @Throws(RequestException::class)
    public fun <T> execute(
        request: Request,
        parser: ResponseParser<T>
    ): Response<T>
}
