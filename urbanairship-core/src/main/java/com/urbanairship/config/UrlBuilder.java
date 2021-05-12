/* Copyright Airship and Contributors */

package com.urbanairship.config;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Url builder.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class UrlBuilder {

    private Uri.Builder uriBuilder;

    /**
     * Default constructor.
     *
     * @param url The URL.
     */
    public UrlBuilder(@Nullable String url) {
        if (url != null) {
            uriBuilder = Uri.parse(url).buildUpon();
        }
    }

    @NonNull
    public UrlBuilder appendEncodedPath(@NonNull String path) {
        if (uriBuilder != null) {
            uriBuilder.appendEncodedPath(path);
        }
        return this;
    }

    @NonNull
    public UrlBuilder appendPath(@NonNull String path) {
        if (uriBuilder != null) {
            uriBuilder.appendPath(path);
        }
        return this;
    }

    @NonNull
    public UrlBuilder appendQueryParameter(@NonNull String key, @NonNull String value) {
        if (uriBuilder != null) {
            uriBuilder.appendQueryParameter(key, value);
        }
        return this;
    }

    /**
     * Returns the Uri. If the Uri is malformed or the base Uri is null, null will be returned.
     *
     * @return The Uri or null.
     */
    @Nullable
    public Uri build() {
        if (uriBuilder == null) {
            return null;
        }
        return uriBuilder.build();
    }
}
