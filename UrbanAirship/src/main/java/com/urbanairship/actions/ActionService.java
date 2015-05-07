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

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.UAStringUtil;

import java.util.HashMap;
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
     * Intent extra for storing the actions bundle.
     */
    public static final String EXTRA_ACTIONS_BUNDLE = "com.urbanairship.actionservice.EXTRA_ACTIONS";

    /**
     * Intent extra for storing the current situation.
     */
    public static final String EXTRA_SITUATION = "com.urbanairship.actionservice.EXTRA_SITUATION";

    /**
     * Intent extra for storing metadata.
     */
    public static final String EXTRA_METADATA = "com.urbanairship.actionservice.EXTRA_METADATA";

    private int lastStartId = 0;

    // Number of currently running actions
    private int runningActions = 0;

    private ActionRunRequestFactory actionRunRequestFactory;

    /**
     * ActionService constructor, allowing an injectable ActionRunRequestFactory instance.
     *
     * @param actionRunRequestFactory The action request factory.
     */
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
     * Convenience method for running actions in the action service with added PushMessage metadata.
     *
     * @param payload Actions payload.
     * @param situation The current situation.
     * @param message The push message that triggered the actions.
     * @deprecated Marked to be removed in 7.0.0. Use {@link #runActions(Context, String, Situation, Bundle)} instead.
     */
    @Deprecated
    public static void runActionsPayload(Context context, String payload, Situation situation, PushMessage message) {
        Bundle metadata = new Bundle();
        if (message != null) {
            metadata.putParcelable(ActionArguments.PUSH_MESSAGE_METADATA, message);
        }

        runActions(context, payload, situation, metadata);
    }

    /**
     * Convenience method for running actions in the action service.
     *
     * @param context The application context.
     * @param payload Actions payload.
     * @param situation The current situation.
     * @deprecated Marked to be removed in 7.0.0. Use {@link #runActions(Context, String, Situation, Bundle)} instead.
     */
    @Deprecated
    public static void runActionsPayload(Context context, String payload, Situation situation) {
        runActions(context, payload, situation, null);
    }

    /**
     * Convenience method for running actions in the action service.
     *
     * @param context The application context.
     * @param actionsPayload Actions payload.
     * @param situation The action situation.
     * @param metadata The action metadata.
     */
    public static void runActions(Context context, String actionsPayload, Situation situation, Bundle metadata) {
        if (UAStringUtil.isEmpty(actionsPayload)) {
            return;
        }

        Map<String, ActionValue> actions = new HashMap<>();

        try {
            JsonMap actionsJson = JsonValue.parseString(actionsPayload).getMap();
            if (actionsJson != null) {
                for (Map.Entry<String, JsonValue> entry : actionsJson) {
                    actions.put(entry.getKey(), new ActionValue(entry.getValue()));
                }
            }
        } catch (JsonException e) {
            Logger.error("Unable to parse action payload: " + actionsPayload);
            return;
        }

        runActions(context, actions, situation, metadata);
    }

    /**
     * Convenience method for running actions in the action service.
     *
     * @param context The application context.
     * @param actions Map of action name to action values.
     * @param situation The action situation.
     * @param metadata The action metadata.
     */
    public static void runActions(Context context, Map<String, ActionValue> actions, Situation situation, Bundle metadata) {
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
                .putExtra(EXTRA_SITUATION, situation == null ? Situation.MANUAL_INVOCATION : situation);

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
    private void onRunActions(Intent intent) {
        Bundle actions = intent.getBundleExtra(EXTRA_ACTIONS_BUNDLE);
        if (actions.isEmpty()) {
            Logger.debug("ActionService - No actions to run.");
            return;
        }

        Situation situation = (Situation) intent.getSerializableExtra(EXTRA_SITUATION);
        Bundle metadata = intent.getBundleExtra(EXTRA_METADATA);

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
                                       public void onFinish(ActionArguments arguments, ActionResult result) {
                                           runningActions--;
                                           if (runningActions == 0) {
                                               stopSelf(lastStartId);
                                           }
                                       }
                                   });
        }
    }
}
