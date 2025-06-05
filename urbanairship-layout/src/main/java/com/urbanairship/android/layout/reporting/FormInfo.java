package com.urbanairship.android.layout.reporting;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class FormInfo implements JsonSerializable {
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

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                .put(KEY_IDENTIFIER, identifier)
                .put(KEY_FORM_RESPONSE_TYPE, formResponseType)
                .put(KEY_FORM_TYPE, formType)
                .putOpt(KEY_IS_FORM_SUBMITTED, isFormSubmitted)
                .build().toJsonValue();
    }

    private static final String KEY_IDENTIFIER = "identifier";
    private static final String KEY_FORM_RESPONSE_TYPE = "formResponseType";
    private static final String KEY_FORM_TYPE = "formType";
    private static final String KEY_IS_FORM_SUBMITTED = "isFormSubmitted";
}
