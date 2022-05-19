/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.RadioEvent;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.ToggleStyle;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.urbanairship.android.layout.model.Accessible.contentDescriptionFromJson;

public class RadioInputModel extends CheckableModel {

    @NonNull
    private final JsonValue reportingValue;
    @NonNull
    private final JsonValue attributeValue;

    public RadioInputModel(
        @NonNull ToggleStyle style,
        @NonNull JsonValue reportingValue,
        @NonNull JsonValue attributeValue,
        @Nullable String contentDescription,
        @Nullable Color backgroundColor,
        @Nullable Border border
    ) {
        super(ViewType.RADIO_INPUT, style, contentDescription, backgroundColor, border);

        this.reportingValue = reportingValue;
        this.attributeValue = attributeValue;
    }

    @NonNull
    public static RadioInputModel fromJson(@NonNull JsonMap json) throws JsonException {
        ToggleStyle style = toggleStyleFromJson(json);
        JsonValue reportingValue = json.opt("reporting_value");
        JsonValue attributeValue = json.opt("attribute_value");
        String contentDescription = contentDescriptionFromJson(json);
        Color backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);

        return new RadioInputModel(style, reportingValue, attributeValue, contentDescription, backgroundColor, border);
    }

    /** Value for reports. */
    @NonNull
    public JsonValue getReportingValue() {
        return reportingValue;
    }

    @NonNull
    public JsonValue getAttributeValue() {
        return attributeValue;
    }

    @NonNull
    @Override
    public Event buildInputChangeEvent(boolean isChecked) {
        return new RadioEvent.InputChange(reportingValue, attributeValue, isChecked);
    }

    @NonNull
    @Override
    public Event buildInitEvent() {
        return new Event.ViewInit(this);
    }

    @Override
    public boolean onEvent(@NonNull Event event, @NonNull LayoutData layoutData) {
        switch (event.getType()) {
            case RADIO_VIEW_UPDATE:
                RadioEvent.ViewUpdate update = (RadioEvent.ViewUpdate) event;
                setChecked(reportingValue.equals(update.getValue()));

                // Don't consume the event so it can be handled by siblings.
                return false;

            default:
                return super.onEvent(event, layoutData);
        }
    }
}
