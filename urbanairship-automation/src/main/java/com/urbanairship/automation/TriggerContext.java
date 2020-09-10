package com.urbanairship.automation;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Triggering context for an automation schedule.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class TriggerContext implements JsonSerializable {

    private static final String TRIGGER_KEY = "trigger";
    private static final String EVENT_KEY = "event";

    private Trigger trigger;
    private JsonValue event;

    /**
     * Default constructor.
     *
     * @param trigger The trigger.
     * @param event The final event that triggered the schedule.
     */
    public TriggerContext(@NonNull Trigger trigger, @NonNull JsonValue event) {
        this.trigger = trigger;
        this.event = event;
    }

    /**
     * Gets the triggering event.
     *
     * @return The triggering event.
     */
    @NonNull
    public JsonValue getEvent() {
        return event;
    }

    /**
     * Gets the trigger.
     *
     * @return The trigger.
     */
    @NonNull
    public Trigger getTrigger() {
        return trigger;
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .put(TRIGGER_KEY, trigger)
                      .put(EVENT_KEY, event)
                      .build()
                      .toJsonValue();
    }

    @NonNull
    public static TriggerContext fromJson(@NonNull JsonValue jsonValue) throws JsonException {
        JsonValue event = jsonValue.optMap().opt(EVENT_KEY);
        Trigger trigger = Trigger.fromJson(jsonValue.optMap().opt(TRIGGER_KEY));
        return new TriggerContext(trigger, event);
    }

    @Override
    @NonNull
    public String toString() {
        return "TriggerContext{" +
                "trigger=" + trigger +
                ", event=" + event +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TriggerContext that = (TriggerContext) o;

        if (!trigger.equals(that.trigger)) return false;
        return event.equals(that.event);
    }

    @Override
    public int hashCode() {
        int result = trigger.hashCode();
        result = 31 * result + event.hashCode();
        return result;
    }

}
