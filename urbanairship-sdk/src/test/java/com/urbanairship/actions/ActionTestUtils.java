/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.os.Bundle;

/**
 * Utilities for writing action tests
 */
public class ActionTestUtils {

    /**
     * Creates an ActionArgument and automatically wraps the value into an ActionValue.
     *
     * @param situation The situation.
     * @param value The action value.
     * @return ActionArguments that contain the situation and value.
     */
    public static ActionArguments createArgs(@Action.Situation int situation, Object value) {
        return createArgs(situation, value, null);
    }

    /**
     * Creates an ActionArgument.
     *
     * @param situation The situation.
     * @param value The action value.
     * @return ActionArguments that contain the situation and value.
     */
    public static ActionArguments createArgs(@Action.Situation int situation, ActionValue value) {
        return createArgs(situation, value, null);
    }


    /**
     * Creates an ActionArgument.
     *
     * @param situation The situation.
     * @param value The action value.
     * @param metadata The metadata.
     * @return ActionArguments that contain the situation, value, and metadata.
     */
    public static ActionArguments createArgs(@Action.Situation int situation, Object value, Bundle metadata) {
        try {
            return createArgs(situation, ActionValue.wrap(value), metadata);
        } catch (ActionValueException e) {

            // Throw an illegal argument exception to fail the test
            throw new IllegalArgumentException("Object value: " + value + " unable to be wrapped as an action value.", e);
        }
    }

    /**
     * Creates an ActionArgument and automatically wraps the value into an ActionValue.
     *
     * @param situation The situation.
     * @param value The action value.
     * @param metadata The metadata.
     * @return ActionArguments that contain the situation, value, and metadata.
     */
    public static ActionArguments createArgs(@Action.Situation int situation, ActionValue value, Bundle metadata) {
        return new ActionArguments(situation, value, metadata);
    }

    /**
     * Creates an ActionResult and automatically wraps the value into an ActionValue.
     *
     * @param value The ActionResult value.
     * @param exception The ActionResult exception.
     * @param status The ActionResult status.
     * @return ActionResult that contains the value, exception, and status.
     */
    public static ActionResult createResult(Object value, Exception exception, @ActionResult.Status int status) {
        try {
            return new ActionResult(ActionValue.wrap(value), exception, status);
        } catch (ActionValueException e) {
            // Throw an illegal argument exception to fail the test
            throw new IllegalArgumentException("Object value: " + value + " unable to be wrapped as an action value.", e);
        }
    }

}
