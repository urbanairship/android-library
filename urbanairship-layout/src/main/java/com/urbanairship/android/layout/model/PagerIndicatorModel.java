/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.shape.Shape;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.ColorInt;
import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static androidx.annotation.Dimension.DP;

public class PagerIndicatorModel extends BaseModel {

    @NonNull
    private final Bindings bindings;
    private final int indicatorSpacing;

    private int size = -1;
    private int position = -1;
    @Nullable
    private Listener listener;

    public PagerIndicatorModel(@NonNull Bindings bindings, int indicatorSpacing,
                               @Nullable @ColorInt Integer backgroundColor, @Nullable Border border) {
        super(ViewType.PAGER_INDICATOR, backgroundColor, border);

        this.bindings = bindings;
        this.indicatorSpacing = indicatorSpacing;
    }

    @NonNull
    public static PagerIndicatorModel fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap bindingsJson = json.opt("indicator_bindings").optMap();
        Bindings bindings = Bindings.fromJson(bindingsJson);
        int indicatorSpacing = json.opt("indicator_spacing").getInt(4);
        Border border = borderFromJson(json);
        @ColorInt Integer backgroundColor = backgroundColorFromJson(json);

        return new PagerIndicatorModel(bindings, indicatorSpacing, backgroundColor, border);
    }

    public static class Bindings {
        @NonNull
        private final Shape selected;
        @NonNull
        private final Shape deselected;

        Bindings(@NonNull Shape selected, @NonNull Shape deselected) {
            this.selected = selected;
            this.deselected = deselected;
        }

        public static Bindings fromJson(@NonNull JsonMap json) throws JsonException {
            JsonMap selectedJson = json.opt("selected").optMap();
            JsonMap deselectedJson = json.opt("deselected").optMap();

            Shape selected = Shape.fromJson(selectedJson);
            Shape deselected = Shape.fromJson(deselectedJson);

            return new Bindings(selected, deselected);
        }

        @NonNull
        public Shape getSelected() {
            return selected;
        }

        @NonNull
        public Shape getDeselected() {
            return deselected;
        }
    }

    //
    // Fields
    //

    @NonNull
    public Bindings getBindings() {
        return bindings;
    }

    @Dimension(unit = DP)
    public int getIndicatorSpacing() {
        return indicatorSpacing;
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
    // Events
    //

    @Override
    public boolean onEvent(@NonNull Event event) {
        switch (event.getType()) {
            case PAGER_INIT:
                if (handleCarouselInit((Event.PagerInit) event)) { return true; }
                break;
            case PAGER_SCROLL:
                if (handleCarouselScroll((Event.PagerScroll) event)) { return true; }
                break;
        }
        return super.onEvent(event);
    }

    private boolean handleCarouselInit(Event.PagerInit event) {
        // Set the size and current position from the event data.
        size = event.getSize();
        position = event.getPosition();

        if (listener != null) {
            listener.onInit(size, position);
        }
        return true;
    }

    private boolean handleCarouselScroll(Event.PagerScroll event) {
        // Update the current position from the event data.
        position = event.getPosition();

        if (listener != null) {
            listener.onUpdate(position);
        }
        return true;
    }
}
