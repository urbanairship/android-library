/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonValue;

import androidx.annotation.NonNull;

public abstract class CheckboxEvent {

    public static final class ControllerInit extends FormEvent.InputInit {
        public ControllerInit(@NonNull String identifier, boolean isValid) {
            super(EventType.FORM_INPUT_INIT, ViewType.CHECKBOX_CONTROLLER, identifier, isValid);
        }

        @Override
        @NonNull
        public String toString() {
            return "CheckBoxEvent.ControllerInit{}";
        }
    }

    /** Event bubbled up from Checkbox views to Checkbox controllers when checked or unchecked. */
    public static final class InputChange extends FormEvent.CheckedChange {
        public InputChange(@NonNull JsonValue value, boolean isChecked) {
            super(EventType.CHECKBOX_INPUT_CHANGE, value, isChecked);
        }

        @Override
        @NonNull
        public String toString() {
            return "CheckBoxEvent.InputChange{" +
                "value=" + value +
                ", isChecked=" + isChecked +
                '}';
        }
    }

    /**
     * Event trickled down to Checkbox views from Checkbox controllers to update
     * their checked state when a checkbox is checked or unchecked.
     */
    public static final class ViewUpdate extends FormEvent.CheckedChange {
        public ViewUpdate(@NonNull JsonValue value, boolean isChecked) {
            super(EventType.CHECKBOX_VIEW_UPDATE, value, isChecked);
        }

        @Override
        @NonNull
        public String toString() {
            return "CheckBoxEvent.ViewUpdate{" +
                "value=" + value +
                ", isChecked=" + isChecked +
                '}';
        }
    }
}
