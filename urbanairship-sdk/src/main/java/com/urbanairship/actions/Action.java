/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.support.annotation.IntDef;
import android.support.annotation.NonNull;

import com.urbanairship.Logger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

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
 * on their own threads with the {@link com.urbanairship.actions.ActionRunRequest}.
 * <p/>
 * Actions that are either long lived or are unable to be interrupted by the device
 * going to sleep should request a wake lock before performing. This is especially
 * important for actions that are performing in SITUATION_PUSH_RECEIVED, when a
 * push is delivered when the device is not active.
 * <p/>
 * The value returned by {@link #shouldRunOnMainThread()} determines which thread should
 * run the action if executed asynchronously. If an action involves a UI interaction, this
 * method should be overridden to return true so that the action definitely runs before the
 * app state changes.
 */
public abstract class Action {

    @IntDef(value={
            SITUATION_MANUAL_INVOCATION,
            SITUATION_PUSH_RECEIVED,
            SITUATION_PUSH_OPENED,
            SITUATION_WEB_VIEW_INVOCATION,
            SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON,
            SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON,
            SITUATION_AUTOMATION
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Situation {}

    /**
     * Situation where an action is manually invoked.
     */
    public final static int SITUATION_MANUAL_INVOCATION = 0;

    /**
     * Situation where an action is triggered when a push is received.
     */
    public final static int SITUATION_PUSH_RECEIVED = 1;

    /**
     * Situation where an action is triggered when a push is opened.
     */
    public final static int SITUATION_PUSH_OPENED = 2;

    /**
     * Situation where an action is triggered from a web view.
     */
    public final static int SITUATION_WEB_VIEW_INVOCATION = 3;

    /**
     * Situation where an action is triggered from a foreground notification action button.
     */
    public final static int SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON = 4;

    /**
     * Situation where an action is triggered from a background notification action button.
     */
    public final static int SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON = 5;

    /**
     * Situation where an action is triggered from {@link com.urbanairship.automation.Automation}.
     */
    public final static int SITUATION_AUTOMATION = 6;

    /**
     * Performs the action, with pre/post execution calls,
     * if it accepts the provided arguments.
     *
     * @param arguments The action arguments.
     * @return The result of the action.
     */
    final ActionResult run(@NonNull ActionArguments arguments) {
        try {
            if (!acceptsArguments(arguments)) {
                Logger.debug("Action " + this + " is unable to accept arguments: " + arguments);
                return ActionResult.newEmptyResultWithStatus(ActionResult.STATUS_REJECTED_ARGUMENTS);
            }

            Logger.info("Running action: " + this + " arguments: " + arguments);
            onStart(arguments);
            ActionResult result = perform(arguments);

            //noinspection ConstantConditions
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
    public boolean acceptsArguments(@NonNull ActionArguments arguments) {
        return true;
    }

    /**
     * Called before an action is performed.
     *
     * @param arguments The action arguments.
     */
    public void onStart(@NonNull ActionArguments arguments) {

    }

    /**
     * Performs the action.
     *
     * @param arguments The action arguments.
     * @return The result of the action.
     */
    @NonNull
    public abstract ActionResult perform(@NonNull ActionArguments arguments);

    /**
     * Called after the action performs.
     *
     * @param arguments The action arguments.
     * @param result The result of the action.
     */
    public void onFinish(@NonNull ActionArguments arguments, @NonNull ActionResult result) {

    }

    /**
     * Determines which thread runs the action.
     *
     * @return {@code true} if the action should be run on the main thread, or {@code false} if the
     * action should run on a background thread.
     */
    public boolean shouldRunOnMainThread() {
        return false;
    }
}
