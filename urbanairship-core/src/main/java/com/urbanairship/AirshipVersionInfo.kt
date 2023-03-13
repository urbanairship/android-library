/* Copyright Airship and Contributors */
package com.urbanairship

import androidx.annotation.RestrictTo

/**
 * Airship package info.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal interface AirshipVersionInfo {

    /**
     * The Airship semantic version.
     *
     * @return The version string.
     */
    val airshipVersion: String

    /**
     * The full package version with format - "!SDK-VERSION-STRING!:<GROUP>:<ARTIFACT_ID>[:<VERSION_QUALIFIER>]:[VERSION]
     *
     * @return The package version.
    </VERSION_QUALIFIER></ARTIFACT_ID></GROUP> */
    val packageVersion: String
}
