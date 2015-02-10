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
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.JSONUtils;
import com.urbanairship.util.UAStringUtil;

import org.json.JSONException;
import org.json.JSONObject;

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
     * Intent extra for storing the actions payload
     */
    public static final String EXTRA_ACTIONS_PAYLOAD = "com.urbanairship.actionservice.EXTRA_ACTIONS_PAYLOAD";

    /**
     * Intent extra for storing the current situation.
     */
    public static final String EXTRA_SITUATION = "com.urbanairship.actionservice.EXTRA_SITUATION";

    /**
     * Intent extra for storing the push bundle that triggered the actions.
     */
    public static final String EXTRA_PUSH_BUNDLE = "com.urbanairship.actionservice.EXTRA_PUSH_BUNDLE";


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

            String actions = intent.getStringExtra(EXTRA_ACTIONS_PAYLOAD);
            Situation situation = (Situation) intent.getSerializableExtra(EXTRA_SITUATION);
            Bundle pushBundle = intent.getBundleExtra(EXTRA_PUSH_BUNDLE);
            PushMessage message = pushBundle == null ? null : new PushMessage(pushBundle);

            runActions(actions, situation, message);
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
     */
    public static void runActionsPayload(Context context, String payload, Situation situation, PushMessage message) {
        if (UAStringUtil.isEmpty(payload)) {
            return;
        }

        Intent i = new Intent(ACTION_RUN_ACTIONS)
                .setClass(context, ActionService.class)
                .putExtra(EXTRA_ACTIONS_PAYLOAD, payload)
                .putExtra(EXTRA_SITUATION, situation);

        if (message != null) {
            i.putExtra(EXTRA_PUSH_BUNDLE, message.getPushBundle());
        }

        context.startService(i);
    }


    /**
     * Convenience method for running actions in the action service.
     *
     * @param payload Actions payload.
     * @param situation The current situation.
     */
    public static void runActionsPayload(Context context, String payload, Situation situation) {
        runActionsPayload(context, payload, situation, null);
    }

    private void runActions(String actionsPayload, Situation situation, PushMessage message) {
        if (situation == null) {
            Logger.debug("ActionService - Unable to run actions with a null situation");
            return;
        }

        if (UAStringUtil.isEmpty(actionsPayload)) {
            Logger.debug("ActionService - No actions to run.");
            return;
        }

        JSONObject actionsJSON;
        try {
            actionsJSON = new JSONObject(actionsPayload);
        } catch (JSONException e) {
            Logger.debug("ActionService - Invalid actions payload: " + actionsPayload);
            return;
        }

        Bundle metadata = null;
        if (message != null) {
            metadata = new Bundle();
            metadata.putParcelable(ActionArguments.PUSH_MESSAGE_METADATA, message);
        }

        Map<String, Object> actionsMap = JSONUtils.convertToMap(actionsJSON);

        for (String actionName : actionsMap.keySet()) {
            runningActions++;

            // ActionCompletionCallback posts the runnable on the callers handle,
            // so we don't have to worry about any threading issues.  onFinish
            // can safely call stopSelf without worrying about any actions about to
            // run.
            actionRunRequestFactory.createActionRequest(actionName)
                    .setMetadata(metadata)
                    .setValue(actionsMap.get(actionName))
                    .setSituation(situation)
                    .run(new ActionCompletionCallback() {
                        @Override
                        public void onFinish(ActionResult result) {
                            runningActions--;
                            if (runningActions == 0) {
                                stopSelf(lastStartId);
                            }
                        }
                    });
        }
    }
}
