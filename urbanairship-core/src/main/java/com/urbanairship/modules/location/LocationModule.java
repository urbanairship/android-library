/* Copyright Airship and Contributors */

package com.urbanairship.modules.location;

import com.urbanairship.AirshipComponent;
import com.urbanairship.modules.Module;

import java.util.Collections;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Location module loader.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class LocationModule extends Module {

    private final AirshipLocationClient locationClient;

    /**
     * Default constructor.
     *
     * @param component The component.
     * @param locationClient The location client.
     */
    public LocationModule(@NonNull AirshipComponent component, @NonNull AirshipLocationClient locationClient) {
        super(Collections.singleton(component));
        this.locationClient = locationClient;
    }

    /**
     * Returns the location client.
     *
     * @return The location client.
     */
    @NonNull
    public AirshipLocationClient getLocationClient() {
        return locationClient;
    }

}
