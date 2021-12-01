/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

import com.urbanairship.android.layout.property.ViewType;

import androidx.annotation.NonNull;

public abstract class CheckboxEvent {

    public static final class ControllerInit extends FormEvent.InputInit {
        public ControllerInit(@NonNull String identifier, boolean isValid) {
            super(EventType.FORM_INPUT_INIT, ViewType.CHECKBOX_CONTROLLER, identifier, isValid);
        }
    }

    /** Event bubbled up from Checkbox views to Checkbox controllers when checked or unchecked. */
    public static final class InputChange extends FormEvent.CheckedChange {
        public InputChange(@NonNull String value,  boolean isChecked) {
            super(EventType.CHECKBOX_INPUT_CHANGE, value, isChecked);
        }
    }

    /** Event trickled down to Checkbox views from Checkbox controllers to update their checked state. */
    public static final class ViewUpdate extends FormEvent.CheckedChange {
        public ViewUpdate(@NonNull String value, boolean isChecked) {
            super(EventType.CHECKBOX_VIEW_UPDATE, value, isChecked);
        }
    }
}
