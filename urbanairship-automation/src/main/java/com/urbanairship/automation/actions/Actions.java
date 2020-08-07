/* Copyright Airship and Contributors */

package com.urbanairship.automation.actions;

import com.urbanairship.automation.ScheduleData;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

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
}
