/* Copyright 2016 Urban Airship and Contributors */

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
 * WakefulBroadcastReceiver that receives GCM messages for Urban Airship.
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

        switch (intent.getAction()) {
            case GcmConstants.ACTION_GCM_RECEIVE:
                Intent pushIntent = new Intent(context, PushService.class)
                        .setAction(PushService.ACTION_RECEIVE_GCM_MESSAGE)
                        .putExtra(PushService.EXTRA_INTENT, intent);

                startWakefulService(context, pushIntent);

                if (this.isOrderedBroadcast()) {
                    this.setResultCode(Activity.RESULT_OK);
                }

                break;

            case GcmConstants.ACTION_INSTANCE_ID:
                startInstanceIdService(context, intent);

                break;

            case GcmConstants.ACTION_GCM_REGISTRATION:

                // When we detect a GCM registration, make sure our GCM token is up-to-date.
                Intent registrationIntent = new Intent(context, PushService.class)
                        .setAction(PushService.ACTION_UPDATE_PUSH_REGISTRATION);

                startWakefulService(context, registrationIntent);
                break;
        }
    }


    /**
     * Starts the application's InstanceIDListenerService.
     *
     * @param context The application's context.
     * @param intent The application's intent.
     */
    private void startInstanceIdService(Context context, Intent intent) {
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
    }

    /**
     * Normalizes the intent based on the GcmReceiver logic.
     *
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
        if ("google.com/iid".equals(from) || "gcm.googleapis.com/refresh".equals(from)) {
            intent.setAction("com.google.android.gms.iid.InstanceID");
        }
    }
}
