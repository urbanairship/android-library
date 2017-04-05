/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.location.Criteria;
import android.location.LocationManager;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowLocationManager;

import java.util.List;

@Implements(LocationManager.class)
public class CustomShadowLocationManager extends ShadowLocationManager {

    @Implementation
    public final List<String> getProviders(Criteria criteria, boolean isEnabled) {
        return this.getProviders(isEnabled);
    }

}
