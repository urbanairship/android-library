/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonMap;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class WebViewModel extends BaseModel {
    @NonNull
    private final String url;

    public WebViewModel(@NonNull String url, @Nullable @ColorInt Integer backgroundColor, @Nullable Border border) {
        super(ViewType.WEB_VIEW, backgroundColor, border);

        this.url = url;
    }

    @NonNull
    public static WebViewModel fromJson(@NonNull JsonMap json) {
        String url = json.opt("url").optString();
        @ColorInt Integer backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);

        return new WebViewModel(url, backgroundColor, border);
    }

    @NonNull
    public String getUrl() {
        return url;
    }
}
