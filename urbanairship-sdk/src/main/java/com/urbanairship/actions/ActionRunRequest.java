/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * ActionRunRequests provides a fluent API for running Actions.
 * <p/>
 * If an action entails a UI interaction, {@link Action#shouldRunOnMainThread()} will be
 * overridden to return true so that the action runs on the UI thread when triggered
 * asynchronously. If called by the UI thread, the action will run immediately, otherwise it will
 * be posted to the main thread's looper. All other actions will run on their own thread.
 * If the async run is triggered on the UI thread or a thread with a prepared looper,
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
    private @Action.Situation int situation = Action.SITUATION_MANUAL_INVOCATION;

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
    public ActionRunRequest setSituation(@Action.Situation int situation) {
        this.situation = situation;
        return this;
    }

    /**
     * Executes the action synchronously.
     *
     * @return The action's result.
     */
    @NonNull
    @WorkerThread
    public ActionResult runSync() {
        final ActionArguments arguments = createActionArguments();
        final Semaphore semaphore = new Semaphore(0);

        ActionRunnable runnable = new ActionRunnable(arguments) {
            @Override
            void onFinish(ActionArguments arguments, ActionResult result) {
                semaphore.release();
            }
        };

        if (shouldRunOnMain(arguments)) {
            new Handler(Looper.getMainLooper()).post(runnable);
        } else {
            executor.execute(runnable);
        }

        try {
            semaphore.acquire();
        } catch (InterruptedException ex) {
            Logger.error("Failed to run action with arguments " + arguments);
           return ActionResult.newErrorResult(ex);
        }

        return runnable.result;
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

        ActionRunnable runnable = new ActionRunnable(arguments) {
            @Override
            void onFinish(final ActionArguments arguments, final ActionResult result) {
                if (callback == null) {
                    return;
                }

                if (handler.getLooper() == Looper.myLooper()) {
                    callback.onFinish(arguments, result);
                } else {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onFinish(arguments, result);
                        }
                    });
                }

            }
        };

        if (shouldRunOnMain(arguments)) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                runnable.run();
            } else {
                new Handler(Looper.getMainLooper()).post(runnable);
            }
        } else {
            executor.execute(runnable);
        }
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

    /**
     * Helper method to check if the request should run on the main thread.
     *
     * @param arguments The action arguments.
     * @return {@code true} if the action should run on the main thread, otherwise {@code false}
     */
    private boolean shouldRunOnMain(ActionArguments arguments) {
        if (action != null) {
            return action.shouldRunOnMainThread();
        }

        ActionRegistry.Entry entry = lookUpAction(actionName);
        return entry != null && entry.getActionForSituation(arguments.getSituation()).shouldRunOnMainThread();
    }

    /**
     * Helper method to actually run the action.
     *
     * @param arguments The action arguments.
     * @return The action's result.
     */
    @NonNull
    private ActionResult executeAction(ActionArguments arguments) {
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
     * Helper runnable for running the action request and retaining the result.
     */
    private abstract class ActionRunnable implements Runnable {

        private volatile ActionResult result;
        private final ActionArguments arguments;

        public ActionRunnable(ActionArguments arguments) {
            this.arguments = arguments;
        }

        @Override
        public final void run() {
            result = executeAction(arguments);
            onFinish(arguments, result);
        }

        /**
         * Called when the action is finished.
         *
         * @param arguments The arguments.
         * @param result The action result.
         */
        abstract void onFinish(ActionArguments arguments, ActionResult result);
    }
}
