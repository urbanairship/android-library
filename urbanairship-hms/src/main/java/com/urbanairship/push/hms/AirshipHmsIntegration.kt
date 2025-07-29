/* Copyright Airship and Contributors */
package com.urbanairship.push.hms

import android.content.Context
import com.urbanairship.PendingResult
import com.urbanairship.UALog
import com.urbanairship.push.PushMessage
import com.urbanairship.push.PushProviderBridge
import java.util.concurrent.Future
import com.huawei.hms.push.RemoteMessage

/**
 * Airship HMS integration.
 */
public object AirshipHmsIntegration {

    /**
     * Called to handle [com.huawei.hms.push.HmsMessageService.onMessageReceived] The task should be finished
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
            .processPush(HmsPushProvider::class.java, PushMessage(message.dataOfMap))
            .execute(context) { pendingResult.setResult(null) }

        return pendingResult
    }

    /**
     * Called to handle [com.huawei.hms.push.HmsMessageService.onMessageReceived]} synchronously.
     *
     * @param context The application context.
     * @param message The message.
     */
    @JvmStatic
    public fun processMessageSync(context: Context, message: RemoteMessage) {
        PushProviderBridge
            .processPush(HmsPushProvider::class.java, PushMessage(message.dataOfMap))
            .executeSync(context)
    }

    /**
     * Called to handle new tokens.
     *
     * @param context The application context.
     * @param token The token.
     */
    @JvmStatic
    public fun processNewToken(context: Context, token: String?) {
        val existingToken = HmsTokenCache.shared()[context]
        if (token != existingToken) {
            HmsTokenCache.shared()[context] = token
            PushProviderBridge.requestRegistrationUpdate(context, HmsPushProvider::class.java, token)
        } else {
            UALog.d("Ignoring call to process new token. Token is already registered!")
        }
    }

    /**
     * Checks if the push is from Airship or not.
     *
     * @param message The message.
     * @return `true` if its from Airship, otherwise `false`.
     */
    public fun isAirshipPush(message: RemoteMessage): Boolean {
        return PushMessage(message.dataOfMap).isAirshipPush
    }
}
