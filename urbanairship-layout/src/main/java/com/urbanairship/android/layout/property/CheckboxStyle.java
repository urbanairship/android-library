/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CheckboxStyle extends ToggleStyle {

    @ColorInt
    private final int checkMarkColor;
    @Nullable
    @ColorInt
    private final Integer checkedBorderColor;
    @Nullable
    @ColorInt
    private final Integer checkedBackgroundColor;

    public CheckboxStyle(
        @ColorInt int checkMarkColor,
        @ColorInt @Nullable Integer checkedBorderColor,
        @ColorInt @Nullable Integer checkedBackgroundColor
    ) {
        super(ToggleType.CHECKBOX);

        this.checkMarkColor = checkMarkColor;
        this.checkedBorderColor = checkedBorderColor;
        this.checkedBackgroundColor = checkedBackgroundColor;
    }

    @NonNull
    public static CheckboxStyle fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap colors = json.opt("checked_colors").optMap();
        @ColorInt Integer checkMarkColor = Color.fromJsonField(colors, "check_mark");
        if (checkMarkColor == null) {
            throw new JsonException("Failed to parse CheckboxStyle! Field 'checked_colors.check_mark' may not be null.");
        }
        @ColorInt Integer borderColor = Color.fromJsonField(colors, "border");
        @ColorInt Integer backgroundColor = Color.fromJsonField(colors, "background");

        return new CheckboxStyle(checkMarkColor, borderColor, backgroundColor);
    }

    /** The color of the check mark. */
    @ColorInt
    public int getCheckMarkColor() {
        return checkMarkColor;
    }

    /** Border color override when checked. Null means no override. */
    @ColorInt
    @Nullable
    public Integer getCheckedBorderColor() {
        return checkedBorderColor;
    }

    /** Background color override when checked. Null means no override. */
    @ColorInt
    @Nullable
    public Integer getCheckedBackgroundColor() {
        return checkedBackgroundColor;
    }
}
