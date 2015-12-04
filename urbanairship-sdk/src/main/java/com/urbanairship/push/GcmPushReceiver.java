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

import android.content.Context;
import android.content.Intent;

import com.google.android.gms.gcm.GcmReceiver;
import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;

/**
 * WakefulBroadcastReceiver that receives GCM messages and delivers them to both the application-specific GcmListenerService subclass,
 * and Urban Airship's PushService.
 */
public class GcmPushReceiver extends GcmReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        Autopilot.automaticTakeOff(context);

        // CE-1574: Security exception is sometimes thrown when the GcmReceiver's onReceive tries to start
        // the instance ID service
        try {
            super.onReceive(context, intent);
        } catch (SecurityException e) {
            Logger.error("Received security exception from GcmReceiver: ", e);

            if (!GcmConstants.ACTION_GCM_RECEIVE.equals(intent.getAction())) {
                // Trying to do any further registrations with GCM leads to a SecurityException - bad process.
                // Lets assume the token was trying to be refreshed, so lets clear the token to force
                // it to be regenerated on next app start and hope GCM is in a better spot.
                UAirship.shared().getPushManager().setGcmToken(null);

                return;
            }
        }

        Logger.verbose("GcmPushReceiver - Received intent: " + intent.getAction());
        if (GcmConstants.ACTION_GCM_RECEIVE.equals(intent.getAction())) {
            Intent pushIntent = new Intent(context, PushService.class)
                    .setAction(PushService.ACTION_RECEIVE_GCM_MESSAGE)
                    .putExtra(PushService.EXTRA_INTENT, intent);

            startWakefulService(context, pushIntent);
        }
    }
}
