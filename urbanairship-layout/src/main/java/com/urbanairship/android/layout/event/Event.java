/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

import com.urbanairship.android.layout.model.BaseModel;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.FormInfo;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.android.layout.reporting.PagerData;
import com.urbanairship.json.JsonValue;

import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Base event. */
public abstract class Event {

    @NonNull
    private final EventType type;

    protected Event(@NonNull EventType type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "Event{" +
                "type=" + type +
                '}';
    }

    /**
     * Returns the {@code Type} of this event.
     *
     * @return the event type.
     */
    @NonNull
    public EventType getType() {
        return type;
    }

    public static class ViewInit extends Event {
        private final BaseModel model;

        public ViewInit(@NonNull BaseModel model) {
            super(EventType.VIEW_INIT);
            this.model = model;
        }

        public ViewType getViewType() {
            return model.getType();
        }

        public BaseModel getModel() {
            return model;
        }

        @Override
        @NonNull
        public String toString() {
            return "ViewInit{" +
                ", viewType=" + getViewType() +
                ", model=" + model +
                '}';
        }
    }

    public static class ViewAttachedToWindow extends Event {
        private final BaseModel model;

        public ViewAttachedToWindow(@NonNull BaseModel model) {
            super(EventType.VIEW_ATTACHED);
            this.model = model;
        }

        public ViewType getViewType() {
            return model.getType();
        }

        public BaseModel getModel() {
            return model;
        }

        @Override
        @NonNull
        public String toString() {
            return "ViewAttachedToWindow{" +
                ", viewType=" + getViewType() +
                ", model=" + model +
                '}';
        }
    }

    public interface EventWithActions {
        @NonNull
        Map<String, JsonValue> getActions();
    }
}
