/* Copyright Airship and Contributors */

package com.urbanairship.config;

import android.net.Uri;

import com.urbanairship.Logger;

import java.net.MalformedURLException;
import java.net.URL;

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
     * Returns the URL. If the URL is malformed or the base URL is null, null will be returned.
     *
     * @return The URL or null.
     */
    @Nullable
    public URL build() {
        if (uriBuilder == null) {
            return null;
        }
        Uri uri = uriBuilder.build();
        try {
            return new URL(uri.toString());
        } catch (MalformedURLException e) {
            Logger.error(e, "Failed to build URL");
            return null;
        }
    }

}
