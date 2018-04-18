/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.push.fcm;

import android.content.Context;

import com.google.firebase.iid.FirebaseInstanceIdService;
import com.urbanairship.push.PushProviderBridge;

/**
 * Urban Airship FirebaseInstanceIdService.
 */
public class AirshipFirebaseInstanceIdService extends FirebaseInstanceIdService {
    @Override
    public void onTokenRefresh() {
        super.onTokenRefresh();
        processTokenRefresh(getApplicationContext());
    }

    /**
     * Called to handle token refresh.
     *
     * @param context The application context.
     */
    public static void processTokenRefresh(Context context) {
        PushProviderBridge.requestRegistrationUpdate(context);
    }
}