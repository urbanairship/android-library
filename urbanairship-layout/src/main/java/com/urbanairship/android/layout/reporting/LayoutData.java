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
    private final String formId;

    @Nullable
    private final PagerData pagerData;

    public LayoutData(@Nullable String formId,
                      @Nullable PagerData pagerData) {
        this.formId = formId;
        this.pagerData = pagerData;
    }

    @Nullable
    public String getFormId() {
        return formId;
    }

    @Nullable
    public PagerData getPagerData() {
        return pagerData;
    }

    @NonNull
    public LayoutData withFormId(@NonNull String id) {
        return new LayoutData(id, pagerData);
    }

    @NonNull
    public LayoutData withPagerData(@NonNull PagerData data) {
        return new LayoutData(formId, data);
    }

    @NonNull
    @Override
    public String toString() {
        return "LayoutData{" +
            "formId='" + formId + '\'' +
            ", pagerData=" + pagerData +
            '}';
    }
}
