/* Copyright Airship and Contributors */

package com.urbanairship.push.adm;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amazon.device.messaging.ADMConstants;
import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.push.PushMessage;
import com.urbanairship.push.PushProviderBridge;

/**
 * AdmPushReceiver listens for incoming ADM registration responses and messages.
 */
public class AdmPushReceiver extends BroadcastReceiver {

    /**
     * Amount of time in milliseconds a broadcast receiver has to process an intent.
     */
    private static final long BROADCAST_INTENT_TIME_MS = 10000;

    @Override
    public void onReceive(@NonNull final Context context, @Nullable final Intent intent) {
        Autopilot.automaticTakeOff(context);

        if (intent == null || intent.getExtras() == null || !ADMConstants.LowLevel.ACTION_RECEIVE_ADM_MESSAGE.equals(intent.getAction())) {
            if (isOrderedBroadcast()) {
                setResultCode(Activity.RESULT_OK);
            }
            return;
        }

        final boolean isOrderedBroadcast = isOrderedBroadcast();
        final PendingResult result = goAsync();
        final PushMessage message = new PushMessage(intent.getExtras());

        Logger.verbose("AdmPushReceiver - Received push.");

        PushProviderBridge.processPush(AdmPushProvider.class, message)
                          .setMaxCallbackWaitTime(BROADCAST_INTENT_TIME_MS)
                          .execute(context, new Runnable() {
                              @Override
                              public void run() {
                                  if (result == null) {
                                      return;
                                  }

                                  if (isOrderedBroadcast) {
                                      result.setResultCode(Activity.RESULT_OK);
                                  }
                                  result.finish();
                              }
                          });
    }

}
