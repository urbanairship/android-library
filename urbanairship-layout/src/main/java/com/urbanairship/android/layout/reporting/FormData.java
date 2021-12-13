/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.reporting;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class FormData<T> implements JsonSerializable {

    private static final String KEY_TYPE = "type";
    private static final String KEY_VALUE = "value";
    private static final String KEY_SCORE_ID = "score_id";
    private static final String KEY_CHILDREN = "children";

    private enum Type implements JsonSerializable {
        FORM("form"),
        NPS_FORM("nps"),
        TOGGLE("toggle"),
        MULTIPLE_CHOICE("multiple_choice"),
        SINGLE_CHOICE("single_choice"),
        TEXT("text"),
        SCORE("score");

        @NonNull
        private final String value;

        Type(@NonNull String value) {
            this.value = value;
        }

        @NonNull
        @Override
        public JsonValue toJsonValue() {
            return JsonValue.wrap(value);
        }
    }

    @NonNull
    private final Type type;
    @NonNull
    private final T value;

    public FormData(@NonNull Type type, @NonNull T value) {
        this.type = type;
        this.value = value;
    }

    @NonNull
    public Type getType() {
        return type;
    }

    @NonNull
    public T getValue() {
        return value;
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return toJsonMap().toJsonValue();
    }

    @NonNull
    protected JsonValue getJsonValue() {
        return JsonValue.wrapOpt(value);
    }

    @NonNull
    protected JsonMap toJsonMap() {
        return JsonMap.newBuilder()
               .put(KEY_TYPE, getType())
               .put(KEY_VALUE, getJsonValue())
               .build();
    }

    public static class Toggle extends FormData<Boolean> {
        public Toggle(boolean value) {
            super(Type.TOGGLE, value);
        }
    }

    public static class CheckboxController extends FormData<Set<JsonValue>> {
        public CheckboxController(@NonNull Set<JsonValue> value) {
            super(Type.MULTIPLE_CHOICE, value);
        }
    }

    public static class RadioInputController extends FormData<JsonValue> {
        public RadioInputController(@NonNull JsonValue value) {
            super(Type.SINGLE_CHOICE, value);
        }
    }

    public static class TextInput extends FormData<String> {
        public TextInput(@NonNull String value) {
            super(Type.TEXT, value);
        }
    }

    public static class Score extends FormData<Integer> {
        public Score(@NonNull Integer value) {
            super(Type.SCORE, value);
        }
    }

    public abstract static class BaseForm extends FormData<Map<String, FormData<?>>> {
        private final String identifier;

        public BaseForm(@NonNull Type type, @NonNull String identifier, @NonNull Map<String, FormData<?>> children) {
            super(type, children);
            this.identifier = identifier;
        }

        public String getIdentifier() {
            return identifier;
        }
    }

    public static class Form extends BaseForm {
        public Form(@NonNull String identifier, @NonNull Map<String, FormData<?>> children) {
            super(Type.FORM, identifier, children);
        }

        @NonNull
        @Override
        public JsonMap toJsonMap() {
            return JsonMap.newBuilder()
                .put(KEY_TYPE, getType())
                .put(KEY_CHILDREN, getJsonValue())
                .build();
        }
    }

    public static class Nps extends BaseForm {
        private final String scoreId;

        public Nps(@NonNull String identifier, @NonNull String scoreId, @NonNull Map<String, FormData<?>> children) {
            super(Type.NPS_FORM, identifier, children);
            this.scoreId = scoreId;
        }

        @NonNull
        @Override
        public JsonMap toJsonMap() {
            return JsonMap.newBuilder()
                .put(KEY_TYPE, getType())
                .put(KEY_SCORE_ID, getScoreId())
                .put(KEY_CHILDREN, getJsonValue())
                .build();
        }

        public String getScoreId() {
            return scoreId;
        }
    }
}
