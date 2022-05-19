/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import android.view.View;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.PagerEvent;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.urbanairship.android.layout.model.Identifiable.identifierFromJson;

public class PagerModel extends LayoutModel {
    @NonNull
    private final List<PagerModel.Item> items;

    @NonNull
    private final List<BaseModel> children = new ArrayList<>();

    private final boolean disableSwipe;

    @Nullable
    private Listener listener;

    private int lastIndex = 0;

    private final int recyclerViewId = View.generateViewId();

    private final HashMap<Integer, Integer> pageViewIds = new HashMap<>();

    public PagerModel(@NonNull List<PagerModel.Item> items, boolean disableSwipe, @Nullable Color backgroundColor, @Nullable Border border) {
        super(ViewType.PAGER, backgroundColor, border);

        this.items = items;
        this.disableSwipe = disableSwipe;

        for (PagerModel.Item item : items) {
            item.view.addListener(this);
            children.add(item.view);
        }
    }

    @NonNull
    public static PagerModel fromJson(@NonNull JsonMap json) throws JsonException {
        JsonList itemsJson = json.opt("items").optList();
        boolean disableSwipe = json.opt("disable_swipe").getBoolean(false);
        Color backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);
        List<PagerModel.Item> items = PagerModel.Item.fromJsonList(itemsJson);
        return new PagerModel(items, disableSwipe, backgroundColor, border);
    }

    @NonNull
    @Override
    public List<BaseModel> getChildren() {
        return children;
    }

    /** Stable viewId for the recycler view. */
    public int getRecyclerViewId() {
        return recyclerViewId;
    }

    /** Returns a stable viewId for the pager item view at the given adapter {@code position}. */
    public int getPageViewId(int position) {
        Integer viewId = null;
        if (pageViewIds.containsKey(position)) {
            viewId = pageViewIds.get(position);
        }
        if (viewId == null) {
            viewId = View.generateViewId();
            pageViewIds.put(position, viewId);
        }
        return viewId;
    }

    //
    // Fields
    //

    @NonNull
    public List<PagerModel.Item> getItems() {
        return items;
    }

    public boolean isSwipeDisabled() {
        return disableSwipe;
    }

    //
    // View Listener
    //

    public interface Listener {
        void onScrollToNext();
        void onScrollToPrevious();
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    //
    // View Actions
    //

    public void onScrollTo(int position, boolean isInternalScroll, long time) {
        // Bail if this is a duplicate scroll event, which can sometimes happen if
        // the user starts scrolling and then lets go without changing pages after a recreate.
        if (position == lastIndex) { return; }

        Item item = items.get(position);
        String pageId = item.identifier;
        Map<String, JsonValue> pageActions = item.actions;
        String previousPageId = this.items.get(lastIndex).identifier;

        bubbleEvent(new PagerEvent.Scroll(this, position, pageId, pageActions, lastIndex, previousPageId, isInternalScroll, time), LayoutData.empty());
        lastIndex = position;
    }

    public void onConfigured(int position, long time) {
        Item item = items.get(position);
        String pageId = item.identifier;
        Map<String, JsonValue> pageActions = item.actions;
        bubbleEvent(new PagerEvent.Init(this, position, pageId, pageActions, time), LayoutData.empty());
    }

    //
    // Events
    //

    @Override
    public boolean onEvent(@NonNull Event event, @NonNull LayoutData layoutData) {
        Logger.verbose("onEvent: %s, layoutData: %s", event, layoutData);
        return onEvent(event, layoutData, true);
    }

    private boolean onEvent(@NonNull Event event, @NonNull LayoutData layoutData, boolean bubbleIfUnhandled) {
        switch (event.getType()) {
            case BUTTON_BEHAVIOR_PAGER_NEXT:
                if (listener != null) {
                    listener.onScrollToNext();
                }
                return true;
            case BUTTON_BEHAVIOR_PAGER_PREVIOUS:
                if (listener != null) {
                    listener.onScrollToPrevious();
                }
                return true;
        }

        return bubbleIfUnhandled && super.onEvent(event, layoutData);
    }

    @Override
    public boolean trickleEvent(@NonNull Event event, @NonNull LayoutData layoutData) {
        if (onEvent(event, layoutData, false)) {
            return true;
        }

        return super.trickleEvent(event, layoutData);
    }

    public static class Item {
        @NonNull
        private final BaseModel view;
        @NonNull
        private final String identifier;
        @NonNull
        private final Map<String, JsonValue> actions;

        public Item(@NonNull BaseModel view, @NonNull String identifier, @NonNull Map<String, JsonValue> actions) {
            this.view = view;
            this.identifier = identifier;
            this.actions = actions;
        }

        @NonNull
        public static PagerModel.Item fromJson(@NonNull JsonMap json) throws JsonException {
            JsonMap viewJson = json.opt("view").optMap();
            String identifier = identifierFromJson(json);
            Map<String, JsonValue> actions = json.opt("display_actions").optMap().getMap();
            BaseModel view = Thomas.model(viewJson);

            return new PagerModel.Item(view, identifier, actions);
        }

        @NonNull
        public static List<PagerModel.Item> fromJsonList(@NonNull JsonList json) throws JsonException {
            List<PagerModel.Item> items = new ArrayList<>(json.size());
            for (int i = 0; i < json.size(); i++) {
                JsonMap itemJson = json.get(i).optMap();
                PagerModel.Item item = PagerModel.Item.fromJson(itemJson);
                items.add(item);
            }
            return items;
        }

        @NonNull
        public BaseModel getView() {
            return view;
        }

        @NonNull
        public String getIdentifier() {
            return identifier;
        }

    }
}
