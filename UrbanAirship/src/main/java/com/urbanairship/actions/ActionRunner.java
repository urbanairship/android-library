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

import android.os.Handler;

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
     * Runs an action asynchronously with a callback.
     *
     * @param action The action to run.
     * @param arguments The action arguments.
     * @param completionCallback The action completion callback. The callback
     * will be executed on the caller's thread if it was called on the main thread
     * or on a thread with a prepared looper, otherwise it will be executed
     * on a background thread.
     * @throws IllegalArgumentException if the action is null
     */
    public void runAction(final Action action, final ActionArguments arguments, final ActionCompletionCallback completionCallback) {
        if (action == null) {
            throw new IllegalArgumentException("Unable to run null action");
        }

        final Handler handler = getHandler();

        executor.execute(new Runnable() {
            @Override
            public void run() {
                final ActionResult result = action.run(null, arguments);
                postResult(handler, completionCallback, result);
            }
        });
    }


    /**
     * Runs an action asynchronously.
     *
     * @param action The action to run.
     * @param arguments The action arguments.
     */
    public void runAction(Action action, ActionArguments arguments) {
        this.runAction(action, arguments, null);
    }

    /**
     * Runs an action asynchronously with a callback.
     *
     * @param actionName Name of the action in the registry.
     * @param arguments The action arguments.
     * @param completionCallback The action completion callback. The callback
     * will be executed on the caller's thread if it was called on the main thread
     * or on a thread with a prepared looper, otherwise it will be executed
     * on a background thread.
     */
    public void runAction(final String actionName, final ActionArguments arguments, final ActionCompletionCallback completionCallback) {
        final Handler handler = getHandler();

        executor.execute(new Runnable() {
            @Override
            public void run() {
                final ActionResult result = runActionSync(actionName, arguments);
                postResult(handler, completionCallback, result);
            }
        });
    }

    /**
     * Runs an action asynchronously.
     *
     * @param actionName Name of the action in the registry.
     * @param arguments The action arguments.
     */
    public void runAction(String actionName, ActionArguments arguments) {
        this.runAction(actionName, arguments, null);
    }

    /**
     * Runs an action synchronously.
     *
     * @param action The action to run.
     * @param arguments The action arguments.
     * @return The action's result.
     */
    public ActionResult runActionSync(Action action, ActionArguments arguments) {
        if (action == null) {
            throw new IllegalArgumentException("Unable to run null action");
        }

        return action.run(null, arguments);
    }


    /**
     * Runs an action synchronously.
     *
     * @param actionName Name of the action in the registry.
     * @param arguments The action arguments.
     * @return The action's result.
     */
    public ActionResult runActionSync(String actionName, ActionArguments arguments) {
        ActionRegistry.Entry entry = actionRegistry.getEntry(actionName);
        if (entry == null) {
            return ActionResult.newEmptyResultWithStatus(ActionResult.Status.ACTION_NOT_FOUND);
        } else if (entry.getPredicate() != null && !entry.getPredicate().apply(arguments)) {
            Logger.info("Action " + actionName + " will not be run. Registry predicate rejected the arguments: " + arguments);
            return ActionResult.newEmptyResultWithStatus(ActionResult.Status.REJECTED_ARGUMENTS);
        } else {
            Situation situation = arguments == null ? null : arguments.getSituation();
            return entry.getActionForSituation(situation).run(actionName, arguments);
        }
    }

    /**
     * Helper method that calls the callback on the right thread with the
     * given result.
     *
     * @param handler The handler of the caller's thread.
     * @param callback The action completion callback.
     * @param result The result of the action.
     */
    private void postResult(Handler handler, final ActionCompletionCallback callback, final ActionResult result) {
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

    /**
     * Helper method to safely get the handler on the current thread.
     *
     * @return Handler if the current thread has a prepared looper, or null.
     */
    private Handler getHandler() {
        try {
            return new Handler();
        } catch (Exception ex) {
            return null;
        }
    }

}
