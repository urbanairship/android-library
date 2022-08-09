/* Copyright Airship and Contributors */

package test;


import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.EventListener;
import com.urbanairship.android.layout.event.EventType;
import com.urbanairship.android.layout.reporting.LayoutData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import static java.util.Objects.requireNonNull;

public class TestEventListener implements EventListener {
    private final Map<EventType, List<Pair<Event, LayoutData>>> eventsByType = new ConcurrentHashMap<>();
    private final List<Pair<Event, LayoutData>> events = new ArrayList<>();

    @Override
    public boolean onEvent(@NonNull Event event, @NonNull LayoutData layoutData) {
        Pair<Event, LayoutData> pair = Pair.create(event, layoutData);
        events.add(pair);

        EventType eventType = event.getType();
        eventsByType.computeIfAbsent(eventType, it -> new ArrayList<>());
        requireNonNull(eventsByType.get(eventType)).add(pair);

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
        return events.get(index).first;
    }

    /** Returns an event at the given index from a filtered list of the given event type. */
    public Event getEventAt(EventType type, int index) {
        try {
            return requireNonNull(eventsByType.get(type)).get(index).first;
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    /** Returns the layout data at the given index from the list of all received events. */
    public LayoutData getLayoutDataAt(int index) {
        return events.get(index).second;
    }

    /** Returns the layout data at the given index from a filtered list of the given event type. */
    public LayoutData getLayoutDataAt(EventType type, int index) {
        try {
            return requireNonNull(eventsByType.get(type)).get(index).second;
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

}
