/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

import com.urbanairship.android.layout.model.ButtonModel;
import com.urbanairship.android.layout.property.ButtonClickBehaviorType;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.android.layout.reporting.PagerData;
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
    @NonNull
    private final LayoutData state;

    public ButtonEvent(@NonNull EventType type, @NonNull String identifier, @NonNull String reportingDescription, @Nullable LayoutData state) {
        super(type);
        this.identifier = identifier;
        this.reportingDescription = reportingDescription;
        this.state = state != null ? state : new LayoutData(null, null,null);
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

    @NonNull
    public LayoutData getState() {
        return state;
    }

    public abstract ButtonEvent overrideState(@NonNull String formId, boolean isFormSubmitted);
    public abstract ButtonEvent overrideState(@NonNull PagerData pagerData);

    protected LayoutData copyState(@NonNull String formId, boolean isFormSubmitted) {
        return state.withFormData(formId, isFormSubmitted);
    }

    protected LayoutData copyState(@NonNull PagerData data) {
        return state.withPagerData(data);
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
            this(button.getIdentifier(), button.reportingDescription(), null);
        }

        private Dismiss(@NonNull String identifier, @NonNull String reportingDescription, @Nullable LayoutData state) {
            super(EventType.BUTTON_BEHAVIOR_DISMISS, identifier, reportingDescription, state);
        }

        @Override
        public Dismiss overrideState(@NonNull String formId, boolean isFormSubmitted) {
            return new Dismiss(getIdentifier(), getReportingDescription(), copyState(formId, isFormSubmitted));
        }

        @Override
        public Dismiss overrideState(@NonNull PagerData pagerData) {
            return new Dismiss(getIdentifier(), getReportingDescription(), copyState(pagerData));
        }

        @Override
        @NonNull
        public String toString() {
            return "ButtonEvent.Dismiss{" +
                "identifier='" + getIdentifier() + '\'' +
                ", reportingDescription='" + getReportingDescription() + '\'' +
                ", state=" + getState() +
                '}';
        }
    }

    public static class Cancel extends ButtonEvent {
        public Cancel(@NonNull ButtonModel button) {
            this(button.getIdentifier(), button.reportingDescription(), null);
        }

        private Cancel(@NonNull String identifier, @NonNull String reportingDescription, @Nullable LayoutData state) {
            super(EventType.BUTTON_BEHAVIOR_CANCEL, identifier, reportingDescription, state);
        }

        @Override
        public boolean isCancel() {
            return true;
        }

        @Override
        public Cancel overrideState(@NonNull String formId, boolean isFormSubmitted) {
            return new Cancel(getIdentifier(), getReportingDescription(), copyState(formId, isFormSubmitted));
        }

        @Override
        public Cancel overrideState(@NonNull PagerData pagerData) {
            return new Cancel(getIdentifier(), getReportingDescription(), copyState(pagerData));
        }

        @Override
        @NonNull
        public String toString() {
            return "ButtonEvent.Cancel{" +
                "identifier='" + getIdentifier() + '\'' +
                ", reportingDescription='" + getReportingDescription() + '\'' +
                ", state=" + getState() +
                '}';
        }
    }

    public static class PagerNext extends ButtonEvent {
        public PagerNext(@NonNull ButtonModel button) {
            this(button.getIdentifier(), button.reportingDescription(), null);
        }

        private PagerNext(@NonNull String identifier, @NonNull String reportingDescription, @Nullable LayoutData state) {
            super(EventType.BUTTON_BEHAVIOR_PAGER_NEXT, identifier, reportingDescription, state);
        }

        @Override
        public PagerNext overrideState(@NonNull String formId, boolean isFormSubmitted) {
            return new PagerNext(getIdentifier(), getReportingDescription(), copyState(formId, isFormSubmitted));
        }

        @Override
        public PagerNext overrideState(@NonNull PagerData pagerData) {
            return new PagerNext(getIdentifier(), getReportingDescription(), copyState(pagerData));
        }

        @Override
        @NonNull
        public String toString() {
            return "ButtonEvent.PagerNext{" +
                "identifier='" + getIdentifier() + '\'' +
                ", reportingDescription='" + getReportingDescription() + '\'' +
                ", state=" + getState() +
                '}';
        }
    }

    public static class PagerPrevious extends ButtonEvent {
        public PagerPrevious(@NonNull ButtonModel button) {
            this(button.getIdentifier(), button.reportingDescription(), null);
        }

        private PagerPrevious(@NonNull String identifier, @NonNull String reportingDescription, @Nullable LayoutData state) {
            super(EventType.BUTTON_BEHAVIOR_PAGER_PREVIOUS, identifier, reportingDescription, state);
        }

        @Override
        public PagerPrevious overrideState(@NonNull String formId, boolean isFormSubmitted) {
            return new PagerPrevious(getIdentifier(), getReportingDescription(), copyState(formId, isFormSubmitted));
        }

        @Override
        public PagerPrevious overrideState(@NonNull PagerData pagerData) {
            return new PagerPrevious(getIdentifier(), getReportingDescription(), copyState(pagerData));
        }

        @Override
        @NonNull
        public String toString() {
            return "ButtonEvent.PagerPrevious{" +
                "identifier='" + getIdentifier() + '\'' +
                ", reportingDescription='" + getReportingDescription() + '\'' +
                ", state=" + getState() +
                '}';
        }
    }

    public static class FormSubmit extends ButtonEvent {
        public FormSubmit(@NonNull ButtonModel button) {
            this(button.getIdentifier(), button.reportingDescription(), null);
        }

        private FormSubmit(@NonNull String identifier, @NonNull String reportingDescription, @Nullable LayoutData state) {
            super(EventType.BUTTON_BEHAVIOR_FORM_SUBMIT, identifier, reportingDescription, state);
        }

        @Override
        public FormSubmit overrideState(@NonNull String formId, boolean isFormSubmitted) {
            return new FormSubmit(getIdentifier(), getReportingDescription(), copyState(formId, isFormSubmitted));
        }

        @Override
        public FormSubmit overrideState(@NonNull PagerData pagerData) {
            return new FormSubmit(getIdentifier(), getReportingDescription(), copyState(pagerData));
        }

        @Override
        @NonNull
        public String toString() {
            return "ButtonEvent.FormSubmit{" +
                "identifier='" + getIdentifier() + '\'' +
                ", reportingDescription='" + getReportingDescription() + '\'' +
                ", state=" + getState() +
                '}';
        }
    }

    public static class Actions extends ButtonEvent {
        @NonNull
        private final Map<String, JsonValue> actions;

        public Actions(@NonNull ButtonModel button) {
            this(button.getIdentifier(), button.reportingDescription(), button.getActions(), null);
        }

        private Actions(@NonNull String identifier, @NonNull String reportingDescription, @NonNull Map<String, JsonValue> actions, @Nullable LayoutData state) {
            super(EventType.BUTTON_ACTIONS, identifier, reportingDescription, state);
            this.actions = actions;
        }

        @Override
        public Actions overrideState(@NonNull String formId, boolean isFormSubmitted) {
            return new Actions(getIdentifier(), getReportingDescription(), getActions(), copyState(formId, isFormSubmitted));
        }

        @Override
        public Actions overrideState(@NonNull PagerData pagerData) {
            return new Actions(getIdentifier(), getReportingDescription(), getActions(), copyState(pagerData));
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
                ", state=" + getState() +
                '}';
        }
    }
}
