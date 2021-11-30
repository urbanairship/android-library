/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.ToggleStyle;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class RadioInputModel extends BaseModel implements Accessible {
    @NonNull
    private final ToggleStyle style;
    @NonNull
    private final String reportingValue;
    @Nullable
    private final String contentDescription;

    public RadioInputModel(
        @NonNull ToggleStyle style,
        @NonNull String reportingValue,
        @Nullable String contentDescription,
        @Nullable Color backgroundColor,
        @Nullable Border border
    ) {
        super(ViewType.RADIO_INPUT, backgroundColor, border);

        this.style = style;
        this.reportingValue = reportingValue;
        this.contentDescription = contentDescription;
    }

    @NonNull
    public static RadioInputModel fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap styleJson = json.opt("style").optMap();
        ToggleStyle style = ToggleStyle.fromJson(styleJson);
        String reportingValue = json.opt("value").optString();

        String contentDescription = Accessible.contentDescriptionFromJson(json);
        Color backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);

        return new RadioInputModel(style, reportingValue, contentDescription, backgroundColor, border);
    }

    @NonNull
    public ToggleStyle getStyle() {
        return style;
    }

    /** Value for reports. */
    @NonNull
    public String getReportingValue() {
        return reportingValue;
    }

    @Override
    @Nullable
    public String getContentDescription() {
        return contentDescription;
    }
}
