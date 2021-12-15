/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

import com.urbanairship.android.layout.model.ButtonModel;
import com.urbanairship.android.layout.property.ButtonClickBehaviorType;
import com.urbanairship.json.JsonValue;

import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class ButtonEvent extends Event {
    @NonNull
    private final String identifier;
    @NonNull
    private final String reportingDescription;

    public ButtonEvent(@NonNull EventType type, @NonNull String identifier, @NonNull String reportingDescription) {
        super(type);
        this.identifier = identifier;
        this.reportingDescription = reportingDescription;
    }

    @NonNull
    public String getIdentifier() {
        return identifier;
    }

    @NonNull
    public String getReportingDescription() {
        return reportingDescription;
    }

    public boolean isCancel() {
        return false;
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
            super(EventType.BUTTON_BEHAVIOR_DISMISS, button.getIdentifier(), button.reportingDescription());
        }

        @Override
        @NonNull
        public String toString() {
            return "ButtonEvent.Dismiss{" +
                "identifier='" + getIdentifier() + '\'' +
                ", reportingDescription='" + getReportingDescription() + '\'' +
                '}';
        }
    }

    public static class Cancel extends ButtonEvent {
        public Cancel(@NonNull ButtonModel button) {
            super(EventType.BUTTON_BEHAVIOR_CANCEL, button.getIdentifier(), button.reportingDescription());
        }

        @Override
        public boolean isCancel() {
            return true;
        }

        @Override
        @NonNull
        public String toString() {
            return "ButtonEvent.Cancel{" +
                "identifier='" + getIdentifier() + '\'' +
                ", reportingDescription='" + getReportingDescription() + '\'' +
                '}';
        }
    }

    public static class PagerNext extends ButtonEvent {
        public PagerNext(@NonNull ButtonModel button) {
            super(EventType.BUTTON_BEHAVIOR_PAGER_NEXT, button.getIdentifier(), button.reportingDescription());
        }

        @Override
        @NonNull
        public String toString() {
            return "ButtonEvent.PagerNext{" +
                "identifier='" + getIdentifier() + '\'' +
                ", reportingDescription='" + getReportingDescription() + '\'' +
                '}';
        }
    }

    public static class PagerPrevious extends ButtonEvent {
        public PagerPrevious(@NonNull ButtonModel button) {
            super(EventType.BUTTON_BEHAVIOR_PAGER_PREVIOUS, button.getIdentifier(), button.reportingDescription());
        }

        @Override
        @NonNull
        public String toString() {
            return "ButtonEvent.PagerPrevious{" +
                "identifier='" + getIdentifier() + '\'' +
                ", reportingDescription='" + getReportingDescription() + '\'' +
                '}';
        }
    }

    public static class FormSubmit extends ButtonEvent {
        public FormSubmit(@NonNull ButtonModel button) {
            super(EventType.BUTTON_BEHAVIOR_FORM_SUBMIT, button.getIdentifier(), button.reportingDescription());
        }

        @Override
        @NonNull
        public String toString() {
            return "ButtonEvent.FormSubmit{" +
                "identifier='" + getIdentifier() + '\'' +
                ", reportingDescription='" + getReportingDescription() + '\'' +
                '}';
        }
    }

    public static class Actions extends ButtonEvent {
        @NonNull
        private final Map<String, JsonValue> actions;

        public Actions(@NonNull ButtonModel button) {
            super(EventType.BUTTON_ACTIONS, button.getIdentifier(), button.reportingDescription());
            this.actions = button.getActions();
        }

        @NonNull
        public Map<String, JsonValue> getActions() {
            return actions;
        }

        @Override
        @NonNull
        public String toString() {
            return "ButtonEvent.Actions{" +
                "identifier='" + getIdentifier() + '\'' +
                ", reportingDescription='" + getReportingDescription() + '\'' +
                ", actions=" + getActions() +
                '}';
        }
    }
}
