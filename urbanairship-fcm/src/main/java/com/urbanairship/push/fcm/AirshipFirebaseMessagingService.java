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
        try {
            handleMessageReceived(getApplicationContext(), message).get();
        } catch (Exception e) {
            Logger.error("Message received exception.", e);
        }
    }

    /**
     * Called to handle {@link #onMessageReceived(RemoteMessage)}. The task should be finished
     * before `onMessageReceived(RemoteMessage message)` is complete. Wait for the message to be complete
     * by calling `get()` on the future.
     * @param context The application context.
     * @param message The message.
     * @return A future.
     */
    public static Future<Void> handleMessageReceived(Context context, RemoteMessage message) {
        /** Creating a map of data that includes the From value so message will be read as
         * Urban Airship Message
         */
        Map<String, String> messageData = message.getData();
        String fromKey = "from";
        messageData.put(fromKey, message.getFrom());

        final PushMessage pushMessage = new PushMessage(messageData);

        final PendingResult<Void> pendingResult = new PendingResult<>();
        PushProviderBridge.receivedPush(context, FcmPushProvider.class, pushMessage, new Runnable() {
            @Override
            public void run() {
                pendingResult.setResult(null);
            }
        });

        return pendingResult;
    }

}
