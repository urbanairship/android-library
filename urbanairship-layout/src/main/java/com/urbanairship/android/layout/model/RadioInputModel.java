/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.RadioEvent;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.ToggleStyle;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.urbanairship.android.layout.model.Accessible.contentDescriptionFromJson;

public class RadioInputModel extends CheckableModel {

    @NonNull
    private final String reportingValue;

    @Nullable
    private Listener listener;

    public RadioInputModel(
        @NonNull ToggleStyle style,
        @NonNull String reportingValue,
        @Nullable String contentDescription,
        @Nullable Color backgroundColor,
        @Nullable Border border
    ) {
        super(ViewType.RADIO_INPUT, style, contentDescription, backgroundColor, border);

        this.reportingValue = reportingValue;
    }

    @NonNull
    public static RadioInputModel fromJson(@NonNull JsonMap json) throws JsonException {
        ToggleStyle style = toggleStyleFromJson(json);
        String reportingValue = json.opt("value").optString();
        String contentDescription = contentDescriptionFromJson(json);
        Color backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);

        return new RadioInputModel(style, reportingValue, contentDescription, backgroundColor, border);
    }

    /** Value for reports. */
    @NonNull
    public String getReportingValue() {
        return reportingValue;
    }

    @NonNull
    @Override
    public Event buildInputChangeEvent(boolean isChecked) {
        return new RadioEvent.InputChange(reportingValue, isChecked);
    }

    @NonNull
    @Override
    public Event buildInitEvent() {
        return new Event.ViewInit(this);
    }

    @Override
    public boolean onEvent(@NonNull Event event) {
        Logger.verbose("onEvent: %s", event.getType());

        switch (event.getType()) {
            case RADIO_VIEW_UPDATE:
                RadioEvent.ViewUpdate update = (RadioEvent.ViewUpdate) event;
                setChecked(reportingValue.equals(update.getValue()));

                // Don't consume the event so it can be handled by siblings.
                return false;

            default:
                return super.onEvent(event);
        }
    }
}
