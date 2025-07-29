/* Copyright Airship and Contributors */
package com.urbanairship.push.fcm

import android.content.Context
import com.urbanairship.PendingResult
import com.urbanairship.push.PushMessage
import com.urbanairship.push.PushProviderBridge
import java.util.concurrent.Future
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase integration.
 */
public object AirshipFirebaseIntegration {

    /**
     * Called to handle [com.google.firebase.messaging.FirebaseMessagingService.onMessageReceived]. The task should be finished
     * before `onMessageReceived(RemoteMessage message)` is complete. Wait for the message to be complete
     * by calling `get()` on the future.
     *
     * @param context The application context.
     * @param message The message.
     * @return A future.
     */
    public fun processMessage(context: Context, message: RemoteMessage): Future<Void> {
        val pendingResult = PendingResult<Void>()

        PushProviderBridge
            .processPush(FcmPushProvider::class.java, PushMessage(message.data))
            .execute(context) { pendingResult.setResult(null) }

        return pendingResult
    }

    /**
     * Called to handle [com.google.firebase.messaging.FirebaseMessagingService.onMessageReceived] synchronously.
     *
     * @param context The application context.
     * @param message The message.
     */
    @JvmStatic
    public fun processMessageSync(context: Context, message: RemoteMessage) {
        PushProviderBridge
            .processPush(FcmPushProvider::class.java, PushMessage(message.data))
            .executeSync(context)
    }

    /**
     * Called to handle new tokens.
     *
     * @param context The application context.
     * @param token The new token.
     */
    @JvmStatic
    public fun processNewToken(context: Context, token: String?) {
        PushProviderBridge
            .requestRegistrationUpdate(context, FcmPushProvider::class.java, token)
    }

    /**
     * Checks if the push is from Airship or not.
     *
     * @param message The message.
     * @return `true` if its from Airship, otherwise `false`.
     */
    public fun isAirshipPush(message: RemoteMessage): Boolean {
        return PushMessage(message.data).isAirshipPush
    }
}
