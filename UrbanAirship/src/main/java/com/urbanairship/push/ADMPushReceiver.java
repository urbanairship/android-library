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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.amazon.device.messaging.ADMConstants;
import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.util.UAStringUtil;

/**
 * ADMPushReceiver listens for incoming ADM registration responses and messages.
 */
public class ADMPushReceiver extends BroadcastReceiver {

    @SuppressLint("NewApi")
    @Override
    public void onReceive(final Context context, final Intent intent) {
        Autopilot.automaticTakeOff(context);

        if (intent == null || intent.getAction() == null) {
            return;
        }

        Logger.verbose("ADMPushReceiver - Received intent: " + intent.getAction());

        if (Build.VERSION.SDK_INT < 15) {
            Logger.error("ADMPushReceiver - Received intent from ADM transport on an unsupported API version.");
            return;
        }

        final PendingResult pendingResult = goAsync();

        UAirship.shared(new UAirship.OnReadyCallback() {
            @Override
            public void onAirshipReady(UAirship airship) {
                if (airship.getPlatformType() == UAirship.AMAZON_PLATFORM) {
                    switch (intent.getAction()) {
                        case ADMConstants.LowLevel.ACTION_RECEIVE_ADM_MESSAGE:
                            handleADMReceivedIntent(airship, context, intent);
                            break;
                        case ADMConstants.LowLevel.ACTION_APP_REGISTRATION_EVENT:
                            handleRegistrationIntent(airship, context, intent);
                            break;
                    }
                } else {
                    Logger.error("ADMPushReceiver - Received intent from invalid transport acting as ADM.");
                }

                pendingResult.finish();
            }
        });
    }

    /**
     * Handles ADM registration intent.
     * @param airship The airship instance.
     * @param context The application context.
     * @param intent The registration intent.
     */
    private void handleRegistrationIntent(UAirship airship, Context context, Intent intent) {
        if (intent.hasExtra(ADMConstants.LowLevel.EXTRA_ERROR)) {
            Logger.error("ADM error occurred: " + intent.getStringExtra(ADMConstants.LowLevel.EXTRA_ERROR));
        } else {
            String registrationID = intent.getStringExtra(ADMConstants.LowLevel.EXTRA_REGISTRATION_ID);
            if (registrationID != null) {
                Logger.info("ADM registration successful. Registration ID: " + registrationID);
                airship.getPushManager().setAdmId(registrationID);
            }
        }

        Intent finishIntent = new Intent(PushService.ACTION_PUSH_REGISTRATION_FINISHED);
        PushService.startServiceWithWakeLock(context, finishIntent);
    }

    /**
     * Handles ADM push intent.
     * @param airship The airship instance.
     * @param context The application context.
     * @param intent The push intent.
     */
    private void handleADMReceivedIntent(UAirship airship, Context context, Intent intent) {
        Logger.debug("ADMPushReceiver - Received push: " + intent);

        if (UAStringUtil.isEmpty(airship.getPushManager().getAdmId())) {
            Logger.error("ADMPushReceiver - Received intent from ADM without registering.");
            return;
        }

        // Deliver message to push service
        Intent pushIntent = new Intent(PushService.ACTION_PUSH_RECEIVED).putExtras(intent.getExtras());
        PushService.startServiceWithWakeLock(context, pushIntent);
    }
}
