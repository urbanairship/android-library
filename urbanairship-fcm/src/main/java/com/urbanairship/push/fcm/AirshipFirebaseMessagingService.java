/* Copyright Airship and Contributors */

package com.urbanairship.push.fcm;

import android.annotation.SuppressLint;
import android.content.Context;
import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.concurrent.Future;

/**
 * Airship FirebaseMessagingService.
 */
public class AirshipFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    @SuppressLint("UnknownNullness")
    public void onMessageReceived(RemoteMessage message) {
        AirshipFirebaseIntegration.processMessageSync(getApplicationContext(), message);
    }

    @Override
    @SuppressLint("UnknownNullness")
    public void onNewToken(String token) {
        AirshipFirebaseIntegration.processNewToken(getApplicationContext());
    }

    /**
     * Called to handle {@link #onMessageReceived(RemoteMessage)}. The task should be finished
     * before `onMessageReceived(RemoteMessage message)` is complete. Wait for the message to be complete
     * by calling `get()` on the future.
     *
     * @param context The application context.
     * @param message The message.
     * @return A future.
     * @deprecated Use {@link AirshipFirebaseIntegration#processMessage(Context, RemoteMessage)} instead.
     */
    @Deprecated
    @NonNull
    public static Future<Void> processMessage(@NonNull Context context, @NonNull RemoteMessage message) {
        return AirshipFirebaseIntegration.processMessage(context, message);
    }

    /**
     * Called to handle {@link #onMessageReceived(RemoteMessage)} synchronously.
     *
     * @param context The application context.
     * @param message The message.
     * @deprecated Use {@link AirshipFirebaseIntegration#processMessageSync(Context, RemoteMessage)} instead.
     */
    @Deprecated
    public static void processMessageSync(@NonNull Context context, @NonNull RemoteMessage message) {
        AirshipFirebaseIntegration.processMessageSync(context, message);
    }

}
