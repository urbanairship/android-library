/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

import com.urbanairship.android.layout.property.ViewType;

import androidx.annotation.NonNull;

public abstract class RadioEvent {

    public static final class ControllerInit extends FormEvent.InputInit {
        public ControllerInit(@NonNull String identifier, boolean isValid) {
            super(EventType.FORM_INPUT_INIT, ViewType.RADIO_INPUT_CONTROLLER, identifier, isValid);
        }
    }

    /** Event emitted by Radio Input views when selected. */
    public static final class InputChange extends FormEvent.CheckedChange {
        public InputChange(@NonNull String value, boolean isChecked) {
            super(EventType.RADIO_INPUT_CHANGE, value, isChecked);
        }
    }

    /** Event emitted by Radio Input views when selected. */
    public static final class ViewUpdate extends FormEvent.CheckedChange {
        public ViewUpdate(@NonNull String value, boolean isChecked) {
            super(EventType.RADIO_VIEW_UPDATE, value, isChecked);
        }
    }
}
