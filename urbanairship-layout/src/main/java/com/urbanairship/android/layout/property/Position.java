/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import android.view.Gravity;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;

public class Position {

    @NonNull
    public static final Position CENTER =
            new Position(HorizontalPosition.CENTER, VerticalPosition.CENTER);
    @NonNull
    private final HorizontalPosition horizontal;
    @NonNull
    private final VerticalPosition vertical;

    public Position(@NonNull HorizontalPosition horizontal, @NonNull VerticalPosition vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
    }
    @NonNull
    public static Position fromJson(@NonNull JsonMap json) throws JsonException {
        String horizontalJson = json.opt("horizontal").optString();
        String verticalJson = json.opt("vertical").optString();

        HorizontalPosition horizontal = HorizontalPosition.from(horizontalJson);
        VerticalPosition vertical = VerticalPosition.from(verticalJson);

        return new Position(horizontal, vertical);
    }

    @NonNull
    public HorizontalPosition getHorizontal() {
        return horizontal;
    }

    @NonNull
    public VerticalPosition getVertical() {
        return vertical;
    }

    public int getGravity() {
        return Gravity.CENTER | horizontal.getGravity() | vertical.getGravity();
    }
}
