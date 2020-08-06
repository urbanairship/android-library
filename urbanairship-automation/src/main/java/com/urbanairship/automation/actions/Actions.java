package com.urbanairship.automation.actions;

import com.urbanairship.automation.ScheduleData;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import androidx.annotation.NonNull;

public class Actions implements ScheduleData {

    private final JsonMap actions;

    public Actions(@NonNull JsonMap actions) {
        this.actions = actions;
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return actions.toJsonValue();
    }

    public JsonMap getActionsMap() {
        return actions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Actions actions1 = (Actions) o;

        return actions != null ? actions.equals(actions1.actions) : actions1.actions == null;
    }

    @Override
    public int hashCode() {
        return actions != null ? actions.hashCode() : 0;
    }

}
