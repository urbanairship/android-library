/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.Layout;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CarouselModel extends BaseModel {
    @NonNull
    private final String identifier;
    @NonNull
    private final List<BaseModel> items;

    @Nullable
    private Listener listener;

    public CarouselModel(@NonNull String identifier, @NonNull List<BaseModel> items) {
        super(ViewType.CAROUSEL);

        this.identifier = identifier;
        this.items = items;
    }

    @NonNull
    public static CarouselModel fromJson(@NonNull JsonMap json) throws JsonException {
        String identifier = json.opt("identifier").optString();
        JsonList itemsJson = json.opt("items").optList();

        List<BaseModel> items = new ArrayList<>(itemsJson.size());
        for (int i = 0; i < itemsJson.size(); i++) {
            JsonMap itemJson = itemsJson.get(i).optMap();
            BaseModel item = Layout.model(itemJson);
            items.add(item);
        }

        return new CarouselModel(identifier, items);
    }

    //
    // Fields
    //

    @NonNull
    public String getIdentifier() {
        return identifier;
    }

    @NonNull
    public List<BaseModel> getItems() {
        return items;
    }

    //
    // View Listener
    //

    public interface Listener {
        void setDisplayedItem(int position);
    }

    public void setListener(@NonNull Listener listener) {
        this.listener = listener;
    }

    //
    // View Actions
    //

    public void onScrollTo(int position) {
        bubbleEvent(new Event.CarouselScroll(this, position));
    }

    public void onConfigured(int position) {
        bubbleEvent(new Event.CarouselInit(this, position));
    }

    //
    // Events
    //

    @Override
    public boolean onEvent(@NonNull Event event) {
        switch (event.getType()) {
            case CAROUSEL_INDICATOR_CLICK:
                if (handleCarouselIndicatorClick((Event.CarouselIndicatorClick) event)) { return true; }
                break;
        }
        return super.onEvent(event);
    }

    private boolean handleCarouselIndicatorClick(@NonNull Event.CarouselIndicatorClick event) {
        // Bail if this event is for another carousel.
        if (!event.getCarouselId().equals(getIdentifier())) { return false; }

        if (listener != null) {
            listener.setDisplayedItem(event.getPosition());
        }
        return true;
    }
}
