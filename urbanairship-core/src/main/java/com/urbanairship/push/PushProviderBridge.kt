/* Copyright Airship and Contributors */
package com.urbanairship.push

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.WorkerThread
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import com.urbanairship.Airship
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * [PushProvider] callback methods.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object PushProviderBridge {

    public const val EXTRA_PROVIDER_CLASS: String = "EXTRA_PROVIDER_CLASS"
    public const val EXTRA_PUSH: String = "EXTRA_PUSH"

    /**
     * Triggers a registration update.
     *
     * @param context The application context.
     * @param pushProviderClass The push provider class.
     * @param newToken The token.
     */
    public fun requestRegistrationUpdate(
        context: Context,
        pushProviderClass: Class<out PushProvider>,
        newToken: String?
    ) {
        Autopilot.automaticTakeOff(context)
        if (Airship.isFlying || Airship.isTakingOff) {
            Airship.onReady {
                push.onTokenChanged(pushProviderClass, newToken)
            }
        }
    }

    /**
     * Creates a new request to process an incoming push message.
     *
     * @param provider The provider's class.
     * @param pushMessage The push message.
     */
    @WorkerThread
    public fun processPush(
        provider: Class<out PushProvider>,
        pushMessage: PushMessage
    ): ProcessPushRequest = ProcessPushRequest(provider, pushMessage)

    /**
     * Process push request.
     */
    public class ProcessPushRequest internal constructor(
        private val provider: Class<out PushProvider>,
        private val pushMessage: PushMessage
    ) {

        private var maxCallbackWaitTime: Long = 0

        /**
         * Sets the max callback wait time in milliseconds.
         *
         * @param milliseconds The max callback wait time. If <= 0, the callback will
         * wait until the push request is completed.
         * @return The process push request.
         */
        public fun setMaxCallbackWaitTime(milliseconds: Long): ProcessPushRequest {
            return this.also { it.maxCallbackWaitTime = milliseconds }
        }

        /**
         * Executes the request.
         *
         * @param context The application context.
         * @param callback The callback.
         */
        public fun execute(context: Context, callback: Runnable? = null) {
            val pushRunnableBuilder = IncomingPushRunnable.Builder(context)
                .setMessage(pushMessage)
                .setProviderClass(provider.toString())

            val future = PushManager.PUSH_EXECUTOR.submit(pushRunnableBuilder.build())

            try {
                if (maxCallbackWaitTime > 0) {
                    future[maxCallbackWaitTime, TimeUnit.MILLISECONDS]
                } else {
                    future.get()
                }
            } catch (e: TimeoutException) {
                UALog.e("Application took too long to process push. App may get closed.")
            } catch (e: Exception) {
                UALog.e(e, "Failed to wait for notification")
            }

            callback?.run()
        }

        /**
         * Executes the request synchronously.
         *
         * @param context The application context.
         */
        @WorkerThread
        public fun executeSync(context: Context) {
            val countDownLatch = CountDownLatch(1)
            execute(context) { countDownLatch.countDown() }

            try {
                countDownLatch.await()
            } catch (e: InterruptedException) {
                UALog.e(e, "Failed to wait for push.")
                Thread.currentThread().interrupt()
            }
        }
    }
}
