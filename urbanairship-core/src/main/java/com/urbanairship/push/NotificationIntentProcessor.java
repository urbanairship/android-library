/* Copyright Airship and Contributors */

package com.urbanairship.push;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.AirshipExecutors;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.UAirship;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionCompletionCallback;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.analytics.InteractiveNotificationEvent;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationManagerCompat;

/**
 * Processes notification intents.
 */
class NotificationIntentProcessor {

    private final Executor executor;
    private final NotificationActionButtonInfo actionButtonInfo;
    private final NotificationInfo notificationInfo;
    private final Intent intent;
    private final Context context;
    private final UAirship airship;

    /**
     * Default constructor.
     *
     * @param context The context.
     * @param intent The intent.
     */
    NotificationIntentProcessor(@NonNull Context context, @NonNull Intent intent) {
        this(UAirship.shared(), context, intent, AirshipExecutors.threadPoolExecutor());
    }

    @VisibleForTesting
    NotificationIntentProcessor(@NonNull UAirship airship, @NonNull Context context,
                                @NonNull Intent intent, @NonNull Executor executor) {
        this.airship = airship;
        this.executor = executor;
        this.intent = intent;
        this.context = context;
        this.notificationInfo = NotificationInfo.fromIntent(intent);
        this.actionButtonInfo = NotificationActionButtonInfo.fromIntent(intent);
    }

    /**
     * Processes the intent.
     *
     * @return A pending result. The result will be {@code true} if the intent was processed, otherwise
     * {@code false}.
     */
    @MainThread
    PendingResult<Boolean> process() {
        final PendingResult<Boolean> pendingResult = new PendingResult<>();

        if (intent.getAction() == null || notificationInfo == null) {
            Logger.error("NotificationIntentProcessor - invalid intent %s", intent);
            pendingResult.setResult(false);
            return pendingResult;
        }

        Logger.verbose("Processing intent: %s", intent.getAction());
        switch (intent.getAction()) {
            case PushManager.ACTION_NOTIFICATION_RESPONSE:
                onNotificationResponse(new Runnable() {
                    @Override
                    public void run() {
                        pendingResult.setResult(true);
                    }
                });
                break;
            case PushManager.ACTION_NOTIFICATION_DISMISSED:
                onNotificationDismissed();
                pendingResult.setResult(true);
                break;

            default:
                Logger.error("NotificationIntentProcessor - Invalid intent action: %s", intent.getAction());
                pendingResult.setResult(false);
                break;
        }

        return pendingResult;
    }

    /**
     * Handles the opened notification without an action.
     * @param completionHandler The completion handler.
     */
    private void onNotificationResponse(@NonNull Runnable completionHandler) {
        Logger.info("Notification response: %s, %s", notificationInfo, actionButtonInfo);

        if (actionButtonInfo == null || actionButtonInfo.isForeground()) {
            // Set the conversion push id and metadata
            airship.getAnalytics().setConversionSendId(notificationInfo.getMessage().getSendId());
            airship.getAnalytics().setConversionMetadata(notificationInfo.getMessage().getMetadata());
        }

        NotificationListener listener = airship.getPushManager().getNotificationListener();

        if (actionButtonInfo != null) {
            // Add the interactive notification event
            InteractiveNotificationEvent event = new InteractiveNotificationEvent(notificationInfo, actionButtonInfo);
            airship.getAnalytics().addEvent(event);

            // Dismiss the notification
            NotificationManagerCompat.from(context).cancel(notificationInfo.getNotificationTag(), notificationInfo.getNotificationId());

            if (actionButtonInfo.isForeground()) {
                if (listener == null || !listener.onNotificationForegroundAction(notificationInfo, actionButtonInfo)) {
                    launchApplication();
                }
            } else if (listener != null) {
                listener.onNotificationBackgroundAction(notificationInfo, actionButtonInfo);
            }
        } else {
            if (listener == null || !listener.onNotificationOpened(notificationInfo)) {
                launchApplication();
            }
        }

        for (InternalNotificationListener internalNotificationListener : airship.getPushManager().getInternalNotificationListeners()) {
            internalNotificationListener.onNotificationResponse(notificationInfo, actionButtonInfo);
        }

        runNotificationResponseActions(completionHandler);
    }

    /**
     * Handles notification dismissed intent.
     */
    private void onNotificationDismissed() {
        Logger.info("Notification dismissed: %s", notificationInfo);

        if (intent.getExtras() != null) {
            PendingIntent deleteIntent = (PendingIntent) intent.getExtras().get(PushManager.EXTRA_NOTIFICATION_DELETE_INTENT);
            if (deleteIntent != null) {
                try {
                    deleteIntent.send();
                } catch (PendingIntent.CanceledException e) {
                    Logger.debug("Failed to send notification's deleteIntent, already canceled.");
                }
            }
        }

        NotificationListener listener = airship.getPushManager().getNotificationListener();
        if (listener != null) {
            listener.onNotificationDismissed(notificationInfo);
        }
    }

    /**
     * Helper method that attempts to launch the application's launch intent.
     */
    private void launchApplication() {

        if (intent.getExtras() != null) {
            PendingIntent contentIntent = (PendingIntent) intent.getExtras().get(PushManager.EXTRA_NOTIFICATION_CONTENT_INTENT);
            if (contentIntent != null) {
                try {
                    contentIntent.send();
                } catch (PendingIntent.CanceledException e) {
                    Logger.debug("Failed to send notification's contentIntent, already canceled.");
                }
                return;
            }
        }

        AirshipConfigOptions options = airship.getAirshipConfigOptions();
        if (options.autoLaunchApplication) {
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(UAirship.getPackageName());
            if (launchIntent != null) {
                launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                launchIntent.putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, notificationInfo.getMessage().getPushBundle());
                launchIntent.setPackage(null);
                Logger.info("Starting application's launch intent.");
                context.startActivity(launchIntent);
            } else {
                Logger.info("Unable to launch application. Launch intent is unavailable.");
            }
        }
    }

    /**
     * Helper method to run the actions.
     *
     * @param completionHandler Callback when finished.
     */
    private void runNotificationResponseActions(@NonNull final Runnable completionHandler) {
        Map<String, ActionValue> actions = null;
        int situation = 0;
        Bundle metadata = new Bundle();
        metadata.putParcelable(ActionArguments.PUSH_MESSAGE_METADATA, notificationInfo.getMessage());

        if (actionButtonInfo != null) {
            String actionPayload = intent.getStringExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ACTIONS_PAYLOAD);
            if (!UAStringUtil.isEmpty(actionPayload)) {
                actions = parseActionValues(actionPayload);

                if (actionButtonInfo.getRemoteInput() != null) {
                    metadata.putBundle(ActionArguments.REMOTE_INPUT_METADATA, actionButtonInfo.getRemoteInput());
                }

                situation = actionButtonInfo.isForeground() ? Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON : Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON;
            }
        } else {
            situation = Action.SITUATION_PUSH_OPENED;
            actions = notificationInfo.getMessage().getActions();
        }

        if (actions == null || actions.isEmpty()) {
            completionHandler.run();
            return;
        }

        runActions(actions, situation, metadata, completionHandler);
    }

    /**
     * Helper method to run actions.
     *
     * @param actions The actions payload.
     * @param situation The situation.
     * @param metadata The metadata.
     * @param completionHandler The completion handler.
     */
    private void runActions(@NonNull final Map<String, ActionValue> actions, final int situation, @NonNull final Bundle metadata, @NonNull final Runnable completionHandler) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final CountDownLatch countDownLatch = new CountDownLatch(actions.size());

                for (Map.Entry<String, ActionValue> entry : actions.entrySet()) {
                    ActionRunRequest.createRequest(entry.getKey())
                                    .setMetadata(metadata)
                                    .setSituation(situation)
                                    .setValue(entry.getValue())
                                    .run(new ActionCompletionCallback() {
                                        @Override
                                        public void onFinish(@NonNull ActionArguments arguments, @NonNull ActionResult result) {
                                            countDownLatch.countDown();
                                        }
                                    });
                }

                try {
                    countDownLatch.await();
                } catch (InterruptedException e) {
                    Logger.error(e, "Failed to wait for actions");
                    Thread.currentThread().interrupt();
                }

                completionHandler.run();
            }
        });
    }

    /**
     * Parses an action payload.
     *
     * @param payload The payload.
     * @return The parsed actions.
     */
    @NonNull
    private Map<String, ActionValue> parseActionValues(@NonNull String payload) {
        Map<String, ActionValue> actionValueMap = new HashMap<>();

        try {
            JsonMap actionsJson = JsonValue.parseString(payload).getMap();
            if (actionsJson != null) {
                for (Map.Entry<String, JsonValue> entry : actionsJson) {
                    actionValueMap.put(entry.getKey(), new ActionValue(entry.getValue()));
                }
            }
        } catch (JsonException e) {
            Logger.error(e, "Failed to parse actions for push.");
        }

        return actionValueMap;
    }

}
