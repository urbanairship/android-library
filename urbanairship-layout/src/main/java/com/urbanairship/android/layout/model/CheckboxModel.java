/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.event.CheckboxEvent;
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

/**
 * Checkbox input for use within a {@code CheckboxController}.
 */
public class CheckboxModel extends BaseModel implements Accessible {
    @NonNull
    private final String reportingValue;
    @NonNull
    private final ToggleStyle style;
    @Nullable
    private final String contentDescription;

    @Nullable
    private Listener listener = null;

    public CheckboxModel(
        @NonNull String reportingValue,
        @NonNull ToggleStyle toggleStyle,
        @Nullable String contentDescription,
        @Nullable Color backgroundColor,
        @Nullable Border border
    ) {
        super(ViewType.CHECKBOX, backgroundColor, border);

        this.reportingValue = reportingValue;
        this.style = toggleStyle;
        this.contentDescription = contentDescription;
    }

    @NonNull
    public static CheckboxModel fromJson(@NonNull JsonMap json) throws JsonException {
        String reportingValue = json.opt("value").optString();
        JsonMap styleJson = json.opt("style").optMap();
        ToggleStyle style = ToggleStyle.fromJson(styleJson);
        String contentDescription = Accessible.contentDescriptionFromJson(json);
        Color backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);

        return new CheckboxModel(reportingValue, style, contentDescription, backgroundColor, border);
    }

    @NonNull
    public String getReportingValue() {
        return reportingValue;
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
    public String getContentDescription() {
        return contentDescription;
    }

    public void onInit() {
        bubbleEvent(new Event.ViewInit(this));
    }

    public void onCheckedChange(boolean isChecked) {
        bubbleEvent(new CheckboxEvent.InputChange(reportingValue, isChecked));
    }

    public void setChecked(boolean isChecked) {
        if (listener != null) {
            listener.onSetChecked(isChecked);
        }
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public interface Listener {
        void onSetChecked(boolean isChecked);
    }

    @Override
    public boolean onEvent(@NonNull Event event) {
        switch (event.getType()) {
            case CHECKBOX_VIEW_UPDATE:
                CheckboxEvent.ViewUpdate update = (CheckboxEvent.ViewUpdate) event;
                if (reportingValue.equals(update.getValue())) {
                    setChecked(update.isChecked());
                }
                // Don't consume the event so it can be handled by siblings.
                return false;

            default:
                return super.onEvent(event);
        }
    }
}