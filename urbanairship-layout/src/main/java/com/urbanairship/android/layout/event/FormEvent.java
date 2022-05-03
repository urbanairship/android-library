/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.AttributeName;
import com.urbanairship.android.layout.reporting.FormData;
import com.urbanairship.json.JsonValue;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class FormEvent extends Event {
    protected FormEvent(@NonNull EventType type) {
        super(type);
    }

    public static class Init extends FormEvent {
        @NonNull
        private final String identifier;
        private final boolean isValid;

        public Init(@NonNull String identifier, boolean isValid) {
            super(EventType.FORM_INIT);
            this.identifier = identifier;
            this.isValid = isValid;
        }

        @NonNull
        public String getIdentifier() {
            return identifier;
        }

        public boolean isValid() {
            return isValid;
        }

        @Override
        @NonNull
        public String toString() {
            return "FormEvent.Init{" +
                "identifier='" + identifier + '\'' +
                ", isValid=" + isValid +
                '}';
        }
    }

    public abstract static class InputInit extends FormEvent {
        @NonNull
        private final ViewType viewType;
        @NonNull
        private final String identifier;
        private final boolean isValid;

        public InputInit(
            @NonNull EventType type,
            @NonNull ViewType viewType,
            @NonNull String identifier,
            boolean isValid
        ) {
            super(type);
            this.viewType = viewType;
            this.identifier = identifier;
            this.isValid = isValid;
        }

        @NonNull
        public ViewType getViewType() {
            return viewType;
        }

        @NonNull
        public String getIdentifier() {
            return identifier;
        }

        public boolean isValid() {
            return isValid;
        }

        @Override
        @NonNull
        public String toString() {
            return "FormEvent.InputInit{" +
                "viewType=" + viewType +
                ", identifier='" + identifier + '\'' +
                ", isValid=" + isValid +
                '}';
        }
    }

    abstract static class InputChange<T> extends FormEvent {
        @NonNull
        protected final T value;

        public InputChange(@NonNull EventType type, @NonNull T value) {
            super(type);
            this.value = value;
        }

        @NonNull
        public T getValue() {
            return value;
        }

        @Override
        @NonNull
        public String toString() {
            return "FormEvent.InputChange{" +
                "value=" + value +
                '}';
        }
    }

    public abstract static class CheckedChange extends InputChange<JsonValue> {
        protected final boolean isChecked;

        public CheckedChange(@NonNull EventType type, @NonNull JsonValue value, boolean isChecked) {
            super(type, value);
            this.isChecked = isChecked;
        }

        public boolean isChecked() {
            return isChecked;
        }

        @Override
        @NonNull
        public String toString() {
            return "FormEvent.CheckedChange{" +
                "value=" + value +
                ", isChecked=" + isChecked +
                '}';
        }
    }


    /** Event bubbled up from form inputs to their parent form controllers when data has changed. */
    public static final class DataChange extends InputChange<FormData<?>> {
        private final boolean isValid;

        @NonNull
        private final Map<AttributeName, JsonValue> attributes = new HashMap<>();

        public DataChange(
            @NonNull FormData<?> value,
            boolean isValid
        ) {
            this(value, isValid, null, null);
        }

        public DataChange(
            @NonNull FormData<?> value,
            boolean isValid,
            @Nullable AttributeName attributeName
            ) {
            this(value, isValid, attributeName, null);
        }

        public DataChange(
            @NonNull FormData<?> value,
            boolean isValid,
            @Nullable AttributeName attributeName,
            @Nullable JsonValue attributeValue
        ) {
            this(
                value,
                isValid,
                attributeName != null && attributeValue != null
                    ? new HashMap<AttributeName, JsonValue>() {{ put(attributeName, attributeValue); }}
                    : null
            );
        }

        public DataChange(
            @NonNull FormData<?> value,
            boolean isValid,
            @Nullable Map<AttributeName, JsonValue> attributes
        ) {
            super(EventType.FORM_DATA_CHANGE, value);
            this.isValid = isValid;
            if (attributes != null) {
                this.attributes.putAll(attributes);
            }
        }

        public boolean isValid() {
            return isValid;
        }

        @NonNull
        public Map<AttributeName, JsonValue> getAttributes() {
            return attributes;
        }

        @Override
        public String toString() {
            return "DataChange{" +
                    "value=" + value +
                    "isValid=" + isValid +
                    ", attributes=" + attributes +
                    '}';
        }

    }


    /** Event emitted by FormControllers to control the enabled state of submit buttons. */
    public static final class ValidationUpdate extends Event {
        private final boolean isValid;

        public ValidationUpdate(boolean isValid) {
            super(EventType.FORM_VALIDATION);
            this.isValid = isValid;
        }

        public boolean isValid() {
            return isValid;
        }

        @Override
        @NonNull
        public String toString() {
            return "FormEvent.ValidationUpdate{" +
                "isValid=" + isValid +
                '}';
        }
    }
}
