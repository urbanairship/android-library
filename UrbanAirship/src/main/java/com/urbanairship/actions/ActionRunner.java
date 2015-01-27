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

import com.urbanairship.Logger;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * A helper class for running an {@link com.urbanairship.actions.Action}.
 * <p/>
 * Each action is run on its own thread when triggered asynchronously. If the
 * async run is triggered on the UI thread or a thread with a prepared looper,
 * the optional {@link com.urbanairship.actions.ActionCompletionCallback} will be
 * executed on the calling thread by sending a message to the calling thread's handler.
 * If the calling thread does not have a prepared looper, the callback will be
 * executed on the action's thread.
 * <p/>
 * Synchronous runs will block, and should never be called on the UI thread.
 * It should only be used when executing actions in a separate thread, or as a
 * convenient way of running actions from another action.
 */
public class ActionRunner {

    private static ActionRunner instance = new ActionRunner(ActionRegistry.shared(), Executors.newCachedThreadPool());

    private ActionRegistry actionRegistry;

    private Executor executor;

    /**
     * ActionRunner constructor.
     *
     * @param registry An instance of ActionRegistry.
     * @param executor An instance of Executor.
     */
    ActionRunner(ActionRegistry registry, Executor executor) {
        this.actionRegistry = registry;
        this.executor = executor;
    }

    /**
     * Returns the shared ActionRunner singleton instance.
     *
     * @return A shared ActionRunner instance.
     */
    public static ActionRunner shared() {
        return instance;
    }

    /**
     * Starts an action run request. The action will not be run
     * until executing the request.
     *
     * @param actionName The action name in the registry.
     * @return An action run request.
     */
    public RunRequest run(String actionName) {
        return new RunRequest(actionName, actionRegistry, executor);
    }

    /**
     * Starts an action run request. The action will not be run
     * until executing the request.
     *
     * @param action The action to run.
     * @return An action run request.
     * @throws java.lang.IllegalArgumentException if the action is null.
     */
    public RunRequest run(Action action) {
        if (action == null) {
            throw new IllegalArgumentException("Unable to run null action");
        }

        return new RunRequest(action, actionRegistry, executor);
    }

    /**
     * Run request object with a fluent api for defining an action run.
     */
    public static class RunRequest {

        private final ActionRegistry registry;
        private final Executor executor;
        private String actionName;
        private Action action;
        private ActionValue actionValue;
        private Bundle metadata;
        private Situation situation;

        /**
         * Creates a new action RunRequest.
         *
         * @param actionName The action name in the registry.
         * @param registry The action registry.
         * @param executor The executor.
         */
        RunRequest(String actionName, ActionRegistry registry, Executor executor) {
            this.actionName = actionName;
            this.registry = registry;
            this.executor = executor;
        }

        /**
         * Creates a new action RunRequest.
         *
         * @param action The action to run.
         * @param registry The action registry.
         * @param executor The executor.
         */
        RunRequest(Action action, ActionRegistry registry, Executor executor) {
            this.action = action;
            this.registry = registry;
            this.executor = executor;
        }

        /**
         * Sets the action argument's value.
         *
         * @param actionValue The action argument's value.
         * @return The request object.
         */
        public RunRequest setValue(ActionValue actionValue) {
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
        public RunRequest setValue(Object object) {
            try {
                this.actionValue = ActionValue.wrap(object);
            } catch (ActionValue.ActionValueException e) {
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
        public RunRequest setMetadata(Bundle metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Sets the situation.
         *
         * @param situation The action argument's situation.
         * @return The request object.
         */
        public RunRequest setSituation(Situation situation) {
            this.situation = situation;
            return this;
        }

        /**
         * Executes the action synchronously.
         *
         * @return The action's result.
         */
        public ActionResult executeSync() {
            ActionArguments arguments = createActionArguments();

            if (actionName != null) {
                ActionRegistry.Entry entry = registry.getEntry(actionName);
                if (entry == null) {
                    return ActionResult.newEmptyResultWithStatus(ActionResult.Status.ACTION_NOT_FOUND);
                } else if (entry.getPredicate() != null && !entry.getPredicate().apply(arguments)) {
                    Logger.info("Action " + actionName + " will not be run. Registry predicate rejected the arguments: " + arguments);
                    return ActionResult.newEmptyResultWithStatus(ActionResult.Status.REJECTED_ARGUMENTS);
                } else {
                    return entry.getActionForSituation(situation).run(arguments);
                }
            } else if (action != null) {
                return action.run(arguments);
            } else {
                return ActionResult.newEmptyResultWithStatus(ActionResult.Status.ACTION_NOT_FOUND);
            }
        }

        /**
         * Executes the action asynchronously.
         */
        public void execute() {
            execute(null);
        }

        /**
         * Executes the action asynchronously with a callback.
         *
         * @param callback The action completion callback.
         */
        public void execute(final ActionCompletionCallback callback) {
            final Handler handler = Looper.myLooper() != null ? new Handler() : null;

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    final ActionResult result = executeSync();

                    if (callback == null) {
                        return;
                    }

                    // Post it on the original caller's handler if we can
                    if (handler != null) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onFinish(result);
                            }
                        });
                    } else {
                        callback.onFinish(result);
                    }
                }
            });
        }

        /**
         * Helper method to construct the action arguments.
         *
         * @return The action arguments.
         */
        private ActionArguments createActionArguments() {
            Bundle metadata = this.metadata == null ? new Bundle() : new Bundle(this.metadata);
            if (actionName != null) {
                metadata.putString(ActionArguments.REGISTRY_ACTION_NAME_METADATA, actionName);
            }

            return new ActionArguments(situation, actionValue, metadata);
        }
    }
}
