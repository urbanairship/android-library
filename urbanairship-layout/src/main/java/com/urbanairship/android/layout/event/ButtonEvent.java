/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

import com.urbanairship.android.layout.model.ButtonModel;
import com.urbanairship.android.layout.property.ButtonClickBehaviorType;
import com.urbanairship.android.layout.reporting.FormInfo;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.android.layout.reporting.PagerData;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

    public static ButtonEvent fromBehavior(@NonNull ButtonClickBehaviorType behavior, @NonNull ButtonModel model) throws JsonException {
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
        throw new JsonException("Unknown button click behavior type: " + behavior.name());
    }

    public static class Dismiss extends ButtonEvent {
        public Dismiss(@NonNull ButtonModel button) {
            this(button.getIdentifier(), button.reportingDescription());
        }

        private Dismiss(@NonNull String identifier, @NonNull String reportingDescription) {
            super(EventType.BUTTON_BEHAVIOR_DISMISS, identifier, reportingDescription);
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
            this(button.getIdentifier(), button.reportingDescription());
        }

        private Cancel(@NonNull String identifier, @NonNull String reportingDescription) {
            super(EventType.BUTTON_BEHAVIOR_CANCEL, identifier, reportingDescription);
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
            this(button.getIdentifier(), button.reportingDescription());
        }

        private PagerNext(@NonNull String identifier, @NonNull String reportingDescription) {
            super(EventType.BUTTON_BEHAVIOR_PAGER_NEXT, identifier, reportingDescription);
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
            this(button.getIdentifier(), button.reportingDescription());
        }

        private PagerPrevious(@NonNull String identifier, @NonNull String reportingDescription) {
            super(EventType.BUTTON_BEHAVIOR_PAGER_PREVIOUS, identifier, reportingDescription);
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
            this(button.getIdentifier(), button.reportingDescription());
        }

        private FormSubmit(@NonNull String identifier, @NonNull String reportingDescription) {
            super(EventType.BUTTON_BEHAVIOR_FORM_SUBMIT, identifier, reportingDescription);
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

    public static class Actions extends ButtonEvent implements EventWithActions {
        @NonNull
        private final Map<String, JsonValue> actions;

        public Actions(@NonNull ButtonModel button) {
            this(button.getIdentifier(), button.reportingDescription(), button.getActions());
        }

        private Actions(@NonNull String identifier, @NonNull String reportingDescription, @NonNull Map<String, JsonValue> actions) {
            super(EventType.BUTTON_ACTIONS, identifier, reportingDescription);
            this.actions = actions;
        }

        @Override
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
