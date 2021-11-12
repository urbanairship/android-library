/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class RadioInputModel extends BaseModel implements Accessible {
    @ColorInt
    private final int foregroundColor;
    @NonNull
    private final String reportingValue;
    @Nullable
    private final String contentDescription;

    public RadioInputModel(
        @ColorInt int foregroundColor,
        @NonNull String reportingValue,
        @Nullable String contentDescription,
        @Nullable @ColorInt Integer backgroundColor,
        @Nullable Border border
    ) {
        super(ViewType.RADIO_INPUT, backgroundColor, border);

        this.foregroundColor = foregroundColor;
        this.reportingValue = reportingValue;
        this.contentDescription = contentDescription;
    }

    @NonNull
    public static RadioInputModel fromJson(@NonNull JsonMap json) throws JsonException {
        @ColorInt Integer foregroundColor = Color.fromJsonField(json, "foreground_color");
        if (foregroundColor == null) {
            throw new JsonException("Failed to parse radio_input. 'foreground_color' may not be null!");
        }
        String reportingValue = json.opt("value").optString();

        String contentDescription = Accessible.contentDescriptionFromJson(json);
        @ColorInt Integer backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);

        return new RadioInputModel(foregroundColor, reportingValue, contentDescription, backgroundColor, border);
    }

    @ColorInt
    public int getForegroundColor() {
        return foregroundColor;
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
