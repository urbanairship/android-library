/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import android.view.View;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.EventListener;
import com.urbanairship.android.layout.event.EventSource;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class BaseModel implements EventSource, EventListener {
    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();

    @NonNull
    private final ViewType viewType;

    @Nullable
    private final Color backgroundColor;

    @Nullable
    private final Border border;

    private final int viewId;

    public BaseModel(@NonNull ViewType viewType, @Nullable Color backgroundColor, @Nullable Border border) {
        this.viewType = viewType;
        this.backgroundColor = backgroundColor;
        this.border = border;

        this.viewId = View.generateViewId();
    }

    @NonNull
    public ViewType getType() {
        return viewType;
    }

    @Nullable
    public Color getBackgroundColor() {
        return backgroundColor;
    }

    @Nullable
    public Border getBorder() {
        return border;
    }

    @Nullable
    public static Color backgroundColorFromJson(@NonNull JsonMap json) throws JsonException {
        return Color.fromJsonField(json, "background_color");
    }

    @Nullable
    public static Border borderFromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap borderJson = json.opt("border").optMap();
        return borderJson.isEmpty() ? null : Border.fromJson(borderJson);
    }

    public int getViewId() {
        return viewId;
    }

    //
    // EventSource impl
    //

    /**
     * {@inheritDoc}
     */
    @Override
    public void addListener(EventListener listener) {
        listeners.add(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeListener(EventListener listener) {
        listeners.remove(listener);
    }

    /** Sets an event listener, removing any previously set listeners. */
    @Override
    public void setListener(EventListener listener) {
        listeners.clear();
        listeners.add(listener);
    }

    //
    // EventListener impl
    //

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onEvent(@NonNull Event event) {
        return false;
    }

    //
    // Event propagation helpers
    //

    /**
     * Bubbles the given {@code Event} to any upstream listeners.
     *
     * @param event The {@code Event} to bubble up.
     * @return {@code true} if the event was handled upstream, {@code false} otherwise.
     */
    protected boolean bubbleEvent(@NonNull Event event) {
        for (EventListener listener : listeners) {
            if (listener.onEvent(event)) { return true; }
        }
        return false;
    }

    /**
     * Trickles the given {@code Event} to any downstream listeners.
     *
     * @param event The {@code Event} to trickle down.
     * @return {@code true} if the event was handled downstream, {@code false} otherwise.
     */
    protected boolean trickleEvent(@NonNull Event event) {
        return onEvent(event);
    }
}
