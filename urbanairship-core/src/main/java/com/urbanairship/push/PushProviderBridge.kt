/* Copyright Airship and Contributors */

package com.urbanairship.push

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.WorkerThread
import com.urbanairship.Airship
import com.urbanairship.AirshipDispatchers
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * [PushProvider] callback methods. Low level push provider bridge. Most apps should not call this directly.
 * Instead, use the specific provider integrations (e.g. `AirshipFirebaseIntegration`, `AirshipHmsIntegration`, or `AdmHandlerBase`) when trying to extend a service and call through to us.
 */
public object PushProviderBridge {

    public const val EXTRA_PROVIDER_CLASS: String = "EXTRA_PROVIDER_CLASS"
    public const val EXTRA_PUSH: String = "EXTRA_PUSH"

    private val scope = CoroutineScope(AirshipDispatchers.IO + SupervisorJob())

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

        private var maxCallbackWaitTime: Duration = Duration.ZERO

        /**
         * Sets the max callback wait time.
         *
         * @param duration The max callback wait time. If not positive, the callback will
         * wait until the push request is completed.
         * @return The process push request.
         */
        public fun setMaxCallbackWaitTime(duration: Duration): ProcessPushRequest {
            return this.also { it.maxCallbackWaitTime = duration }
        }

        /**
         * Sets the max callback wait time in milliseconds.
         *
         * Provided for Java callers, which cannot express a [Duration]. Named separately
         * rather than overloading [setMaxCallbackWaitTime] so that a Kotlin caller cannot
         * pass a bare number and have it mean milliseconds by convention.
         *
         * @param milliseconds The max callback wait time. If <= 0, the callback will
         * wait until the push request is completed.
         * @return The process push request.
         */
        public fun setMaxCallbackWaitTimeMs(milliseconds: Long): ProcessPushRequest =
            setMaxCallbackWaitTime(milliseconds.milliseconds)

        /**
         * Executes the request.
         *
         * @param context The application context.
         * @param callback The callback.
         */
        public fun execute(context: Context, callback: Runnable? = null) {
            scope.launch {
                try {
                    execute(context)
                } finally {
                    callback?.run()
                }
            }
        }

        /**
         * Executes the request.
         *
         * @param context The application context.
         */
        public suspend fun execute(context: Context) {
            val pushJob = IncomingPushRunnable.Builder(context)
                .setMessage(pushMessage)
                .setProviderClass(provider.toString())
                .build()

            try {
                if (maxCallbackWaitTime > Duration.ZERO) {
                    withTimeout(maxCallbackWaitTime) {
                        pushJob.run()
                    }
                } else {
                    pushJob.run()
                }
            } catch (e: TimeoutException) {
                UALog.e(e, "Application took too long to process push. App may get closed.")
            } catch (e: Exception) {
                UALog.e(e, "Failed to wait for notification")
            }
        }

        /**
         * Executes the request synchronously.
         *
         * @param context The application context.
         */
        @WorkerThread
        public fun executeSync(context: Context) {
            runBlocking {
                execute(context)
            }
        }
    }
}
