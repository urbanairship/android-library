/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.push;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.util.UAStringUtil;

/**
 * GCMPushReceiver listens for incoming GCM registration responses and messages, then forwards them
 * to the PushService.
 */
public class GCMPushReceiver extends BroadcastReceiver {

    @SuppressLint("NewApi")
    @Override
    public void onReceive(final Context context, final Intent intent) {
        Autopilot.automaticTakeOff(context);

        Logger.verbose("GCMPushReceiver - Received intent: " + intent.getAction());

        if (GCMConstants.ACTION_GCM_RECEIVE.equals(intent.getAction())) {
            if (Build.VERSION.SDK_INT >= 11) {
                final PendingResult pendingResult = goAsync();
                if (isOrderedBroadcast()) {
                    pendingResult.setResultCode(Activity.RESULT_OK);
                }

                UAirship.shared(new UAirship.OnReadyCallback() {
                    @Override
                    public void onAirshipReady(UAirship airship) {
                        handleGCMReceived(airship, context, intent);
                        pendingResult.finish();
                    }
                });
            } else {
                handleGCMReceived(UAirship.shared(), context, intent);

                if (isOrderedBroadcast()) {
                    setResultCode(Activity.RESULT_OK);
                }
            }
        }
    }

    /**
     * Handles the incoming GCM Message.
     * @param airship The airship instance.
     * @param context The application context.
     * @param intent The incoming intent.
     */
    private void handleGCMReceived(UAirship airship, Context context, Intent intent) {
        if (airship.getPlatformType() != UAirship.ANDROID_PLATFORM) {
            Logger.error("GCMPushReceiver - Received intent from invalid transport acting as GCM.");
            return;
        }

        if (UAStringUtil.isEmpty(airship.getPushManager().getGcmId())) {
            Logger.error("GCMPushReceiver - Received intent from GCM without registering.");
            return;
        }

        String sender = intent.getStringExtra("from");
        if (sender != null && !sender.equals(airship.getAirshipConfigOptions().gcmSender)) {
            Logger.info("Ignoring GCM message from sender: " + sender);
            return;
        }

        if (GCMConstants.GCM_DELETED_MESSAGES_VALUE.equals(intent.getStringExtra(GCMConstants.EXTRA_GCM_MESSAGE_TYPE))) {
            Logger.info("GCM deleted " + intent.getStringExtra(GCMConstants.EXTRA_GCM_TOTAL_DELETED) + " pending messages.");
        } else {
            Logger.debug("GCMPushReceiver - Received push: " + intent);

            // Deliver message to push service
            Intent pushIntent = new Intent(PushService.ACTION_PUSH_RECEIVED)
                    .putExtras(intent.getExtras());

            PushService.startServiceWithWakeLock(context, pushIntent);
        }
    }
}
