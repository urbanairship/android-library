/* Copyright Airship and Contributors */

package com.urbanairship.push;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A broadcast receiver that handles notification intents.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class NotificationProxyReceiver extends BroadcastReceiver {

    private static final long ACTION_TIMEOUT_SECONDS = 10; // 10 seconds

    @Override
    public void onReceive(@NonNull final Context context, @Nullable final Intent intent) {
        Autopilot.automaticTakeOff(context);

        if (!UAirship.isTakingOff() && !UAirship.isFlying()) {
            Logger.error("NotificationProxyReceiver - unable to receive intent, takeOff not called.");
            return;
        }

        if (intent == null || intent.getAction() == null) {
            return;
        }

        Logger.verbose("NotificationProxyReceiver - Received intent: %s", intent.getAction());

        final PendingResult pendingResult = goAsync();

        try {
            Boolean result = new NotificationIntentProcessor(context, intent)
                    .process()
                    .get(ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            Logger.verbose("NotificationProxyReceiver - Finished processing notification intent with result %s.", result);

        } catch (InterruptedException | ExecutionException  e) {
            Logger.error(e, "NotificationProxyReceiver - Exception when processing notification intent.");
            Thread.currentThread().interrupt();
        } catch (TimeoutException e) {
            Logger.error("NotificationProxyReceiver - Application took too long to process notification intent.");
        }

        pendingResult.finish();
    }
}
