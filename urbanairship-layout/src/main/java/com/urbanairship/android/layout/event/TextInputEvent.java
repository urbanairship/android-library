/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

import com.urbanairship.android.layout.property.ViewType;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class TextInputEvent {
    /** Event bubbled up from text input views to Form controllers when initialized. */
    public static final class Init extends FormEvent.InputInit {
        public Init(@NonNull String identifier, boolean isValid) {
            super(EventType.FORM_INPUT_INIT, ViewType.TEXT_INPUT, identifier, isValid);
        }

        @Override
        @NonNull
        public String toString() {
            return "TextInputEvent.Init{}";
        }
    }
}
