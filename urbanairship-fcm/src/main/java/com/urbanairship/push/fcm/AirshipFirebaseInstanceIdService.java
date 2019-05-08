/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.push.fcm;

import android.content.Context;

import com.urbanairship.push.PushProviderBridge;

/**
 * Urban Airship FirebaseInstanceIdService.
 *
 * @deprecated Use AirshipFirebaseMessagingService instead
 */
@Deprecated
public class AirshipFirebaseInstanceIdService {
    /**
     * Called to handle token refresh.
     *
     * @deprecated Use AirshipFirebaseMessagingService.processTokenRefresh instead
     *
     * @param context The application context.
     */
    @Deprecated
    public static void processTokenRefresh(Context context) {
        PushProviderBridge.requestRegistrationUpdate(context);
    }
}