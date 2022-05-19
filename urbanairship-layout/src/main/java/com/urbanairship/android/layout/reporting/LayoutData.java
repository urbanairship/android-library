/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.reporting;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Layout state of an event.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class LayoutData {

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

    @NonNull
    public LayoutData withFormInfo(@NonNull FormInfo formInfo) {
        return new LayoutData(formInfo, pagerData, buttonIdentifier);
    }

    @NonNull
    public LayoutData withPagerData(@NonNull PagerData data) {
        return new LayoutData(formInfo, data, buttonIdentifier);
    }

    @Override
    public String toString() {
        return "LayoutData{" +
                "formInfo=" + formInfo +
                ", pagerData=" + pagerData +
                ", buttonIdentifier='" + buttonIdentifier + '\'' +
                '}';
    }

}
