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

package com.urbanairship.actions;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;

/**
 * The base action class that describes an operation to perform.
 * <p/>
 * An action is an abstraction over a unary function, which takes
 * {@link com.urbanairship.actions.ActionArguments} and performs a defined task,
 * producing an optional {@link com.urbanairship.actions.ActionResult}. Actions
 * may restrict or vary the work they perform depending on the arguments they
 * receive, which may include type introspection and runtime context.
 * <p/>
 * In the larger view, the Actions framework provides a convenient way to
 * automatically perform tasks by name in response to push notifications,
 * Rich App Page interactions and JavaScript.
 * <p/>
 * The UA library comes with pre-made actions for common tasks such as setting
 * tags and opening URLs out of the box, but this class can also be extended to
 * enable custom app behaviors and engagement experiences.
 * <p/>
 * While actions can be run manually, typically they are associated with names
 * in the {@link com.urbanairship.actions.ActionRegistry}, and run
 * on their own threads with the {@link com.urbanairship.actions.ActionRunner}.
 * <p/>
 * Actions that are either long lived or are unable to be interrupted by the device
 * going to sleep should request a wake lock before performing. This is especially
 * important for actions that are performing in Situation.PUSH_RECEIVED, when a
 * push is delivered when the device is not active.
 */
public abstract class Action {

    /**
     * Performs the action, with pre/post execution calls,
     * if it accepts the provided arguments.
     *
     * @param arguments The action arguments.
     * @return The result of the action.
     */
    final ActionResult run(ActionArguments arguments) {
        try {
            if (arguments == null || !acceptsArguments(arguments)) {
                Logger.debug("Action " + this + " is unable to accept arguments: " + arguments);
                return ActionResult.newEmptyResultWithStatus(ActionResult.Status.REJECTED_ARGUMENTS);
            }

            Logger.info("Running action: " + this + " arguments: " + arguments);
            onStart(arguments);
            ActionResult result = perform(arguments);
            if (result == null) {
                result = ActionResult.newEmptyResult();
            }
            onFinish(arguments, result);
            return result;
        } catch (Exception e) {
            Logger.error("Failed to run action " + this, e);
            return ActionResult.newErrorResult(e);
        }
    }

    /**
     * Called before an action is performed to determine if the
     * the action can accept the arguments.
     *
     * @param arguments The action arguments.
     * @return <code>true</code> if the action can perform with the arguments,
     * otherwise <code>false</code>.
     */
    public boolean acceptsArguments(ActionArguments arguments) {
        return true;
    }

    /**
     * Called before an action is performed.
     *
     * @param arguments The action arguments.
     */
    public void onStart(ActionArguments arguments) {

    }

    /**
     * Performs the action.
     *
     * @param arguments The action arguments.
     * @return The result of the action.
     */
    public abstract ActionResult perform(ActionArguments arguments);

    /**
     * Called after the action performs.
     *
     * @param arguments The action arguments.
     * @param result The result of the action.
     */
    public void onFinish(ActionArguments arguments, ActionResult result) {

    }

    /**
     * Starts an activity for a result.
     *
     * @param intent The activity to start.
     * @return The result of the activity in a ActivityResult object.
     */
    public final ActivityResult startActivityForResult(Intent intent) {
        final ActivityResult result = new ActivityResult();

        ResultReceiver receiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onReceiveResult(int resultCode, Bundle resultData) {
                result.setResult(resultCode, (Intent) resultData.getParcelable(ActionActivity.RESULT_INTENT_EXTRA));
                synchronized (result) {
                    result.notify();
                }
            }
        };

        Context context = UAirship.getApplicationContext();
        Intent actionIntent = new Intent(context, ActionActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setPackage(UAirship.getPackageName())
                .putExtra(ActionActivity.START_ACTIVITY_INTENT_EXTRA, intent)
                .putExtra(ActionActivity.RESULT_RECEIVER_EXTRA, receiver);

        synchronized (result) {
            context.startActivity(actionIntent);
            try {
                result.wait();
            } catch (InterruptedException e) {
                Logger.error("Thread interrupted when waiting for result from activity.", e);
                return new ActivityResult();
            }
        }

        return result;
    }


    /**
     * Wraps the result code and data from starting an activity
     * for a result.
     */
    public static class ActivityResult {
        private int resultCode = Activity.RESULT_CANCELED;
        private Intent intent;

        /**
         * Gets the result intent.
         *
         * @return The result intent from the activity.
         */
        public Intent getIntent() {
            return intent;
        }

        /**
         * Gets the result code from the activity.
         *
         * @return The result code from the activity.
         */
        public int getResultCode() {
            return resultCode;
        }


        private void setResult(int resultCode, Intent intent) {
            this.resultCode = resultCode;
            this.intent = intent;
        }
    }
}
