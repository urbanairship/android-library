/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;

public class Margin {
    private final int top;
    private final int bottom;
    private final int start;
    private final int end;

    public Margin(int top, int bottom, int start, int end) {
        this.top = top;
        this.bottom = bottom;
        this.start = start;
        this.end = end;
    }

    @NonNull
    public static Margin fromJson(@NonNull JsonMap json) {
        int top = json.opt("top").getInt(0);
        int bottom = json.opt("bottom").getInt(0);
        int start = json.opt("start").getInt(0);
        int end = json.opt("end").getInt(0);

        return new Margin(top, bottom, start, end);
    }

    public int getTop() {
        return top;
    }

    public int getBottom() {
        return bottom;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }
}
