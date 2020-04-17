/* Copyright Airship and Contributors */

package com.urbanairship.push.fcm;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import androidx.annotation.NonNull;

/**
 * Airship FirebaseMessagingService.
 */
public class AirshipFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        AirshipFirebaseIntegration.processMessageSync(getApplicationContext(), message);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        AirshipFirebaseIntegration.processNewToken(getApplicationContext());
    }
}
