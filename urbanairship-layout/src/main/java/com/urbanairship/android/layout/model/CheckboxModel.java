/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.event.CheckboxEvent;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.ToggleStyle;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.urbanairship.android.layout.model.Accessible.contentDescriptionFromJson;

/**
 * Checkbox input for use within a {@code CheckboxController}.
 */
public class CheckboxModel extends CheckableModel {
    @NonNull
    private final JsonValue reportingValue;

    public CheckboxModel(
        @NonNull JsonValue reportingValue,
        @NonNull ToggleStyle style,
        @Nullable String contentDescription,
        @Nullable Color backgroundColor,
        @Nullable Border border
    ) {
        super(ViewType.CHECKBOX, style, contentDescription, backgroundColor, border);

        this.reportingValue = reportingValue;
    }

    @NonNull
    public static CheckboxModel fromJson(@NonNull JsonMap json) throws JsonException {
        JsonValue reportingValue = json.opt("reporting_value").toJsonValue();
        ToggleStyle style = toggleStyleFromJson(json);
        String contentDescription = contentDescriptionFromJson(json);
        Color backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);

        return new CheckboxModel(reportingValue, style, contentDescription, backgroundColor, border);
    }

    @NonNull
    public JsonValue getReportingValue() {
        return reportingValue;
    }

    @NonNull
    @Override
    public Event buildInputChangeEvent(boolean isChecked) {
        return new CheckboxEvent.InputChange(reportingValue, isChecked);
    }

    @NonNull
    @Override
    public Event buildInitEvent() {
        return new Event.ViewInit(this);
    }

    @Override
    public boolean onEvent(@NonNull Event event, @NonNull LayoutData layoutData) {
        switch (event.getType()) {
            case CHECKBOX_VIEW_UPDATE:
                CheckboxEvent.ViewUpdate update = (CheckboxEvent.ViewUpdate) event;
                if (reportingValue.equals(update.getValue())) {
                    setChecked(update.isChecked());
                }
                // Don't consume the event so it can be handled by siblings.
                return false;

            default:
                return super.onEvent(event, layoutData);
        }
    }
}
