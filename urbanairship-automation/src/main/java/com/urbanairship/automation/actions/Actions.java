/* Copyright Airship and Contributors */

package com.urbanairship.automation.actions;

import com.urbanairship.automation.ScheduleData;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Actions schedule data.
 */
public class Actions implements ScheduleData {

    private final JsonMap actions;

    /**
     * Default constructor.
     * @param actions The actions map.
     */
    public Actions(@NonNull JsonMap actions) {
        this.actions = actions;
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return actions.toJsonValue();
    }

    /**
     * Gets the actions map.
     * @return The actions map.
     */
    public JsonMap getActionsMap() {
        return actions;
    }

    /**
     * Factory method to create a builder.
     * @return The builder.
     */
    @NonNull
    public static Builder newBuilder() {
        return new Builder();
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Actions actions1 = (Actions) o;

        return actions.equals(actions1.actions);
    }

    @Override
    public int hashCode() {
        return actions.hashCode();
    }

    /**
     * Actions builder.
     */
    public static class Builder {
        private Map<String, JsonSerializable> actions = new HashMap<>();

        private Builder() {}

        /**
         * Adds an action.
         * @param action The action.
         * @param actionValue The action value.
         * @return The builder.
         */
        @NonNull
        public Builder addAction(@NonNull String action, @NonNull JsonSerializable actionValue) {
            actions.put(action, actionValue);
            return this;
        }

        /**
         * Adds an action.
         * @param action The action.
         * @param actionValue The action value.
         * @return The builder.
         */
        @NonNull
        public Builder addAction(@NonNull String action, @NonNull String actionValue) {
            actions.put(action, JsonValue.wrap(actionValue));
            return this;
        }

        /**
         * Adds an action.
         * @param action The action.
         * @param actionValue The action value.
         * @return The builder.
         */
        @NonNull
        public Builder addAction(@NonNull String action, long actionValue) {
            actions.put(action, JsonValue.wrap(actionValue));
            return this;
        }

        /**
         * Adds an action.
         * @param action The action.
         * @param actionValue The action value.
         * @return The builder.
         */
        @NonNull
        public Builder addAction(@NonNull String action, double actionValue) {
            actions.put(action, JsonValue.wrap(actionValue));
            return this;
        }

        /**
         * Adds an action.
         * @param action The action.
         * @param actionValue The action value.
         * @return The builder.
         */
        @NonNull
        public Builder addAction(@NonNull String action, boolean actionValue) {
            actions.put(action, JsonValue.wrap(actionValue));
            return this;
        }

        /**
         * Builds the actions.
         * @return The actions.
         */
        @NonNull
        public Actions build() {
            return new Actions(JsonValue.wrapOpt(actions).optMap());
        }
    }
}
