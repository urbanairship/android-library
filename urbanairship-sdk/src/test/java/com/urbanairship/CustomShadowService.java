/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.app.Service;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowService;

@Implements(Service.class)
public class CustomShadowService extends ShadowService {


    private int lastStopSelfId;

    @Implementation
    public final void stopSelf(int id) {
        lastStopSelfId = id;
    }

    public int getLastStopSelfId() {
        return lastStopSelfId;
    }

}