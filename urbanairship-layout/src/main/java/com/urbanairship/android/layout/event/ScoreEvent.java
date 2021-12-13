/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

import com.urbanairship.android.layout.property.ViewType;

import androidx.annotation.NonNull;

public class ScoreEvent {

    /** Event bubbled up from Score views to Form controllers when initialized. */
    public static final class Init extends FormEvent.InputInit {
        public Init(@NonNull String identifier, boolean isValid) {
            super(EventType.FORM_INPUT_INIT, ViewType.SCORE, identifier, isValid);
        }

        @Override
        @NonNull
        public String toString() {
            return "ScoreEvent.Init{}";
        }
    }
}
