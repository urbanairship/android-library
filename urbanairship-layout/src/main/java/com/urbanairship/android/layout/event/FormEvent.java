/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.FormData;

import androidx.annotation.NonNull;

public abstract class FormEvent extends Event {
    protected FormEvent(@NonNull EventType type) {
        super(type);
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
    }

    public abstract static class InputChange<T> extends FormEvent {
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
    }

    public abstract static class CheckedChange extends InputChange<String> {
        protected final boolean isChecked;

        public CheckedChange(@NonNull EventType type, @NonNull String value, boolean isChecked) {
            super(type, value);
            this.isChecked = isChecked;
        }

        public boolean isChecked() {
            return isChecked;
        }
    }


    /** Event bubbled up from form inputs to their parent form controllers when data has changed. */
    public static final class DataChange extends InputChange<FormData<?>> {
        @NonNull
        private final String identifier;
        private final boolean isValid;

        public DataChange(
            @NonNull String identifier,
            @NonNull FormData<?> value,
            boolean isValid
        ) {
            super(EventType.FORM_DATA_CHANGE, value);
            this.identifier = identifier;
            this.isValid = isValid;
        }

        public boolean isValid() {
            return isValid;
        }

        @NonNull
        public String getIdentifier() {
            return identifier;
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
    }
}
