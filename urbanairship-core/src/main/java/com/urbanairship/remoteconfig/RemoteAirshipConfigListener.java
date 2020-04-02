/* Copyright Airship and Contributors */

package com.urbanairship.remoteconfig;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * {@link RemoteAirshipConfig} listener.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface RemoteAirshipConfigListener {
    void onRemoteConfigUpdated(@NonNull RemoteAirshipConfig remoteAirshipConfig);
}
