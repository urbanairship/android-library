/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class RadioEvent {

    public static final class ControllerInit extends FormEvent.InputInit {

        public ControllerInit(@NonNull String identifier, boolean isValid) {
            super(EventType.FORM_INPUT_INIT, ViewType.RADIO_INPUT_CONTROLLER, identifier, isValid);
        }

        @Override
        @NonNull
        public String toString() {
            return "RadioEvent.ControllerInit{}";
        }

    }

    /**
     * Event emitted by Radio Input views when selected.
     */
    public static final class InputChange extends FormEvent.CheckedChange {

        private JsonValue attributeValue;

        public InputChange(@NonNull JsonValue value, @Nullable JsonValue attributeValue, boolean isChecked) {
            super(EventType.RADIO_INPUT_CHANGE, value, isChecked);
            this.attributeValue = attributeValue;
        }

        @Override
        @NonNull
        public String toString() {
            return "RadioEvent.InputChange{" +
                    "value=" + value +
                    "attribute_value=" + attributeValue +
                    ", isChecked=" + isChecked +
                    '}';
        }

        @Nullable
        public JsonValue getAttributeValue() {
            return attributeValue;
        }

    }

    /**
     * Event emitted by Radio Input views when selected.
     */
    public static final class ViewUpdate extends FormEvent.CheckedChange {

        public ViewUpdate(@NonNull JsonValue value, boolean isChecked) {
            super(EventType.RADIO_VIEW_UPDATE, value, isChecked);
        }

        @Override
        @NonNull
        public String toString() {
            return "RadioEvent.ViewUpdate{" +
                    "value=" + value +
                    ", isChecked=" + isChecked +
                    '}';
        }

    }

}
