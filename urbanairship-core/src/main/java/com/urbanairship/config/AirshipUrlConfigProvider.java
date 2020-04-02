/* Copyright Airship and Contributors */

package com.urbanairship.config;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Provides {@link AirshipUrlConfig}.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AirshipUrlConfigProvider {
    @NonNull
    AirshipUrlConfig getConfig();
}
