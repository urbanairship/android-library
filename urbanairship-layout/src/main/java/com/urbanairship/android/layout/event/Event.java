/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

import com.urbanairship.android.layout.model.BaseModel;
import com.urbanairship.android.layout.property.ViewType;

import androidx.annotation.NonNull;

/** Base event. */
public abstract class Event {

    @NonNull
    private final EventType type;

    protected Event(@NonNull EventType type) {
        this.type = type;
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
    }
}
