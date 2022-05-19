/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import android.os.Bundle;

import com.urbanairship.android.layout.event.WebViewEvent;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class WebViewModel extends BaseModel {
    @NonNull
    private final String url;

    @Nullable
    private Bundle savedState;

    public WebViewModel(@NonNull String url, @Nullable Color backgroundColor, @Nullable Border border) {
        super(ViewType.WEB_VIEW, backgroundColor, border);

        this.url = url;
    }

    @NonNull
    public static WebViewModel fromJson(@NonNull JsonMap json) throws JsonException {
        String url = json.opt("url").optString();
        Color backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);

        return new WebViewModel(url, backgroundColor, border);
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    public void onClose() {
        bubbleEvent(new WebViewEvent.Close(), LayoutData.empty());
    }

    public void saveState(@NonNull Bundle bundle) {
        savedState = bundle;
    }

    @Nullable
    public Bundle getSavedState() {
        return savedState;
    }
}
