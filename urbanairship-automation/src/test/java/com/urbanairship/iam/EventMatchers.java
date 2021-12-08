package com.urbanairship.iam;

import com.urbanairship.Predicate;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.Event;
import com.urbanairship.iam.events.InAppReportingEvent;
import com.urbanairship.json.JsonMap;

import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import androidx.core.util.ObjectsCompat;

public abstract class EventMatchers {
    public static ArgumentMatcher<Event> isResolution() {
        return argument -> argument.getType().equals(InAppReportingEvent.TYPE_RESOLUTION);
    }

    public static ArgumentMatcher<Event> isDisplay() {
        return argument -> argument.getType().equals(InAppReportingEvent.TYPE_DISPLAY);
    }

    public static ArgumentMatcher<Event> eventType(String eventType) {
        return argument -> argument.getType().equals(eventType);
    }

    public static ArgumentMatcher<Event> event(String eventType, JsonMap eventData) {
        return argument -> ObjectsCompat.equals(eventData, argument.getEventData())
                && ObjectsCompat.equals(eventType, argument.getType());
    }

    public static ArgumentMatcher<Event> event(String eventType, Predicate<JsonMap> predicate) {
        return argument -> predicate.apply(argument.getEventData())
                && ObjectsCompat.equals(eventType, argument.getType());
    }
}
