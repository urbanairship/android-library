/* Copyright Airship and Contributors */

package com.urbanairship.push.fcm;

import android.content.Context;
import androidx.annotation.NonNull;

/**
 * Airship FirebaseInstanceIdService.
 *
 * @deprecated To be removed in SDK 12. Use {@link AirshipFirebaseMessagingService} instead.
 */
@Deprecated
public class AirshipFirebaseInstanceIdService  {

    /**
     * Called to handle token refresh.
     *
     * @param context The application context.
     * @deprecated To be removed in SDK 12. Use {@link AirshipFirebaseIntegration#processNewToken(Context)}
     */
    @Deprecated
    public static void processTokenRefresh(@NonNull Context context) {
        AirshipFirebaseIntegration.processNewToken(context);
    }
}
