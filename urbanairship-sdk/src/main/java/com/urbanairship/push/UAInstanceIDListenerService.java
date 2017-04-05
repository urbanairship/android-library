/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import com.google.android.gms.iid.InstanceIDListenerService;
import com.urbanairship.Logger;

/**
 * Listens for GCM Security token refresh.
 */
public class UAInstanceIDListenerService extends InstanceIDListenerService {

    @Override
    public void onTokenRefresh() {
        super.onTokenRefresh();
        Logger.info("GCM token refreshed.");
    }
}
