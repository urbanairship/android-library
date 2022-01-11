/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.reporting;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.Collection;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class FormData<T> {

    private static final String KEY_TYPE = "type";
    private static final String KEY_VALUE = "value";
    private static final String KEY_SCORE_ID = "score_id";
    private static final String KEY_CHILDREN = "children";
    private static final String KEY_RESPONSE_TYPE = "response_type";

    private enum Type implements JsonSerializable {
        FORM("form"),
        NPS_FORM("nps"),
        TOGGLE("toggle"),
        MULTIPLE_CHOICE("multiple_choice"),
        SINGLE_CHOICE("single_choice"),
        TEXT("text_input"),
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

    @NonNull
    private final String identifier;

    public FormData(@NonNull String identifier, @NonNull Type type, @NonNull T value) {
        this.identifier = identifier;
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
    public String getIdentifier() {
        return identifier;
    }

    @NonNull
    protected JsonMap getFormData() {
        return JsonMap.newBuilder()
                      .put(KEY_TYPE, getType())
                      .put(KEY_VALUE, JsonValue.wrapOpt(value))
                      .build();
    }

    public static class Toggle extends FormData<Boolean> {
        public Toggle(@NonNull String identifier, boolean value) {
            super(identifier, Type.TOGGLE, value);
        }
    }


    public static class CheckboxController extends FormData<Set<JsonValue>> {

        public CheckboxController(@NonNull String identifier, @NonNull Set<JsonValue> value) {
            super(identifier, Type.MULTIPLE_CHOICE, value);
        }

    }

    public static class RadioInputController extends FormData<JsonValue> {

        public RadioInputController(@NonNull String identifier, @NonNull JsonValue value) {
            super(identifier, Type.SINGLE_CHOICE, value);
        }

    }

    public static class TextInput extends FormData<String> {

        public TextInput(@NonNull String identifier, @NonNull String value) {
            super(identifier, Type.TEXT, value);
        }

    }

    public static class Score extends FormData<Integer> {

        public Score(@NonNull String identifier, @NonNull Integer value) {
            super(identifier, Type.SCORE, value);
        }

    }

    public abstract static class BaseForm extends FormData<Collection<FormData<?>>> implements JsonSerializable {

        protected final String responseType;

        public BaseForm(@NonNull String identifier, @Nullable String responseType, @NonNull Type type, @NonNull Collection<FormData<?>> children) {
            super(identifier, type, children);
            this.responseType = responseType;
        }

        @NonNull
        protected JsonSerializable getChildrenJson() {
            JsonMap.Builder builder = JsonMap.newBuilder();
            for (FormData<?> child : getValue()) {
                builder.putOpt(child.identifier, child.getFormData());
            }
            return builder.build();
        }

        @NonNull
        protected abstract JsonMap getFormData();

        @NonNull
        @Override
        public JsonValue toJsonValue() {
            return JsonMap.newBuilder()
                          .put(getIdentifier(), getFormData())
                          .build()
                          .toJsonValue();
        }

    }

    public static class Form extends BaseForm {

        public Form(@NonNull String identifier, @Nullable String responseType, @NonNull Collection<FormData<?>> children) {
            super(identifier, responseType, Type.FORM, children);
        }

        @NonNull
        @Override
        protected JsonMap getFormData() {
            return JsonMap.newBuilder()
                          .put(KEY_TYPE, getType())
                          .put(KEY_CHILDREN, getChildrenJson())
                          .put(KEY_RESPONSE_TYPE, responseType)
                          .build();
        }

    }

    public static class Nps extends BaseForm {

        private final String scoreId;

        public Nps(@NonNull String identifier, @Nullable String responseType, @NonNull String scoreId, @NonNull Collection<FormData<?>> children) {
            super(identifier, responseType, Type.NPS_FORM, children);
            this.scoreId = scoreId;
        }

        public String getScoreId() {
            return scoreId;
        }

        @NonNull
        @Override
        protected JsonMap getFormData() {
            return JsonMap.newBuilder()
                          .put(KEY_TYPE, getType())
                          .put(KEY_CHILDREN, getChildrenJson())
                          .put(KEY_SCORE_ID, scoreId)
                          .put(KEY_RESPONSE_TYPE, responseType)
                          .build();
        }
    }
}
