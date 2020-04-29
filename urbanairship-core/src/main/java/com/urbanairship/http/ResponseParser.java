/* Copyright Airship and Contributors */

package com.urbanairship.http;

import java.util.List;
import java.util.Map;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Parses a response.
 *
 * @param <T> The result type.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface ResponseParser<T> {
    T parseResponse(int status, @Nullable Map<String, List<String>> headers, @Nullable String responseBody) throws Exception;
}
