/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.net.Uri;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowContentResolver;

@Implements(ContentResolver.class)
public class CustomShadowContentResolver extends ShadowContentResolver {

    /*
     * Default implementation is missing this method.
     */
    @Implementation
    public final String getType(Uri uri) {
        ContentProvider provider = getProvider(uri);
        if (provider != null) {
            return provider.getType(uri);
        } else {
            return null;
        }
    }

}
