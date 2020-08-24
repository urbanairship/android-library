/* Copyright Airship and Contributors */

package com.urbanairship.push.hms;

import android.content.Context;

import androidx.annotation.NonNull;

import com.huawei.hms.push.RemoteMessage;
import com.urbanairship.PendingResult;
import com.urbanairship.push.PushMessage;
import com.urbanairship.push.PushProviderBridge;

import java.util.concurrent.Future;

/**
 * Airship HMS integration.
 */
public class AirshipHmsIntegration {

    /**
     * Called to handle {@link com.huawei.hms.push.HmsMessageService#onMessageReceived(RemoteMessage)} The task should be finished
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
        PushProviderBridge.processPush(HmsPushProvider.class, new PushMessage(message.getDataOfMap()))
                          .execute(context, new Runnable() {
                              @Override
                              public void run() {
                                  pendingResult.setResult(null);
                              }
                          });

        return pendingResult;
    }

    /**
     * Called to handle {@link com.huawei.hms.push.HmsMessageService#onMessageReceived(RemoteMessage)}} synchronously.
     *
     * @param context The application context.
     * @param message The message.
     */
    public static void processMessageSync(@NonNull Context context, @NonNull RemoteMessage message) {
        PushProviderBridge.processPush(HmsPushProvider.class, new PushMessage(message.getDataOfMap()))
                          .executeSync(context);
    }

    /**
     * Called to handle new tokens.
     *
     * @param context The application context.
     * @param token The token.
     */
    public static void processNewToken(@NonNull Context context, @NonNull String token) {
        HmsTokenCache.shared().set(context, token);
        PushProviderBridge.requestRegistrationUpdate(context);
    }
}
