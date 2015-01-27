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
 * Container for the argument data passed to an {@link com.urbanairship.actions.Action}.
 */
public final class ActionArguments {

    /**
     * Metadata when running an action from the JavaScript interface with an associated RichPushMessage.
     * The value is stored as a String.
     */
    public static final String RICH_PUSH_ID_METADATA = "com.urbanairship.RICH_PUSH_ID_METADATA";

    /**
     * Metadata attached to action arguments when launching actions from a push message.
     * The value is stored as a {@link com.urbanairship.push.PushMessage}.
     */
    public static final String PUSH_MESSAGE_METADATA = "com.urbanairship.PUSH_MESSAGE";

    /**
     * Metadata attached to action arguments when triggering an action from by name.
     * The value is stored as a String.
     */
    public static final String REGISTRY_ACTION_NAME_METADATA = "com.urbanairship.REGISTRY_ACTION_NAME";

    private final Situation situation;
    private final ActionValue value;
    private final Bundle metadata;

    /**
     * Constructs ActionArguments.
     *
     * @param situation The situation. Defaults to {@link Situation#MANUAL_INVOCATION} if null.
     * @param value The argument's value.
     * @param metadata The argument's metadata.
     */
    public ActionArguments(Situation situation, ActionValue value, Bundle metadata) {
        this.situation = situation == null ? Situation.MANUAL_INVOCATION : situation;
        this.value = value == null ? ActionValue.NULL : value;
        this.metadata = metadata == null ? new Bundle() : new Bundle(metadata);
    }

    /**
     * Retrieves the argument value.
     *
     * @return The value as an Object.
     */
    public ActionValue getValue() {
        return value;
    }

    /**
     * Retrieves the situation.
     *
     * @return The situation.
     */
    public Situation getSituation() {
        return situation;
    }

    /**
     * Gets the metadata for the action arguments. Metadata provides additional information about the
     * calling environment.
     *
     * @return The arguments metadata.
     */
    public Bundle getMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return "ActionArguments { situation: " + situation + ", value: " + value + ", metadata: " + metadata + " }";
    }
}
