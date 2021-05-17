/* Copyright Airship and Contributors */

package com.urbanairship.config;

import com.urbanairship.UAirship;

import androidx.annotation.RestrictTo;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface PlatformProvider {
    @UAirship.Platform
    int getPlatform();
}
