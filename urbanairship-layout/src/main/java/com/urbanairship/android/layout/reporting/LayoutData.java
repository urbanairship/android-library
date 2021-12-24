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
    private final Boolean isFormSubmitted;

    @Nullable
    private final PagerData pagerData;

    public LayoutData(@Nullable String formId,
                      @Nullable Boolean isFormSubmitted,
                      @Nullable PagerData pagerData) {
        this.formId = formId;
        this.isFormSubmitted = isFormSubmitted;
        this.pagerData = pagerData;
    }

    @Nullable
    public String getFormId() {
        return formId;
    }

    @Nullable
    public Boolean getFormSubmitted() {
        return isFormSubmitted;
    }

    @Nullable
    public PagerData getPagerData() {
        return pagerData;
    }

    @NonNull
    public LayoutData withFormData(@NonNull String id, boolean isSubmitted) {
        return new LayoutData(id, isSubmitted, pagerData);
    }

    @NonNull
    public LayoutData withPagerData(@NonNull PagerData data) {
        return new LayoutData(formId, isFormSubmitted, data);
    }

    @NonNull
    @Override
    public String toString() {
        return "LayoutData{" +
            "formId='" + formId + '\'' +
            ", isFormSubmitted='" + isFormSubmitted + '\'' +
            ", pagerData=" + pagerData +
            '}';
    }
}
