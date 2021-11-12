/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.ToggleStyle;
import com.urbanairship.android.layout.property.ToggleType;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Checkbox input for use within a {@code CheckboxController}.
 */
public class CheckboxModel extends BaseModel implements Accessible {
    @NonNull
    private final String reportingValue;
    @NonNull
    private final ToggleStyle toggleStyle;
    @Nullable
    private final String contentDescription;

    public CheckboxModel(
        @NonNull String reportingValue,
        @NonNull ToggleStyle toggleStyle,
        @Nullable String contentDescription,
        @Nullable @ColorInt Integer backgroundColor,
        @Nullable Border border
    ) {
        super(ViewType.CHECKBOX, backgroundColor, border);

        this.reportingValue = reportingValue;
        this.toggleStyle = toggleStyle;
        this.contentDescription = contentDescription;
    }

    @NonNull
    public static CheckboxModel fromJson(@NonNull JsonMap json) throws JsonException {
        String reportingValue = json.opt("value").optString();
        JsonMap toggleStyleJson = json.opt("toggle_style").optMap();
        ToggleStyle toggleStyle = ToggleStyle.fromJson(toggleStyleJson);
        String contentDescription = Accessible.contentDescriptionFromJson(json);
        @ColorInt Integer backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);

        return new CheckboxModel(reportingValue, toggleStyle, contentDescription, backgroundColor, border);
    }

    @NonNull
    public String getReportingValue() {
        return reportingValue;
    }

    @NonNull
    public ToggleStyle getToggleStyle() {
        return toggleStyle;
    }

    @NonNull
    public ToggleType getToggleType() {
        return toggleStyle.getType();
    }

    @Nullable
    public String getContentDescription() {
        return contentDescription;
    }
}
