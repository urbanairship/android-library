/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.push.gcm;

import com.google.android.gms.iid.InstanceIDListenerService;
import com.urbanairship.Logger;

/**
 * Listens for GCM Security token refresh.
 *
 * @deprecated Marked to be removed in SDK 10.
 */
@Deprecated
public class UAInstanceIDListenerService extends InstanceIDListenerService {

    @Override
    public void onTokenRefresh() {
        super.onTokenRefresh();
        Logger.info("GCM token refreshed.");
    }
}
