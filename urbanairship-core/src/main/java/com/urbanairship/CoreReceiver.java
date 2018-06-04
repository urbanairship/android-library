/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.RemoteInput;

import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.ActionService;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.analytics.InteractiveNotificationEvent;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.UAStringUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


/**
 * This class is the core Broadcast Receiver for the Urban Airship library.
 */
public class CoreReceiver extends BroadcastReceiver {

    private Executor executor;

    public CoreReceiver() {
        this(Executors.newSingleThreadExecutor());
    }

    @VisibleForTesting
    public CoreReceiver(@NonNull Executor executor) {
        super();
        this.executor = executor;
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
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
                break;
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

        // Notify the legacy in-app message manager about the push
        UAirship.shared().getLegacyInAppMessageManager().onPushResponse(message);

        Intent openIntent = new Intent(PushManager.ACTION_NOTIFICATION_OPENED)
                .putExtras(intent.getExtras())
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .setPackage(UAirship.getPackageName())
                .addCategory(UAirship.getPackageName());

        context.sendOrderedBroadcast(openIntent, null);
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

        // Notify the legacy in-app message manager about the push
        UAirship.shared().getLegacyInAppMessageManager().onPushResponse(message);

        // Dismiss the notification
        NotificationManagerCompat.from(context).cancel(notificationId);

        // Add the interactive notification event
        InteractiveNotificationEvent event = new InteractiveNotificationEvent(message, notificationActionId, description, isForegroundAction, remoteInput);
        UAirship.shared().getAnalytics().addEvent(event);

        Intent openIntent = new Intent(PushManager.ACTION_NOTIFICATION_OPENED)
                .putExtras(intent.getExtras())
                .setPackage(UAirship.getPackageName())
                .addCategory(UAirship.getPackageName());

        if (isForegroundAction) {
            openIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        }

        if (remoteInput != null && remoteInput.size() != 0) {
            openIntent.putExtra(AirshipReceiver.EXTRA_REMOTE_INPUT, remoteInput);
        }

        context.sendOrderedBroadcast(openIntent, null);
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

        context.sendOrderedBroadcast(dismissIntent, null);
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

        boolean shouldLaunchApplication = false;
        Map<String, ActionValue> actionMap = null;
        @Action.Situation int actionSituation = 0;
        Bundle actionMetadata = new Bundle();

        if (intent.hasExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID)) {
            boolean isForeground = intent.getBooleanExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, false);

            if (isForeground && getResultCode() != AirshipReceiver.RESULT_ACTIVITY_LAUNCHED && options.autoLaunchApplication) {
                shouldLaunchApplication = true;
            }

            if (intent.hasExtra(AirshipReceiver.EXTRA_REMOTE_INPUT)) {
                actionMetadata.putBundle(ActionArguments.REMOTE_INPUT_METADATA, intent.getBundleExtra(AirshipReceiver.EXTRA_REMOTE_INPUT));
            }

            String actionPayload = intent.getStringExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ACTIONS_PAYLOAD);
            if (!UAStringUtil.isEmpty(actionPayload)) {
                actionMap = parseActionValues(actionPayload);
            }
        } else {
            actionMap = message.getActions();
            actionSituation = Action.SITUATION_PUSH_OPENED;

            if (getResultCode() != AirshipReceiver.RESULT_ACTIVITY_LAUNCHED) {
                PendingIntent contentIntent = (PendingIntent) intent.getExtras().get(PushManager.EXTRA_NOTIFICATION_CONTENT_INTENT);
                if (contentIntent != null) {
                    try {
                        contentIntent.send();
                    } catch (PendingIntent.CanceledException e) {
                        Logger.debug("Failed to send notification's contentIntent, already canceled.");
                    }
                } else if (options.autoLaunchApplication) {
                    shouldLaunchApplication = true;
                }
            }
        }

        actionMetadata.putParcelable(ActionArguments.PUSH_MESSAGE_METADATA, message);

        final int result;
        if (shouldLaunchApplication && launchApplication(context)) {
            result = AirshipReceiver.RESULT_ACTIVITY_LAUNCHED;
        } else {
            result = AirshipReceiver.RESULT_ACTIVITY_NOT_LAUNCHED;
        }

        final boolean isOrderedBroadcast = isOrderedBroadcast();
        final PendingResult pendingResult = goAsync();
        if (isOrderedBroadcast && pendingResult != null) {
            pendingResult.setResultCode(result);
        }

        runActions(context, actionMap, actionSituation, actionMetadata, new Runnable() {
            @Override
            public void run() {
                if (pendingResult != null) {
                    pendingResult.finish();
                }
            }
        });
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
     * Helper method to run the actions.
     *
     * @param context The context.
     * @param actions The actions payload.
     * @param situation The situation.
     * @param metadata The metadata.
     * @param callback Callback when finished.
     */
    private void runActions(@NonNull Context context, @Nullable final Map<String, ActionValue> actions,
                            final int situation, @Nullable final Bundle metadata, @NonNull final Runnable callback) {

        if (actions == null || actions.isEmpty()) {
            callback.run();
            return;
        }

        if (ActivityMonitor.shared(context).isAppForegrounded() || situation == Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON || situation == Action.SITUATION_PUSH_OPENED) {
            try {
                ActionService.runActions(context, actions, situation, metadata);
                callback.run();
                return;
            } catch (SecurityException | IllegalStateException e) {
                Logger.error("Unable to start action service.", e);
            }
        }

        // Fallback to running actions in the executor
        executor.execute(new Runnable() {
            @Override
            public void run() {
                for (Map.Entry<String, ActionValue> entry : actions.entrySet()) {
                    ActionRunRequest.createRequest(entry.getKey())
                            .setMetadata(metadata)
                            .setSituation(situation)
                            .setValue(entry.getValue())
                            .runSync();
                }

                callback.run();
            }
        });
    }

    /**
     * Parses an action payload.
     *
     * @param payload The payload.
     * @return The parsed actions.
     */
    private Map<String, ActionValue> parseActionValues(String payload) {
        Map<String, ActionValue> actionValueMap = new HashMap<>();

        try {
            JsonMap actionsJson = JsonValue.parseString(payload).getMap();
            if (actionsJson != null) {
                for (Map.Entry<String, JsonValue> entry : actionsJson) {
                    actionValueMap.put(entry.getKey(), new ActionValue(entry.getValue()));
                }
            }
        } catch (JsonException e) {
            Logger.error("Failed to parse actions for push.", e);
        }


        return actionValueMap;
    }
}
