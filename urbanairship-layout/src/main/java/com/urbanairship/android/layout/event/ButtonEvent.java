/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

import com.urbanairship.android.layout.model.ButtonModel;
import com.urbanairship.android.layout.property.ButtonClickBehaviorType;
import com.urbanairship.json.JsonMap;

import java.util.List;

import androidx.annotation.NonNull;

public abstract class ButtonEvent extends Event {
    @NonNull
    private final String identifier;

    public ButtonEvent(@NonNull EventType type, @NonNull String identifier) {
        super(type);
        this.identifier = identifier;
    }

    @NonNull
    public String getIdentifier() {
        return identifier;
    }

    public static ButtonEvent fromBehavior(@NonNull ButtonClickBehaviorType behavior, @NonNull ButtonModel model) {
        switch (behavior) {
            case CANCEL:
                return new ButtonEvent.Cancel(model);
            case DISMISS:
                return new ButtonEvent.Dismiss(model);
            case PAGER_NEXT:
                return new ButtonEvent.PagerNext(model);
            case PAGER_PREVIOUS:
                return new ButtonEvent.PagerPrevious(model);
            case FORM_SUBMIT:
                return new ButtonEvent.FormSubmit(model);
        }
        // Shouldn't get here, provided the above switch remains exhaustive.
        throw new IllegalArgumentException("Unknown button click behavior type: " + behavior.name());
    }

    public static class Dismiss extends ButtonEvent {
        public Dismiss(@NonNull ButtonModel button) {
            super(EventType.BUTTON_BEHAVIOR_DISMISS, button.getIdentifier());
        }
    }

    public static class Cancel extends ButtonEvent {
        public Cancel(@NonNull ButtonModel button) {
            super(EventType.BUTTON_BEHAVIOR_CANCEL, button.getIdentifier());
        }
    }

    public static class PagerNext extends ButtonEvent {
        public PagerNext(@NonNull ButtonModel button) {
            super(EventType.BUTTON_BEHAVIOR_PAGER_NEXT, button.getIdentifier());
        }
    }

    public static class PagerPrevious extends ButtonEvent {
        public PagerPrevious(@NonNull ButtonModel button) {
            super(EventType.BUTTON_BEHAVIOR_PAGER_PREVIOUS, button.getIdentifier());
        }
    }

    public static class FormSubmit extends ButtonEvent {
        public FormSubmit(@NonNull ButtonModel button) {
            super(EventType.BUTTON_BEHAVIOR_FORM_SUBMIT, button.getIdentifier());
        }
    }

    public static class Actions extends ButtonEvent {
        @NonNull
        private final List<JsonMap> actions;

        public Actions(@NonNull ButtonModel button) {
            super(EventType.BUTTON_ACTIONS, button.getIdentifier());
            this.actions = button.getActions();
        }

        @NonNull
        public List<JsonMap> getActions() {
            return actions;
        }
    }
}
