package com.urbanairship.actions;

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

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * ActionRunRequests provides a fluent API for running Actions.
 * <p/>
 * Each action is run on its own thread when triggered asynchronously. If the
 * async run is triggered on the UI thread or a thread with a prepared looper,
 * the optional {@link com.urbanairship.actions.ActionCompletionCallback} will be
 * executed on the calling thread by sending a message to the calling thread's handler.
 * If the calling thread does not have a prepared looper, the callback will be
 * executed on the main thread.
 * <p/>
 * Synchronous runs will block, and should never be called on the UI thread.
 * It should only be used when executing actions in a separate thread, or as a
 * convenient way of running actions from another action.
 */
public class ActionRunRequest {

    @VisibleForTesting
    static Executor executor = Executors.newCachedThreadPool();

    private ActionRegistry registry;
    private String actionName;
    private Action action;
    private ActionValue actionValue;
    private Bundle metadata;
    private Situation situation;

    /**
     * Creates an action run request. The action will not be run
     * until running the request.
     *
     * @param actionName The action name in the registry.
     * @return An action run request.
     */
    public static ActionRunRequest createRequest(String actionName) {
        return new ActionRunRequest(actionName, null);
    }

    /**
     * Creates an action run request. The action will not be run
     * until running the request.
     *
     * @param actionName The action name in the registry.
     * @param registry Optional - The action registry to look up the action. If null, the registry
     * from {@link com.urbanairship.UAirship#getActionRegistry()} will be used.
     * @return An action run request.
     */
    @NonNull
    public static ActionRunRequest createRequest(String actionName, ActionRegistry registry) {
        return new ActionRunRequest(actionName, registry);
    }

    /**
     * Creates an action run request. The action will not be run
     * until running the request.
     *
     * @param action The action to run.
     * @return An action run request.
     * @throws java.lang.IllegalArgumentException if the action is null.
     */
    @NonNull
    public static ActionRunRequest createRequest(@NonNull Action action) {
        //noinspection ConstantConditions
        if (action == null) {
            throw new IllegalArgumentException("Unable to run null action");
        }

        return new ActionRunRequest(action);
    }

    /**
     * Creates a new action RunRequest.
     *
     * @param actionName The action name in the registry.
     * @param registry Optional - The action registry to look up the action. Defaults to {@link com.urbanairship.UAirship#getActionRegistry()}
     */
    @VisibleForTesting
    ActionRunRequest(String actionName, ActionRegistry registry) {
        this.actionName = actionName;
        this.registry = registry;
    }

    /**
     * Creates a new action RunRequest.
     *
     * @param action The action to run.
     */
    @VisibleForTesting
    ActionRunRequest(Action action) {
        this.action = action;
    }

    /**
     * Sets the action argument's value.
     *
     * @param actionValue The action argument's value.
     * @return The request object.
     */
    @NonNull
    public ActionRunRequest setValue(ActionValue actionValue) {
        this.actionValue = actionValue;
        return this;
    }

    /**
     * Sets the action arguments value. The object will automatically be wrapped
     * as a ActionValue and throw an illegal argument exception if its an invalid value.
     *
     * @param object The action arguments as an object.
     * @return The request object.
     * @throws IllegalArgumentException if the object is unable to be wrapped in an ActionValue.
     */
    @NonNull
    public ActionRunRequest setValue(Object object) {
        try {
            this.actionValue = ActionValue.wrap(object);
        } catch (ActionValueException e) {
            throw new IllegalArgumentException("Unable to wrap object: " + object + " as an ActionValue.", e);
        }
        return this;
    }

    /**
     * Sets the action argument's metadata.
     *
     * @param metadata The action argument's metadata.
     * @return The request object.
     */
    @NonNull
    public ActionRunRequest setMetadata(Bundle metadata) {
        this.metadata = metadata;
        return this;
    }

    /**
     * Sets the situation.
     *
     * @param situation The action argument's situation.
     * @return The request object.
     */
    @NonNull
    public ActionRunRequest setSituation(Situation situation) {
        this.situation = situation;
        return this;
    }

    /**
     * Executes the action synchronously.
     *
     * @return The action's result.
     */
    @NonNull
    public ActionResult runSync() {
        return runSync(createActionArguments());
    }

    /**
     * Runs the action synchronously with the given action arguments.
     *
     * @param arguments The action arguments.
     * @return The action's result.
     */
    @NonNull
    private ActionResult runSync(ActionArguments arguments) {
        if (actionName != null) {
            ActionRegistry.Entry entry = lookUpAction(actionName);
            if (entry == null) {
                return ActionResult.newEmptyResultWithStatus(ActionResult.STATUS_ACTION_NOT_FOUND);
            } else if (entry.getPredicate() != null && !entry.getPredicate().apply(arguments)) {
                Logger.info("Action " + actionName + " will not be run. Registry predicate rejected the arguments: " + arguments);
                return ActionResult.newEmptyResultWithStatus(ActionResult.STATUS_REJECTED_ARGUMENTS);
            } else {
                return entry.getActionForSituation(situation).run(arguments);
            }
        } else if (action != null) {
            return action.run(arguments);
        } else {
            return ActionResult.newEmptyResultWithStatus(ActionResult.STATUS_ACTION_NOT_FOUND);
        }
    }

    /**
     * Executes the action asynchronously.
     */
    public void run() {
        run(null, null);
    }

    /**
     * Executes the action asynchronously with a callback.
     *
     * @param callback The action completion callback.
     */
    public void run(final ActionCompletionCallback callback) {
        run(callback, null);
    }

    /**
     * Executes the action asynchronously with a callback.
     *
     * @param callback The action completion callback.
     * @param looper A Looper object whose message queue will be used for the callback,
     * or null to make callbacks on the calling thread or main thread if the current thread
     * does not have a looper associated with it.
     */
    public void run(final ActionCompletionCallback callback, Looper looper) {
        if (looper == null) {
            Looper myLooper = Looper.myLooper();
            looper = myLooper != null ? myLooper : Looper.getMainLooper();
        }

        final ActionArguments arguments = createActionArguments();

        final Handler handler = new Handler(looper);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                final ActionResult result = runSync(arguments);

                if (callback == null) {
                    return;
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onFinish(arguments, result);
                    }
                });
            }
        });
    }

    /**
     * Helper method to construct the action arguments.
     *
     * @return The action arguments.
     */
    @NonNull
    private ActionArguments createActionArguments() {
        Bundle metadata = this.metadata == null ? new Bundle() : new Bundle(this.metadata);
        if (actionName != null) {
            metadata.putString(ActionArguments.REGISTRY_ACTION_NAME_METADATA, actionName);
        }

        return new ActionArguments(situation, actionValue, metadata);
    }

    /**
     * Helper method to look an action registry entry.
     *
     * @param actionName The action name in the registry.
     * @return The action registry entry if found, or null.
     */
    @Nullable
    private ActionRegistry.Entry lookUpAction(@NonNull String actionName) {
        if (this.registry != null) {
            return this.registry.getEntry(actionName);
        }

        return UAirship.shared().getActionRegistry().getEntry(actionName);
    }
}
