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

    @Nullable
    private final FormInfo formInfo;


    @Nullable
    private final PagerData pagerData;

    public LayoutData(@Nullable FormInfo formInfo,
                      @Nullable PagerData pagerData) {
        this.formInfo = formInfo;
        this.pagerData = pagerData;
    }

    @Nullable
    public FormInfo getFormInfo() {
        return formInfo;
    }

    @Nullable
    public PagerData getPagerData() {
        return pagerData;
    }

    @NonNull
    public LayoutData withFormInfo(@NonNull FormInfo formInfo) {
        return new LayoutData(formInfo, pagerData);
    }

    @NonNull
    public LayoutData withPagerData(@NonNull PagerData data) {
        return new LayoutData(formInfo, data);
    }

    @Override
    public String toString() {
        return "LayoutData{" +
                "formInfo=" + formInfo +
                ", pagerData=" + pagerData +
                '}';
    }

}
