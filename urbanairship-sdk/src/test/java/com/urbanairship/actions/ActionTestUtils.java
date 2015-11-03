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
    public static ActionArguments createArgs(Situation situation, Object value) {
        return createArgs(situation, value, null);
    }

    /**
     * Creates an ActionArgument.
     *
     * @param situation The situation.
     * @param value The action value.
     * @return ActionArguments that contain the situation and value.
     */
    public static ActionArguments createArgs(Situation situation, ActionValue value) {
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
    public static ActionArguments createArgs(Situation situation, Object value, Bundle metadata) {
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
    public static ActionArguments createArgs(Situation situation, ActionValue value, Bundle metadata) {
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
