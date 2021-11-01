/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import android.graphics.Color;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.EventListener;
import com.urbanairship.android.layout.event.EventSource;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonMap;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class BaseModel implements EventSource, EventListener {
    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();

    @NonNull
    private final ViewType viewType;

    @Nullable
    @ColorInt
    private final Integer backgroundColor;

    @Nullable
    private final Border border;

    public BaseModel(@NonNull ViewType viewType) {
        this(viewType, null, null);
    }

    public BaseModel(@NonNull ViewType viewType, @Nullable @ColorInt Integer backgroundColor, @Nullable Border border) {
        this.viewType = viewType;
        this.backgroundColor = backgroundColor;
        this.border = border;
    }

    @NonNull
    public ViewType getType() {
        return viewType;
    }

    @Nullable
    public Integer getBackgroundColor() {
        return backgroundColor;
    }

    @Nullable
    public Border getBorder() {
        return border;
    }

    @ColorInt
    public static Integer backgroundColorFromJson(@NonNull JsonMap json) {
        String colorString = json.opt("backgroundColor").optString();
        return colorString.isEmpty() ? null : Color.parseColor(colorString);
    }

    @Nullable
    public static Border borderFromJson(@NonNull JsonMap json) {
        JsonMap borderJson = json.opt("border").optMap();
        return borderJson.isEmpty() ? null : Border.fromJson(borderJson);
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
        Logger.verbose("%s - bubbleEvent: %s", getType(), event.getType().name());

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
        Logger.verbose("%s - trickleEvent: %s", getType(), event.getType().name());
        return onEvent(event);
    }
}
