package com.urbanairship.android.layout.reporting;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class FormInfo {
    @NonNull
    private final String identifier;

    @NonNull
    private final String formResponseType;

    @NonNull
    private final String formType;

    @Nullable
    private final Boolean isFormSubmitted;

    public FormInfo(@NonNull String identifier, @NonNull String formType, @Nullable String formResponseType, @Nullable Boolean isFormSubmitted) {
        this.identifier = identifier;
        this.formResponseType = formResponseType;
        this.formType = formType;
        this.isFormSubmitted = isFormSubmitted;
    }

    @NonNull
    public String getIdentifier() {
        return identifier;
    }

    @NonNull
    public String getFormResponseType() {
        return formResponseType;
    }

    @NonNull
    public String getFormType() {
        return formType;
    }

    @Nullable
    public Boolean getFormSubmitted() {
        return isFormSubmitted;
    }

    @Override
    public String toString() {
        return "FormInfo{" +
                "identifier='" + identifier + '\'' +
                ", formResponseType='" + formResponseType + '\'' +
                ", formType='" + formType + '\'' +
                ", isFormSubmitted=" + isFormSubmitted +
                '}';
    }

}
