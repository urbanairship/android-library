/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CarouselIndicatorModel extends BaseModel {
    @NonNull
    private final String identifier;
    @NonNull
    private final String currentCardId; // TODO: what to do with this?

    private int size = -1;
    private int position = -1;
    @Nullable
    private Listener listener;

    public CarouselIndicatorModel(@NonNull String identifier, @NonNull String currentCardId) {
        super(ViewType.CAROUSEL_INDICATOR);

        this.identifier = identifier;
        this.currentCardId = currentCardId;
    }

    @NonNull
    public static CarouselIndicatorModel fromJson(@NonNull JsonMap json) {
        String identifier = json.opt("carouselIdentifier").optString();
        String currentCardId = json.opt("currentCardId").optString();

        return new CarouselIndicatorModel(identifier, currentCardId);
    }

    //
    // Fields
    //

    @NonNull
    public String getIdentifier() {
        return identifier;
    }

    @NonNull
    public String getCurrentCardId() {
        return currentCardId;
    }

    //
    // State
    //

    public int getSize() {
        return size;
    }

    public int getPosition() {
        return position;
    }

    //
    // View Listener
    //

    public interface Listener {
        void onInit(int size, int position);
        void onUpdate(int position);
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;

        if (listener != null && size != -1 && position != -1) {
            listener.onInit(size, position);
        }
    }

    //
    // View Actions
    //

    public void onIndicatorClick(int position) {
        bubbleEvent(new Event.CarouselIndicatorClick(this, position));
    }

    //
    // Events
    //

    @Override
    public boolean onEvent(@NonNull Event event) {
        switch (event.getType()) {
            case CAROUSEL_INIT:
                if (handleCarouselInit((Event.CarouselInit) event)) { return true; }
                break;
            case CAROUSEL_SCROLL:
                if (handleCarouselScroll((Event.CarouselScroll) event)) { return true; }
                break;
        }
        return super.onEvent(event);
    }

    private boolean handleCarouselInit(Event.CarouselInit event) {
        // Bail if this event is for another carousel indicator.
        if (!event.getCarouselId().equals(identifier)) { return false; }

        Logger.verbose("onCarouselInit: size = %d, position = %d, id = %s", event.getSize(), event.getPosition(), event.getCarouselId());

        // Set the size and current position from the event data.
        size = event.getSize();
        position = event.getPosition();

        if (listener != null) {
            listener.onInit(size, position);
        }
        return true;
    }

    private boolean handleCarouselScroll(Event.CarouselScroll event) {
        // Bail if this event is for another carousel indicator.
        if (!event.getCarouselId().equals(identifier)) { return false; }

        Logger.verbose("onCarouselScroll: position = %d, id = %s", event.getPosition(), event.getCarouselId());

        // Update the current position from the event data.
        position = event.getPosition();

        if (listener != null) {
            listener.onUpdate(position);
        }
        return true;
    }
}
