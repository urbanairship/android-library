/* Copyright Airship and Contributors */

package test;

import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.EventListener;
import com.urbanairship.android.layout.event.EventType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import androidx.annotation.NonNull;

import static java.util.Objects.requireNonNull;

public class TestEventListener implements EventListener {
    private final Map<EventType, LongAdder> eventCounts = new ConcurrentHashMap<>();
    private final List<Event> events = new ArrayList<>();

    @Override
    public boolean onEvent(@NonNull Event event) {
        events.add(event);

        EventType eventType = event.getType();
        eventCounts.computeIfAbsent(eventType, type -> new LongAdder());
        requireNonNull(eventCounts.get(eventType)).increment();

        return true;
    }

    public int getCount(EventType type) {
        return requireNonNull(eventCounts.getOrDefault(type, new LongAdder())).intValue();
    }

    public Event getEventAt(int index) {
        return events.get(index);
    }
}
