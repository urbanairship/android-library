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

import com.urbanairship.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Container for the argument data passed to an {@link com.urbanairship.actions.Action}.
 * <p/>
 * Actions that are invoked from a push notification or from Javascript will have
 * its value parsed from JSON with the following rules: JSON Objects as a Map,
 * JSON Arrays as a List, "null" values as <code>null</code>, primitive types (boolean, long,
 * double, etc.) as primitive type object wrappers (Boolean, Long, Double, Integer, etc.),
 * and Everything else as a String.
 */
public class ActionArguments {

    /**
     * Metadata attached to action arguments when launching from the JavaScript interface with
     * an associated RichPushMessage. The value is stored as a {@link com.urbanairship.richpush.RichPushMessage}.
     */
    public static final String RICH_PUSH_METADATA = "com.urbanairship.RICH_PUSH_METADATA";

    /**
     * Metadata attached to action arguments when launching actions from a push message.
     * The value is stored as a {@link com.urbanairship.push.PushMessage}.
     */
    public static final String PUSH_MESSAGE_METADATA = "com.urbanairship.PUSH_MESSAGE";

    private final Situation situation;
    private Object value;
    private Map<String, Object> metadata;

    /**
     * ActionArguments constructor.
     *
     * @param situation The situation the action is in.
     * @param value The argument value.
     */
    public ActionArguments(Situation situation, Object value) {
        this(situation, value, null);
    }

    /**
     * ActionArguments constructor.
     *
     * @param situation The situation the action is in.
     * @param value The argument value.
     * @param metadata Optional meta data for the arguments.
     */
    private ActionArguments(Situation situation, Object value, Map<String, Object> metadata) {
        this.value = value;
        this.situation = situation;

        if (metadata != null) {
            this.metadata = new HashMap<>(metadata);
        }
    }

    /**
     * Retrieves the argument value.
     *
     * @return The value as an Object.
     */
    public Object getValue() {
        return value;
    }

    /**
     * Gets metadata for the action arguments. Metadata
     * provides more information about the environment of which
     * the action was triggered from.
     *
     * @param key The key.
     * @return An object or null.
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key) {
        Object value = null;

        if (metadata != null) {
            value = metadata.get(key);
        }

        if (value == null) {
            return null;
        }

        try {
            return (T) value;
        } catch (ClassCastException e) {
            Logger.error("Unable to cast action argument value: " + value, e);
            return null;
        }
    }

    /**
     * Retrieves the situation.
     *
     * @return The situation.
     */
    public Situation getSituation() {
        return situation;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ActionArguments { situation: ")
          .append(situation)
          .append(", value: ")
          .append(value)
          .append(", metadata: {");

        if (metadata != null) {
            for (String key : metadata.keySet()) {
                sb.append(" ")
                  .append(key)
                  .append(": ")
                  .append(metadata.get(key));
            }
        }

        sb.append(" } }");
        return sb.toString();
    }

    /**
     * Action argument builder.
     */
    public static class Builder {
        private Situation situation = Situation.MANUAL_INVOCATION;
        private Map<String, Object> metadata = new HashMap<>();
        private Object value;

        /**
         * Sets the action argument's situation.
         *
         * @param situation The situation the action is in.
         * @return The builder.
         */
        public Builder setSituation(Situation situation) {
            this.situation = situation;
            return this;
        }

        /**
         * Sets the action argument's value.
         *
         * @param value The argument value.
         * @return The builder.
         */
        public Builder setValue(Object value) {
            this.value = value;
            return this;
        }

        /**
         * Adds metadata to action argument.
         *
         * @param key The meta data key.
         * @param value The meta data value.
         * @return The builder.
         */
        public Builder addMetadata(String key, Object value) {
            metadata.put(key, value);
            return this;
        }

        /**
         * Creates the action argument.
         *
         * @return The action argument.
         */
        public ActionArguments create() {
            return new ActionArguments(situation, value, metadata);
        }
    }
}
