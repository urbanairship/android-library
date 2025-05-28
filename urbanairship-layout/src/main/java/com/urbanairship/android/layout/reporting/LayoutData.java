/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.reporting;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Layout state of an event.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class LayoutData implements JsonSerializable {

    private static LayoutData EMPTY = new LayoutData(null, null, null);

    @Nullable
    private final FormInfo formInfo;

    @Nullable
    private final PagerData pagerData;

    @Nullable
    private final String buttonIdentifier;

    public LayoutData(@Nullable FormInfo formInfo,
                      @Nullable PagerData pagerData,
                      @Nullable String buttonIdentifier) {
        this.formInfo = formInfo;
        this.pagerData = pagerData;
        this.buttonIdentifier = buttonIdentifier;
    }

    public static LayoutData form(@Nullable FormInfo formInfo) {
        return new LayoutData(formInfo, null, null);
    }

    public static LayoutData pager(@Nullable PagerData pagerData) {
        return new LayoutData(null, pagerData, null);
    }

    public static LayoutData button(@Nullable String buttonIdentifier) {
        return new LayoutData(null, null, buttonIdentifier);
    }

    public static LayoutData empty() {
        return EMPTY;
    }


    @Nullable
    public FormInfo getFormInfo() {
        return formInfo;
    }

    @Nullable
    public PagerData getPagerData() {
        return pagerData;
    }

    @Nullable
    public String getButtonIdentifier() {
        return buttonIdentifier;
    }

    @Override
    public String toString() {
        return "LayoutData{" +
                "formInfo=" + formInfo +
                ", pagerData=" + pagerData +
                ", buttonIdentifier='" + buttonIdentifier + '\'' +
                '}';
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                .putOpt(KEY_FORM_INFO, formInfo)
                .putOpt(KEY_PAGER_DATA, pagerData)
                .putOpt(KEY_BUTTON_IDENTIFIER, buttonIdentifier)
                .build().toJsonValue();
    }

    private static final String KEY_FORM_INFO = "formInfo";
    private static final String KEY_PAGER_DATA = "pagerData";
    private static final String KEY_BUTTON_IDENTIFIER = "buttonIdentifier";
}
