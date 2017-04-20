/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.RemoteInput;

import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionService;
import com.urbanairship.analytics.InteractiveNotificationEvent;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.PushMessage;
import com.urbanairship.push.iam.InAppMessage;
import com.urbanairship.push.iam.InAppMessageManager;
import com.urbanairship.push.iam.ResolutionEvent;
import com.urbanairship.util.UAStringUtil;


/**
 * This class is the core Broadcast Receiver for the Urban Airship library.
 */
public class CoreReceiver extends BroadcastReceiver {


    @Override
    public void onReceive(Context context, Intent intent) {
        Autopilot.automaticTakeOff(context);

        if (!UAirship.isTakingOff() && !UAirship.isFlying()) {
            Logger.error("CoreReceiver - unable to receive intent, takeOff not called.");
            return;
        }

        if (intent == null || intent.getAction() == null) {
            return;
        }

        Logger.verbose("CoreReceiver - Received intent: " + intent.getAction());

        switch (intent.getAction()) {
            case PushManager.ACTION_NOTIFICATION_OPENED_PROXY:
                onNotificationOpenedProxy(context, intent);
                break;
            case PushManager.ACTION_NOTIFICATION_BUTTON_OPENED_PROXY:
                onNotificationButtonOpenedProxy(context, intent);
                break;
            case PushManager.ACTION_NOTIFICATION_DISMISSED_PROXY:
                onNotificationDismissedProxy(context, intent);
                break;
            case PushManager.ACTION_NOTIFICATION_OPENED:
                onNotificationOpened(context, intent);
        }
    }

    /**
     * Handles the opened notification without an action.
     *
     * @param context The application context.
     * @param intent The notification intent.
     */
    private void onNotificationOpenedProxy(Context context, Intent intent) {
        PushMessage message = PushMessage.fromIntent(intent);
        if (message == null) {
            Logger.error("CoreReceiver - Intent is missing push message for: " + intent.getAction());
            return;
        }

        int notificationId = intent.getIntExtra(PushManager.EXTRA_NOTIFICATION_ID, -1);

        Logger.info("Notification opened ID: " + notificationId);

        // ConversionId needs to be the send id and not the push id, naming is hard.
        UAirship.shared().getAnalytics().setConversionSendId(message.getSendId());

        // Set the conversion send metadata.
        UAirship.shared().getAnalytics().setConversionMetadata(message.getMetadata());

        // Clear the in-app message if it matches the push send Id
        clearInAppMessage(message.getSendId());

        Intent openIntent = new Intent(PushManager.ACTION_NOTIFICATION_OPENED)
                .putExtras(intent.getExtras())
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
    private void onNotificationButtonOpenedProxy(Context context, Intent intent) {
        PushMessage message = PushMessage.fromIntent(intent);
        if (message == null) {
            Logger.error("CoreReceiver - Intent is missing push message for: " + intent.getAction());
            return;
        }

        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);

        String notificationActionId = intent.getStringExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID);
        if (notificationActionId == null) {
            Logger.error("CoreReceiver - Intent is missing notification button ID: " + intent.getAction());
            return;
        }

        int notificationId = intent.getIntExtra(PushManager.EXTRA_NOTIFICATION_ID, -1);
        boolean isForegroundAction = intent.getBooleanExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, true);
        String description = intent.getStringExtra(PushManager.EXTRA_NOTIFICATION_ACTION_BUTTON_DESCRIPTION);

        Logger.info("Notification opened ID: " + notificationId + " action button Id: " + notificationActionId);

        // Set the conversion push id and metadata
        if (isForegroundAction) {
            UAirship.shared().getAnalytics().setConversionSendId(message.getSendId());
            UAirship.shared().getAnalytics().setConversionMetadata(message.getMetadata());
        }

        // Clear the in-app message if it matches the push send Id
        clearInAppMessage(message.getSendId());

        // Dismiss the notification
        NotificationManagerCompat.from(context).cancel(notificationId);

        // Add the interactive notification event
        InteractiveNotificationEvent event = new InteractiveNotificationEvent(message, notificationActionId, description, isForegroundAction, remoteInput);
        UAirship.shared().getAnalytics().addEvent(event);

        Intent openIntent = new Intent(PushManager.ACTION_NOTIFICATION_OPENED)
                .putExtras(intent.getExtras())
                .setPackage(UAirship.getPackageName())
                .addCategory(UAirship.getPackageName());

        if (remoteInput != null && remoteInput.size() != 0) {
            openIntent.putExtra(AirshipReceiver.EXTRA_REMOTE_INPUT, remoteInput);
        }

        context.sendOrderedBroadcast(openIntent, UAirship.getUrbanAirshipPermission());
    }


    /**
     * Handles notification dismissed intent.
     *
     * @param context The application context.
     * @param intent The notification intent.
     */
    private void onNotificationDismissedProxy(Context context, Intent intent) {
        PushMessage message = PushMessage.fromIntent(intent);
        if (message == null) {
            Logger.error("CoreReceiver - Intent is missing push message for: " + intent.getAction());
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
                .putExtras(intent.getExtras())
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
    private void onNotificationOpened(Context context, Intent intent) {
        AirshipConfigOptions options = UAirship.shared().getAirshipConfigOptions();

        PushMessage message = PushMessage.fromIntent(intent);
        if (message == null) {
            Logger.error("CoreReceiver - Intent is missing push message for: " + intent.getAction());
            return;
        }

        if (intent.hasExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID)) {
            boolean isForeground = intent.getBooleanExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, false);
            String actionPayload = intent.getStringExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ACTIONS_PAYLOAD);

            if (isForeground && getResultCode() != AirshipReceiver.RESULT_ACTIVITY_LAUNCHED && options.autoLaunchApplication) {
                // Set the result if its an ordered broadcast
                if (launchApplication(context) && isOrderedBroadcast()) {
                    setResultCode(AirshipReceiver.RESULT_ACTIVITY_LAUNCHED);
                }
            }

            if (!UAStringUtil.isEmpty(actionPayload)) {
                // Run UA actions for the notification action
                Logger.debug("Running actions for notification action: " + actionPayload);

                @Action.Situation
                int situation = isForeground ? Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON : Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON;

                Bundle metadata = new Bundle();
                metadata.putParcelable(ActionArguments.PUSH_MESSAGE_METADATA, message);

                if (intent.hasExtra(AirshipReceiver.EXTRA_REMOTE_INPUT)) {
                    metadata.putBundle(ActionArguments.REMOTE_INPUT_METADATA, intent.getBundleExtra(AirshipReceiver.EXTRA_REMOTE_INPUT));
                }

                ActionService.runActions(context, actionPayload, situation, metadata);
            }

        } else {

            if (getResultCode() != AirshipReceiver.RESULT_ACTIVITY_LAUNCHED) {
                PendingIntent contentIntent = (PendingIntent) intent.getExtras().get(PushManager.EXTRA_NOTIFICATION_CONTENT_INTENT);
                if (contentIntent != null) {
                    try {
                        contentIntent.send();
                        if (isOrderedBroadcast()) {
                            setResultCode(AirshipReceiver.RESULT_ACTIVITY_LAUNCHED);
                        }
                    } catch (PendingIntent.CanceledException e) {
                        Logger.debug("Failed to send notification's contentIntent, already canceled.");
                    }
                } else if (options.autoLaunchApplication) {
                    // Set the result if its an ordered broadcast
                    if (launchApplication(context) && isOrderedBroadcast()) {
                        setResultCode(AirshipReceiver.RESULT_ACTIVITY_LAUNCHED);
                    }
                }
            }

            // Run any actions for the push
            Bundle metadata = new Bundle();
            metadata.putParcelable(ActionArguments.PUSH_MESSAGE_METADATA, message);

            ActionService.runActions(context, message.getActions(), Action.SITUATION_PUSH_OPENED, metadata);
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
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            Logger.info("Starting application's launch intent.");
            context.startActivity(launchIntent);
            return true;
        } else {
            Logger.info("Unable to launch application. Launch intent is unavailable.");
            return false;
        }
    }

    /**
     * Helper method to clear the pending in-app message and generate a resolution event if the
     * message is pending and currently not being displayed.
     *
     * @param messageId The message ID to clear.
     */
    private static void clearInAppMessage(String messageId) {
        if (UAStringUtil.isEmpty(messageId)) {
            return;
        }

        InAppMessageManager iamManager = UAirship.shared().getInAppMessageManager();
        InAppMessage pendingMessage = iamManager.getPendingMessage();
        InAppMessage currentMessage = iamManager.getCurrentMessage();

        // Only clear it if the messageId matches and the pending message is not currently showing
        if (pendingMessage != null && messageId.equals(pendingMessage.getId()) && !pendingMessage.equals(currentMessage)) {
            Logger.info("Clearing pending in-app message due to directly interacting with the message's push notification.");
            iamManager.setPendingMessage(null);

            // Direct open event
            ResolutionEvent resolutionEvent = ResolutionEvent.createDirectOpenResolutionEvent(pendingMessage);
            UAirship.shared().getAnalytics().addEvent(resolutionEvent);
        }
    }
}
