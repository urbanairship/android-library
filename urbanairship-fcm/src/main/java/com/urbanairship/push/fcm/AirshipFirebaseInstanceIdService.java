package com.urbanairship.push.fcm;

import android.content.Context;
import android.support.annotation.NonNull;

import com.google.firebase.iid.FirebaseInstanceIdService;

/**
 * Urban Airship FirebaseInstanceIdService.
 *
 * @deprecated To be removed in SDK 12. Use {@link AirshipFirebaseMessagingService} instead.
 */
@Deprecated
public class AirshipFirebaseInstanceIdService extends FirebaseInstanceIdService {

    @Override
    public void onTokenRefresh() {
        super.onTokenRefresh();
        AirshipFirebaseMessagingService.processNewToken(getApplicationContext());
    }

    /**
     * Called to handle token refresh.
     *
     * @param context The application context.
     * @deprecated To be removed in SDK 12. Use {@link AirshipFirebaseMessagingService#processNewToken(Context)}
     */
    @Deprecated
    public static void processTokenRefresh(@NonNull Context context) {
        AirshipFirebaseMessagingService.processNewToken(context);
    }
}
