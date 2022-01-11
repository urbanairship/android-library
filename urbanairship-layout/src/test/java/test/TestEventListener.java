/* Copyright Airship and Contributors */

package test;

import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.EventListener;
import com.urbanairship.android.layout.event.EventType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import androidx.annotation.NonNull;

import static java.util.Objects.requireNonNull;

public class TestEventListener implements EventListener {
    private final Map<EventType, List<Event>> eventsByType = new ConcurrentHashMap<>();
    private final List<Event> events = new ArrayList<>();

    @Override
    public boolean onEvent(@NonNull Event event) {
        events.add(event);

        EventType eventType = event.getType();
        eventsByType.computeIfAbsent(eventType, it -> new ArrayList<>());
        requireNonNull(eventsByType.get(eventType)).add(event);

        return true;
    }

    /** Returns a total count of all received events. */
    public int getCount() {
        return events.size();
    }

    /** Returns a total count of received events with the given type. */
    public int getCount(EventType type) {
        eventsByType.computeIfAbsent(type, it -> new ArrayList<>());
        return requireNonNull(eventsByType.get(type)).size();
    }

    /** Returns an event at the given index from the list of all received events. */
    public Event getEventAt(int index) {
        return events.get(index);
    }

    /** Returns an event at the given index from a filtered list of the given event type. */
    public Event getEventAt(EventType type, int index) {
        try {
            return requireNonNull(eventsByType.get(type)).get(index);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    /** Returns the full list of received events. */
    public List<Event> getEvents() {
        return events;
    }
}
