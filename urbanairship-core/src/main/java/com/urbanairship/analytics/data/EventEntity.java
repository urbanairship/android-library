package com.urbanairship.analytics.data;

import com.urbanairship.analytics.Event;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import java.nio.charset.StandardCharsets;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.core.util.ObjectsCompat;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Representation of an {@link Event} for persistent storage via Room.
 *
 * @hide
 */
@Entity(tableName = "events", indices = {
    @Index(value = { "eventId" }, unique = true)
})
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class EventEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String type;
    public String eventId;
    public String time;
    public JsonValue data;
    public String sessionId;
    public int eventSize;

    EventEntity(String type, String eventId, String time, JsonValue data, String sessionId, int eventSize) {
        this.type = type;
        this.eventId = eventId;
        this.time = time;
        this.data = data;
        this.sessionId = sessionId;
        this.eventSize = eventSize;
    }

    public static EventEntity create(@NonNull Event event, @NonNull String sessionId) throws JsonException {
        String payload = event.createEventPayload(sessionId);
        JsonValue json = JsonValue.parseString(payload);

        return new EventEntity(
                event.getType(),
                event.getEventId(),
                event.getTime(),
                json,
                sessionId,
                payload.getBytes(StandardCharsets.UTF_8).length
        );
    }

    @Ignore
    @Override
    public String toString() {
        return "EventEntity{" +
                "id=" + id +
                ", type='" + type + '\'' +
                ", eventId='" + eventId + '\'' +
                ", time=" + time +
                ", data='" + data.toString() + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", eventSize=" + eventSize +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventEntity entity = (EventEntity) o;
        return id == entity.id &&
                eventSize == entity.eventSize &&
                ObjectsCompat.equals(type, entity.type) &&
                ObjectsCompat.equals(eventId, entity.eventId) &&
                ObjectsCompat.equals(time, entity.time) &&
                ObjectsCompat.equals(data, entity.data) &&
                ObjectsCompat.equals(sessionId, entity.sessionId);
    }

    @Override
    public int hashCode() {
        return ObjectsCompat.hash(id, type, eventId, time, data, sessionId, eventSize);
    }

    public boolean contentEquals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventEntity entity = (EventEntity) o;
        return eventSize == entity.eventSize &&
                ObjectsCompat.equals(type, entity.type) &&
                ObjectsCompat.equals(eventId, entity.eventId) &&
                ObjectsCompat.equals(time, entity.time) &&
                ObjectsCompat.equals(data, entity.data) &&
                ObjectsCompat.equals(sessionId, entity.sessionId);
    }

    /**
     * Minimal wrapper for queries that only need to return the event ID and data fields.
     */
    public static class EventIdAndData {
        public int id;
        public String eventId;
        public JsonValue data;

        public EventIdAndData(int id, String eventId, JsonValue data) {
            this.id = id;
            this.eventId = eventId;
            this.data = data;
        }
    }
}
