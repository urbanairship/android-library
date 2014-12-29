/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

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

package com.urbanairship;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationManagerCompat;

import com.urbanairship.actions.ActionService;
import com.urbanairship.actions.Situation;
import com.urbanairship.analytics.InteractiveNotificationEvent;
import com.urbanairship.push.BaseIntentReceiver;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.UAStringUtil;


/**
 * This class is the core Broadcast Receiver for the Urban Airship library.
 */
public class CoreReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Autopilot.automaticTakeOff(context);

        Logger.verbose("CoreReceiver - Received intent: " + intent.getAction());

        switch (intent.getAction()) {
            case PushManager.ACTION_NOTIFICATION_OPENED_PROXY:
                handleNotificationOpenedProxy(context, intent);
                break;
            case PushManager.ACTION_NOTIFICATION_BUTTON_OPENED_PROXY:
                handleNotificationButtonOpenedProxy(context, intent);
                break;
            case PushManager.ACTION_NOTIFICATION_DISMISSED_PROXY:
                handleNotificationDismissedProxy(context, intent);
                break;
            case PushManager.ACTION_NOTIFICATION_OPENED:
                handleNotificationOpened(context, intent);
                break;
        }
    }

    /**
     * Handles the opened notification without an action.
     *
     * @param context The application context.
     * @param intent The notification intent.
     */
    static void handleNotificationOpenedProxy(Context context, Intent intent) {
        Bundle pushBundle = intent.getBundleExtra(PushManager.EXTRA_PUSH_BUNDLE);
        if (pushBundle == null) {
            Logger.error("CoreReceiver - Intent is missing push bundle for: " + intent.getAction());
            return;
        }

        PushMessage message = new PushMessage(pushBundle);
        int notificationId = intent.getIntExtra(PushManager.EXTRA_NOTIFICATION_ID, -1);

        Logger.info("Notification opened ID: " + notificationId);

        // ConversionId needs to be the send id and not the push id, naming is hard.
        UAirship.shared().getAnalytics().setConversionSendId(message.getSendId());

        PendingIntent contentIntent = (PendingIntent) intent.getExtras().get(PushManager.EXTRA_NOTIFICATION_CONTENT_INTENT);
        if (contentIntent != null) {
            try {
                contentIntent.send();
            } catch (PendingIntent.CanceledException e) {
                Logger.debug("Failed to send notification's contentIntent, already canceled.");
            }
        }

        Intent openIntent = new Intent(PushManager.ACTION_NOTIFICATION_OPENED)
                .putExtra(PushManager.EXTRA_PUSH_BUNDLE, message.getPushBundle())
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, notificationId)
                .setPackage(UAirship.getPackageName())
                .addCategory(UAirship.getPackageName());

        context.sendOrderedBroadcast(openIntent, UAirship.getUrbanAirshipPermission());
    }

    /**
     * Handles the opened notification button.
     *
     * @param context The application context.
     * @param intent The notification intent.
     */
    static void handleNotificationButtonOpenedProxy(Context context, Intent intent) {
        Bundle pushBundle = intent.getBundleExtra(PushManager.EXTRA_PUSH_BUNDLE);
        if (pushBundle == null) {
            Logger.error("CoreReceiver - Intent is missing push bundle for: " + intent.getAction());
            return;
        }

        String notificationActionId = intent.getStringExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID);
        if (notificationActionId == null) {
            Logger.error("CoreReceiver - Intent is missing notification button ID: " + intent.getAction());
            return;
        }

        PushMessage message = new PushMessage(pushBundle);
        int notificationId = intent.getIntExtra(PushManager.EXTRA_NOTIFICATION_ID, -1);
        boolean isForegroundAction = intent.getBooleanExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, true);
        String actionPayload = intent.getStringExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ACTIONS_PAYLOAD);
        String description = intent.getStringExtra(PushManager.EXTRA_NOTIFICATION_ACTION_BUTTON_DESCRIPTION);

        Logger.info("Notification opened ID: " + notificationId + " action button Id: " + notificationActionId);

        // Set the conversion push id
        if (isForegroundAction) {
            UAirship.shared().getAnalytics().setConversionSendId(message.getSendId());
        }

        // Dismiss the notification
        NotificationManagerCompat.from(context).cancel(notificationId);

        // Add the interactive notification event
        InteractiveNotificationEvent event = new InteractiveNotificationEvent(message, notificationActionId, description, isForegroundAction);
        UAirship.shared().getAnalytics().addEvent(event);

        Intent openIntent = new Intent(PushManager.ACTION_NOTIFICATION_OPENED)
                .putExtra(PushManager.EXTRA_PUSH_BUNDLE, message.getPushBundle())
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID, notificationActionId)
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, isForegroundAction)
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ACTIONS_PAYLOAD, actionPayload)
                .setPackage(UAirship.getPackageName())
                .addCategory(UAirship.getPackageName());

        context.sendOrderedBroadcast(openIntent, UAirship.getUrbanAirshipPermission());
    }


    /**
     * Handles notification dismissed intent.
     *
     * @param context The application context.
     * @param intent The notification intent.
     */
    private void handleNotificationDismissedProxy(Context context, Intent intent) {
        Bundle pushBundle = intent.getBundleExtra(PushManager.EXTRA_PUSH_BUNDLE);
        if (pushBundle == null) {
            Logger.error("CoreReceiver - Intent is missing push bundle for: " + intent.getAction());
            return;
        }

        int notificationId = intent.getIntExtra(PushManager.EXTRA_NOTIFICATION_ID, -1);

        Logger.info("Notification dismissed ID: " + notificationId);


        PendingIntent deleteIntent = (PendingIntent) intent.getExtras().get(PushManager.EXTRA_NOTIFICATION_DELETE_INTENT);
        if (deleteIntent != null) {
            try {
                deleteIntent.send();
            } catch (PendingIntent.CanceledException e) {
                Logger.debug("Failed to send notification's deleteIntent, already canceled.");
            }
        }

        Intent dismissIntent = new Intent(PushManager.ACTION_NOTIFICATION_DISMISSED)
                .putExtra(PushManager.EXTRA_PUSH_BUNDLE, pushBundle)
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, notificationId)
                .setPackage(UAirship.getPackageName())
                .addCategory(UAirship.getPackageName());

        context.sendOrderedBroadcast(dismissIntent, UAirship.getUrbanAirshipPermission());
    }

    /**
     * Handles notification opened intent.
     *
     * @param context The application context.
     * @param intent The notification intent.
     */
    private void handleNotificationOpened(Context context, Intent intent) {
        AirshipConfigOptions options = UAirship.shared().getAirshipConfigOptions();

        Bundle pushBundle = intent.getBundleExtra(PushManager.EXTRA_PUSH_BUNDLE);
        if (pushBundle == null) {
            Logger.error("CoreReceiver - Intent is missing push bundle for: " + intent.getAction());
            return;
        }

        PushMessage message = new PushMessage(pushBundle);

        if (intent.hasExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID)) {
            boolean isForeground = intent.getBooleanExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, false);
            String actionPayload = intent.getStringExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ACTIONS_PAYLOAD);

            if (isForeground && getResultCode() != BaseIntentReceiver.RESULT_ACTIVITY_LAUNCHED && options.autoLaunchApplication) {
                // Set the result if its an ordered broadcast
                if (launchApplication(context) && isOrderedBroadcast()) {
                    setResultCode(BaseIntentReceiver.RESULT_ACTIVITY_LAUNCHED);
                }
            }

            if (!UAStringUtil.isEmpty(actionPayload)) {
                // Run UA actions for the notification action
                Logger.debug("Running actions for notification action: " + actionPayload);

                Situation situation = isForeground ? Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON : Situation.BACKGROUND_NOTIFICATION_ACTION_BUTTON;
                ActionService.runActionsPayload(context, actionPayload, situation, message);
            }

        } else {

            if (getResultCode() != BaseIntentReceiver.RESULT_ACTIVITY_LAUNCHED && options.autoLaunchApplication) {
                // Set the result if its an ordered broadcast
                if (launchApplication(context) && isOrderedBroadcast()) {
                    setResultCode(BaseIntentReceiver.RESULT_ACTIVITY_LAUNCHED);
                }
            }

            ActionService.runActionsPayload(context, message.getActionsPayload(), Situation.PUSH_OPENED, message);
        }
    }

    /**
     * Helper method that attempts to launch the application's launch intent.
     *
     * @param context The application context.
     */
    private boolean launchApplication(Context context) {
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(UAirship.getPackageName());
        if (launchIntent != null) {
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Logger.info("Starting application's launch intent.");
            context.startActivity(launchIntent);
            return true;
        } else {
            Logger.info("Unable to launch application. Launch intent is unavailable.");
            return false;
        }
    }
}
