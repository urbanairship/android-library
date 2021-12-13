/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.ToggleStyle;
import com.urbanairship.android.layout.property.ToggleType;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class CheckableModel extends BaseModel implements Accessible {

    @NonNull
    private final ToggleStyle style;
    @Nullable
    private final String contentDescription;

    @Nullable
    private Listener listener = null;

    public CheckableModel(
        @NonNull ViewType viewType,
        @NonNull ToggleStyle style,
        @Nullable String contentDescription,
        @Nullable Color backgroundColor,
        @Nullable Border border
    ) {
        super(viewType, backgroundColor, border);

        this.style = style;
        this.contentDescription = contentDescription;
    }

    @NonNull
    protected static ToggleStyle toggleStyleFromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap styleJson = json.opt("style").optMap();
        return ToggleStyle.fromJson(styleJson);
    }

    @NonNull
    public ToggleStyle getStyle() {
        return style;
    }

    @NonNull
    public ToggleType getToggleType() {
        return style.getType();
    }

    @Nullable
    @Override
    public String getContentDescription() {
        return contentDescription;
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void setChecked(boolean isChecked) {
        if (listener != null) {
            listener.onSetChecked(isChecked);
        }
    }

    public void onConfigured() {
        bubbleEvent(buildInitEvent());
    }

    public void onAttachedToWindow() {
        bubbleEvent(new Event.ViewAttachedToWindow(this));
    }

    public void onCheckedChange(boolean isChecked) {
        bubbleEvent(buildInputChangeEvent(isChecked));
    }

    @NonNull
    public abstract Event buildInputChangeEvent(boolean isChecked);

    @NonNull
    public abstract Event buildInitEvent();

    public interface Listener {
        void onSetChecked(boolean isChecked);
    }
}
