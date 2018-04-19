/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.fcm;

import android.content.Context;
import android.os.Bundle;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.push.PushMessage;
import com.urbanairship.push.PushProviderBridge;

import java.util.Map;
import java.util.concurrent.Future;

/**
 * Urban Airship FirebaseMessagingService.
 */
public class AirshipFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage message) {
        processMessageSync(getApplicationContext(), message);
    }

    /**
     * Called to handle {@link #onMessageReceived(RemoteMessage)}. The task should be finished
     * before `onMessageReceived(RemoteMessage message)` is complete. Wait for the message to be complete
     * by calling `get()` on the future.
     *
     * @param context The application context.
     * @param message The message.
     * @return A future.
     */
    public static Future<Void> processMessage(Context context, RemoteMessage message) {
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
     * Called to handle {@link #onMessageReceived(RemoteMessage)} synchronously.
     *
     * @param context The application context.
     * @param message The message.
     */
    public static void processMessageSync(Context context, RemoteMessage message) {
        PushProviderBridge.processPush(FcmPushProvider.class, new PushMessage(message.getData()))
                          .executeSync(context);
    }
}
