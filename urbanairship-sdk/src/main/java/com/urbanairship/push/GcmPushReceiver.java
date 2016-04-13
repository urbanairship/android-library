/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Base64;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;

/**
 * WakefulBroadcastReceiver that receives GCM messages and delivers them to both the application-specific GcmListenerService subclass,
 * and Urban Airship's PushService.
 */
public class GcmPushReceiver extends WakefulBroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        Autopilot.automaticTakeOff(context);

        Logger.verbose("GcmPushReceiver - Received intent: " + intent.getAction());

        normalizeIntent(context, intent);

        if (intent.getAction() == null) {
            if (this.isOrderedBroadcast()) {
                this.setResultCode(Activity.RESULT_CANCELED);
            }
            return;
        }

        // Send the intent to the InstanceIdService or the GcmIntentService
        try {
            ComponentName componentName = startWakefulService(context, intent);
            if (isOrderedBroadcast()) {
                setResultCode(componentName == null ? 404 : Activity.RESULT_OK);
            }
        } catch (SecurityException e) {
            Logger.error("GcmPushReceiver - Error while delivering the message to the serviceIntent", e);
            if (this.isOrderedBroadcast()) {
                this.setResultCode(401);
            }
        }

        // Forward any GCM Receive messages to Push Service
        if (GcmConstants.ACTION_GCM_RECEIVE.equals(intent.getAction())) {
            Intent pushIntent = new Intent(context, PushService.class)
                    .setAction(PushService.ACTION_RECEIVE_GCM_MESSAGE)
                    .putExtra(PushService.EXTRA_INTENT, intent);

            startWakefulService(context, pushIntent);

            if (this.isOrderedBroadcast()) {
                this.setResultCode(Activity.RESULT_OK);
            }
        }
    }

    /**
     * Normalizes the intent based on the GcmReceiver logic.
     * @param context The application context.
     * @param intent The intent.
     */
    private void normalizeIntent(Context context, Intent intent) {
        // Clear the component
        intent.setComponent(null);

        // Set the package name
        intent.setPackage(context.getPackageName());

        // Remove the category on pre kitkat devices
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            intent.removeCategory(context.getPackageName());
        }

        // Decode the gcm data if its base 64 encoded
        String encodedData = intent.getStringExtra("gcm.rawData64");
        if (encodedData != null) {
            intent.putExtra("rawData", Base64.decode(encodedData, 0));
            intent.removeExtra("gcm.rawData64");
        }

        // Registration, iid, and refresh needs to start the InstanceID service.
        String from = intent.getStringExtra("from");
        if ("com.google.android.c2dm.intent.REGISTRATION".equals(intent.getAction()) || "google.com/iid".equals(from) || "gcm.googleapis.com/refresh".equals(from)) {
            intent.setAction("com.google.android.gms.iid.InstanceID");
        }

        // Try to set the service class name
        ResolveInfo resolveInfo = context.getPackageManager().resolveService(intent, 0);
        if (resolveInfo != null && resolveInfo.serviceInfo != null) {
            ServiceInfo serviceInfo = resolveInfo.serviceInfo;
            if (context.getPackageName().equals(serviceInfo.packageName) && serviceInfo.name != null) {
                String serviceName = serviceInfo.name;
                serviceName = serviceName.startsWith(".") ? context.getPackageName() + serviceName : serviceName;

                Logger.debug("GcmPushReceiver - Forwarding GCM intent to " + serviceName);
                intent.setClassName(context.getPackageName(), serviceName);
            } else {
                Logger.error("GcmPushReceiver - Error resolving target intent service, skipping classname enforcement. Resolved service was: " + serviceInfo.packageName + "/" + serviceInfo.name);
            }
        }
    }
}
