package com.urbanairship.permission;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.Locale;

import androidx.annotation.NonNull;

public enum PermissionStatus implements JsonSerializable {
    GRANTED("granted"),
    DENIED("denied"),
    NOT_DETERMINED("not_determined");

    @NonNull
    private final String value;

    PermissionStatus(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static PermissionStatus fromJson(@NonNull JsonValue value) throws JsonException {
        String valueString = value.optString();
        for (PermissionStatus type : PermissionStatus.values()) {
            if (type.value.equalsIgnoreCase(valueString)) {
                return type;
            }
        }
        throw new JsonException("Invalid permission status: " + value);
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonValue.wrapOpt(value);
    }
}
