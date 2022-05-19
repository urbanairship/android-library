/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import android.view.View;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.PagerEvent;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.Image.Icon;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.android.layout.shape.Shape;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    private final HashMap<Integer, Integer> indicatorViewIds = new HashMap<>();

    public PagerIndicatorModel(@NonNull Bindings bindings, int indicatorSpacing,
                               @Nullable Color backgroundColor, @Nullable Border border) {
        super(ViewType.PAGER_INDICATOR, backgroundColor, border);

        this.bindings = bindings;
        this.indicatorSpacing = indicatorSpacing;
    }

    @NonNull
    public static PagerIndicatorModel fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap bindingsJson = json.opt("bindings").optMap();
        Bindings bindings = Bindings.fromJson(bindingsJson);
        int indicatorSpacing = json.opt("spacing").getInt(4);
        Border border = borderFromJson(json);
        Color backgroundColor = backgroundColorFromJson(json);

        return new PagerIndicatorModel(bindings, indicatorSpacing, backgroundColor, border);
    }

    public static class Bindings {
        @NonNull
        private final Binding selected;
        @NonNull
        private final Binding unselected;

        Bindings(@NonNull Binding selected, @NonNull Binding unselected) {
            this.selected = selected;
            this.unselected = unselected;
        }

        public static Bindings fromJson(@NonNull JsonMap json) throws JsonException {
            JsonMap selectedJson = json.opt("selected").optMap();
            JsonMap unselectedJson = json.opt("unselected").optMap();

            Binding selected = Binding.fromJson(selectedJson);
            Binding unselected = Binding.fromJson(unselectedJson);

            return new Bindings(selected, unselected);
        }

        @NonNull
        public Binding getSelected() {
            return selected;
        }

        @NonNull
        public Binding getUnselected() {
            return unselected;
        }
    }

    public static class Binding {
        @NonNull
        private final List<Shape> shapes;
        @Nullable
        private final Icon icon;

        public Binding(@NonNull List<Shape> shapes, @Nullable Icon icon) {
            this.shapes = shapes;
            this.icon = icon;
        }

        @NonNull
        public static Binding fromJson(@NonNull JsonMap json) throws JsonException {
            JsonList shapesJson = json.opt("shapes").optList();
            JsonMap iconJson = json.opt("icon").optMap();

            List<Shape> shapes = new ArrayList<>();
            for (int i = 0; i < shapesJson.size(); i++) {
                JsonMap shapeJson = shapesJson.get(i).optMap();
                Shape shape = Shape.fromJson(shapeJson);
                shapes.add(shape);
            }
            Icon icon = iconJson.isEmpty() ? null : Icon.fromJson(iconJson);

            return new Binding(shapes, icon);
        }

        @NonNull
        public List<Shape> getShapes() {
            return shapes;
        }

        @Nullable
        public Icon getIcon() {
            return icon;
        }
    }

    /** Returns a stable viewId for the indicator view at the given {@code position}. */
    public int getIndicatorViewId(int position) {
        Integer viewId = null;
        if (indicatorViewIds.containsKey(position)) {
            viewId = indicatorViewIds.get(position);
        }
        if (viewId == null) {
            viewId = View.generateViewId();
            indicatorViewIds.put(position, viewId);
        }
        return viewId;
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
    // View Actions
    //

    public void onConfigured() {
        bubbleEvent(new PagerEvent.IndicatorInit(this), LayoutData.empty());
    }

    //
    // Events
    //

    @Override
    public boolean onEvent(@NonNull Event event, @NonNull LayoutData layoutData) {
        Logger.verbose("onEvent: %s layoutData: %s", event, layoutData);

        switch (event.getType()) {
            case PAGER_INIT:
                if (onPagerInit((PagerEvent.Init) event)) { return true; }
                break;
            case PAGER_SCROLL:
                if (onPagerScroll((PagerEvent.Scroll) event)) { return true; }
                break;
        }
        return super.onEvent(event, layoutData);
    }

    private boolean onPagerInit(PagerEvent.Init event) {
        // Set the size and current position from the event data.
        size = event.getSize();
        position = event.getPageIndex();

        if (listener != null) {
            listener.onInit(size, position);
        }
        return true;
    }

    private boolean onPagerScroll(PagerEvent.Scroll event) {
        // Update the current position from the event data.
        position = event.getPageIndex();

        if (listener != null) {
            listener.onUpdate(position);
        }
        return true;
    }
}
