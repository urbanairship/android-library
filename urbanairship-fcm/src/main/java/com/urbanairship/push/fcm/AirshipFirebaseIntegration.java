/* Copyright Airship and Contributors */

package com.urbanairship.push.fcm;

import android.content.Context;

import com.google.firebase.messaging.RemoteMessage;
import com.urbanairship.PendingResult;
import com.urbanairship.push.PushMessage;
import com.urbanairship.push.PushProviderBridge;

import java.util.concurrent.Future;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Firebase integration.
 */
public class AirshipFirebaseIntegration {

    /**
     * Called to handle {@link com.google.firebase.messaging.FirebaseMessagingService#onMessageReceived(RemoteMessage)}. The task should be finished
     * before `onMessageReceived(RemoteMessage message)` is complete. Wait for the message to be complete
     * by calling `get()` on the future.
     *
     * @param context The application context.
     * @param message The message.
     * @return A future.
     */
    @NonNull
    public static Future<Void> processMessage(@NonNull Context context, @NonNull RemoteMessage message) {
        final PendingResult<Void> pendingResult = new PendingResult<>();
        PushProviderBridge.processPush(FcmPushProvider.class, new PushMessage(message.getData()))
                          .execute(context, new Runnable() {
                              @Override
                              public void run() {
                                  pendingResult.setResult(null);
                              }
                          });

        return pendingResult;
    }

    /**
     * Called to handle {@link com.google.firebase.messaging.FirebaseMessagingService#onMessageReceived(RemoteMessage)} synchronously.
     *
     * @param context The application context.
     * @param message The message.
     */
    public static void processMessageSync(@NonNull Context context, @NonNull RemoteMessage message) {
        PushProviderBridge.processPush(FcmPushProvider.class, new PushMessage(message.getData()))
                          .executeSync(context);
    }

    /**
     * Called to handle new tokens.
     *
     * @param context The application context.
     * @param token The new token.
     */
    public static void processNewToken(@NonNull Context context, @Nullable String token) {
        PushProviderBridge.requestRegistrationUpdate(context, FcmPushProvider.class, token);
    }

    /**
     * Checks if the push is from Airship or not.
     *
     * @param message The message.
     * @return {@code true} if its from Airship, otherwise {@code false}.
     */
    public static boolean isAirshipPush(@NonNull RemoteMessage message) {
        return new PushMessage(message.getData()).isAirshipPush();
    }

}
