/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import java.util.Locale;

import androidx.annotation.NonNull;

import static android.text.InputType.TYPE_CLASS_NUMBER;
import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
import static android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
import static android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;
import static android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;

import com.urbanairship.json.JsonException;

public enum FormInputType {
    EMAIL("email", TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
    NUMBER("number", TYPE_CLASS_NUMBER),
    SMS("sms", TYPE_CLASS_NUMBER),
    TEXT("text", TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_AUTO_CORRECT | TYPE_TEXT_FLAG_CAP_SENTENCES),
    TEXT_MULTILINE("text_multiline", TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_MULTI_LINE | TYPE_TEXT_FLAG_AUTO_CORRECT | TYPE_TEXT_FLAG_CAP_SENTENCES);

    @NonNull
    private final String value;
    private final int typeMask;

    FormInputType(@NonNull String value, int typeMask) {
        this.value = value;
        this.typeMask = typeMask;
    }

    @NonNull
    public static FormInputType from(@NonNull String value) throws JsonException {
        for (FormInputType type : FormInputType.values()) {
            if (type.value.equals(value.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        throw new JsonException("Unknown Form Input Type value: " + value);
    }

    public int getTypeMask() {
        return typeMask;
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
