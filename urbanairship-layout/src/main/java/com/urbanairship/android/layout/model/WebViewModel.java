/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import android.graphics.Color;

import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonMap;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class WebViewModel extends BaseModel {
    @NonNull
    private final String url;

    @Nullable
    @ColorInt
    private final Integer backgroundColor; // TODO: what is this for? loading screen background?

    public WebViewModel(@NonNull String url, @Nullable @ColorInt Integer backgroundColor) {
        super(ViewType.WEB_VIEW);

        this.url = url;
        this.backgroundColor = backgroundColor;
    }

    @NonNull
    public static WebViewModel fromJson(@NonNull JsonMap json) {
        String url = json.opt("url").optString();
        String colorString = json.opt("backgroundColor").optString();

        @ColorInt Integer backgroundColor = colorString.isEmpty() ? null : Color.parseColor(colorString);

        return new WebViewModel(url, backgroundColor);
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    @Nullable
    @ColorInt
    public Integer getBackgroundColor() {
        return backgroundColor;
    }
}
