/* Copyright Airship and Contributors */

package com.urbanairship;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Airship package info.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AirshipVersionInfo {

    /**
     * The Airship semantic version.
     *
     * @return The version string.
     */
    @NonNull
    String getAirshipVersion();

    /**
     * The full package version with format - "!SDK-VERSION-STRING!:<GROUP>:<ARTIFACT_ID>[:<VERSION_QUALIFIER>]:[VERSION]
     *
     * @return The package version.
     */
    @NonNull
    String getPackageVersion();

}
