/* Copyright Airship and Contributors */

package com.urbanairship.push;

import android.content.Context;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

/**
 * {@link PushProvider} callback methods.
 *
 * @hide
 */
public abstract class PushProviderBridge {

    final static String EXTRA_PROVIDER_CLASS = "EXTRA_PROVIDER_CLASS";
    final static String EXTRA_PUSH = "EXTRA_PUSH";

    /**
     * Triggers a registration update.
     *
     * @param context The application context.
     * @param pushProviderClass The push provider class.
     * @param newToken The token.
     */
    public static void requestRegistrationUpdate(@NonNull Context context, @NonNull final Class<? extends PushProvider> pushProviderClass, @Nullable final String newToken) {
        Autopilot.automaticTakeOff(context);
        if (UAirship.isFlying() || UAirship.isTakingOff()) {
            UAirship.shared(new UAirship.OnReadyCallback() {
                @Override
                public void onAirshipReady(@NonNull UAirship airship) {
                    airship.getPushManager().onTokenChanged(pushProviderClass, newToken);
                }
            });
        }
    }

    /**
     * Triggers a registration update.
     *
     * @param context The application context.
     * @deprecated Use {@link #requestRegistrationUpdate(Context, Class, String)} instead.
     */
    @Deprecated
    public static void requestRegistrationUpdate(@NonNull Context context) {
        Autopilot.automaticTakeOff(context);

        if (UAirship.isFlying() || UAirship.isTakingOff()) {
            UAirship.shared(new UAirship.OnReadyCallback() {
                @Override
                public void onAirshipReady(@NonNull UAirship airship) {
                    airship.getPushManager().onTokenChanged(null, null);
                }
            });
        }
    }

    /**
     * Creates a new request to process an incoming push message.
     *
     * @param provider The provider's class.
     * @param pushMessage The push message.
     */
    @WorkerThread
    @NonNull
    public static ProcessPushRequest processPush(@NonNull Class<? extends PushProvider> provider, @NonNull PushMessage pushMessage) {
        return new ProcessPushRequest(provider, pushMessage);
    }

    /**
     * Process push request.
     */
    public static class ProcessPushRequest {

        private final Class<? extends PushProvider> provider;
        private final PushMessage pushMessage;
        private long maxCallbackWaitTime;

        private ProcessPushRequest(@NonNull Class<? extends PushProvider> provider, @NonNull PushMessage pushMessage) {
            this.provider = provider;
            this.pushMessage = pushMessage;
        }

        /**
         * Sets the max callback wait time in milliseconds.
         *
         * @param milliseconds The max callback wait time. If <= 0, the callback will
         * wait until the push request is completed.
         * @return The process push request.
         */
        @NonNull
        public ProcessPushRequest setMaxCallbackWaitTime(long milliseconds) {
            this.maxCallbackWaitTime = milliseconds;
            return this;
        }

        /**
         * Executes the request.
         *
         * @param context The application context.
         */
        public void execute(@NonNull Context context) {
            execute(context, null);
        }

        /**
         * Executes the request.
         *
         * @param context The application context.
         * @param callback The callback.
         */
        public void execute(@NonNull Context context, @Nullable final Runnable callback) {
            IncomingPushRunnable.Builder pushRunnableBuilder = new IncomingPushRunnable.Builder(context)
                    .setMessage(pushMessage)
                    .setProviderClass(provider.toString());

            Future<?> future = PushManager.PUSH_EXECUTOR.submit(pushRunnableBuilder.build());

            try {
                if (maxCallbackWaitTime > 0) {
                    future.get(maxCallbackWaitTime, TimeUnit.MILLISECONDS);
                } else {
                    future.get();
                }
            } catch (TimeoutException e) {
                Logger.error("Application took too long to process push. App may get closed.");
            } catch (Exception e) {
                Logger.error(e, "Failed to wait for notification");
            }

            if (callback != null) {
                callback.run();
            }

        }

        /**
         * Executes the request synchronously.
         *
         * @param context The application context.
         */
        @WorkerThread
        public void executeSync(@NonNull Context context) {
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            execute(context, new Runnable() {
                @Override
                public void run() {
                    countDownLatch.countDown();
                }
            });

            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                Logger.error(e, "Failed to wait for push.");
                Thread.currentThread().interrupt();
            }
        }

    }

}
