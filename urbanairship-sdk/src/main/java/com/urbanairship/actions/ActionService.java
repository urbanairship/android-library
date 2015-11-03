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

package com.urbanairship.actions;

import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import java.util.Map;

/**
 * Service class for running actions via push payload.
 */
public class ActionService extends Service {

    /**
     * Intent action for running actions.
     */
    public static final String ACTION_RUN_ACTIONS = "com.urbanairship.actionservice.ACTION_RUN_ACTIONS";

    /**
     * Intent extra for storing the actions as a bundle of action name to action values.
     */
    public static final String EXTRA_ACTIONS_BUNDLE = "com.urbanairship.actionservice.EXTRA_ACTIONS";

    /**
     * Intent extra for storing the current situation.
     */
    public static final String EXTRA_SITUATION = "com.urbanairship.actionservice.EXTRA_SITUATION";

    /**
     * Intent extra for storing metadata as a bundle.
     */
    public static final String EXTRA_METADATA = "com.urbanairship.actionservice.EXTRA_METADATA";

    private int lastStartId = 0;

    // Number of currently running actions
    private int runningActions = 0;

    private final ActionRunRequestFactory actionRunRequestFactory;

    /**
     * ActionService constructor, allowing an injectable ActionRunRequestFactory instance.
     *
     * @param actionRunRequestFactory The action request factory.
     */
    @VisibleForTesting
    ActionService(ActionRunRequestFactory actionRunRequestFactory) {
        this.actionRunRequestFactory = actionRunRequestFactory;
    }

    /**
     * Default ActionService constructor.
     */
    public ActionService() {
        this(new ActionRunRequestFactory());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Autopilot.automaticTakeOff(getApplicationContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Autopilot.automaticTakeOff((Application) getApplicationContext());

        lastStartId = startId;

        if (intent != null && ACTION_RUN_ACTIONS.equals(intent.getAction())) {
            Logger.verbose("ActionService - Received intent: " + intent.getAction() + " startId: " + startId);
            onRunActions(intent);
        }

        if (runningActions == 0) {
            stopSelf(startId);
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Convenience method for running actions in the action service.
     *
     * @param context The application context.
     * @param actionsPayload Actions payload.
     * @param situation The action situation.
     * @param metadata The action metadata.
     */
    public static void runActions(@NonNull Context context, @NonNull String actionsPayload, @Action.Situation int situation, @Nullable Bundle metadata) {
        Bundle actions = createActionsBundle(actionsPayload);
        if (actions.isEmpty()) {
            return;
        }

        Intent intent = new Intent(ACTION_RUN_ACTIONS)
                .setClass(context, ActionService.class)
                .putExtra(EXTRA_ACTIONS_BUNDLE, actions)
                .putExtra(EXTRA_SITUATION, situation);

        if (metadata != null) {
            intent.putExtra(EXTRA_METADATA, metadata);
        }

        context.startService(intent);
    }

    /**
     * Convenience method for running actions in the action service.
     *
     * @param context The application context.
     * @param actions Map of action name to action values.
     * @param situation The action situation.
     * @param metadata The action metadata.
     */
    public static void runActions(@NonNull Context context, @NonNull Map<String, ActionValue> actions, @Action.Situation int situation, @Nullable Bundle metadata) {
        if (actions.isEmpty()) {
            return;
        }

        Bundle actionsBundle = new Bundle();
        for (Map.Entry<String, ActionValue> entry : actions.entrySet()) {
            actionsBundle.putParcelable(entry.getKey(), entry.getValue());
        }

        Intent intent = new Intent(ACTION_RUN_ACTIONS)
                .setClass(context, ActionService.class)
                .putExtra(EXTRA_ACTIONS_BUNDLE, actionsBundle)
                .putExtra(EXTRA_SITUATION, situation);

        if (metadata != null) {
            intent.putExtra(EXTRA_METADATA, metadata);
        }

        context.startService(intent);
    }

    /**
     * Handles the {@link #ACTION_RUN_ACTIONS} intent action.
     *
     * @param intent The service intent.
     */
    private void onRunActions(@NonNull Intent intent) {
        Bundle actions = intent.getBundleExtra(EXTRA_ACTIONS_BUNDLE);
        if (actions == null) {
            actions = new Bundle();
        }

        @Action.Situation int situation;
        switch (intent.getIntExtra(EXTRA_SITUATION, Action.SITUATION_MANUAL_INVOCATION)) {
            case Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON:
                situation = Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON;
                break;

            case Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON:
                situation = Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON;
                break;

            case Action.SITUATION_PUSH_OPENED:
                situation = Action.SITUATION_PUSH_OPENED;
                break;

            case Action.SITUATION_PUSH_RECEIVED:
                situation = Action.SITUATION_PUSH_RECEIVED;
                break;

            case Action.SITUATION_WEB_VIEW_INVOCATION:
                situation = Action.SITUATION_WEB_VIEW_INVOCATION;
                break;

            case Action.SITUATION_MANUAL_INVOCATION:
            default:
                situation = Action.SITUATION_MANUAL_INVOCATION;
                break;
        }

        Bundle metadata = intent.getBundleExtra(EXTRA_METADATA);
        if (metadata == null) {
            metadata = new Bundle();
        }

        if (actions.isEmpty()) {
            Logger.debug("ActionService - No actions to run.");
            return;
        }

        for (String actionName : actions.keySet()) {
            runningActions++;

            // ActionCompletionCallback posts the runnable on the callers handle,
            // so we don't have to worry about any threading issues.  onFinish
            // can safely call stopSelf without worrying about any actions about to
            // run.
            actionRunRequestFactory.createActionRequest(actionName)
                                   .setMetadata(metadata)
                                   .setValue((ActionValue) actions.getParcelable(actionName))
                                   .setSituation(situation)
                                   .run(new ActionCompletionCallback() {
                                       @Override
                                       public void onFinish(@NonNull ActionArguments arguments, @NonNull ActionResult result) {
                                           runningActions--;
                                           if (runningActions == 0) {
                                               stopSelf(lastStartId);
                                           }
                                       }
                                   });
        }
    }


    /**
     * Creates a Bundle of action name to action values from an action payload.
     *
     * @param actionsPayload The action payload.
     * @return A bundle of actions to run.
     */
    private static Bundle createActionsBundle(@NonNull String actionsPayload) {
        Bundle actions = new Bundle();

        if (UAStringUtil.isEmpty(actionsPayload)) {
            return actions;
        }

        try {
            JsonMap actionsJson = JsonValue.parseString(actionsPayload).getMap();
            if (actionsJson != null) {
                for (Map.Entry<String, JsonValue> entry : actionsJson) {
                    actions.putParcelable(entry.getKey(), new ActionValue(entry.getValue()));
                }
            }
        } catch (JsonException e) {
            Logger.error("Unable to parse action payload: " + actionsPayload);
        }

        return actions;
    }
}
