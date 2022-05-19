/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.event.ButtonEvent;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.FormEvent;
import com.urbanairship.android.layout.event.ReportingEvent;
import com.urbanairship.android.layout.event.WebViewEvent;
import com.urbanairship.android.layout.property.FormBehaviorType;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.AttributeName;
import com.urbanairship.android.layout.reporting.FormData;
import com.urbanairship.android.layout.reporting.FormInfo;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Base model for top-level form controllers.
 *
 * @see FormController
 * @see NpsFormController
 */
public abstract class BaseFormController extends LayoutModel implements Identifiable {
    @NonNull
    private final String identifier;
    @Nullable
    private final String responseType;
    @NonNull
    private final BaseModel view;
    @Nullable
    private final FormBehaviorType submitBehavior;

    @NonNull
    private final Map<String, FormData<?>> formData = new HashMap<>();

    @NonNull
    private final Map<AttributeName, JsonValue> attributes = new HashMap<>();

    @NonNull
    private final Map<String, Boolean> inputValidity = new HashMap<>();

    private boolean isDisplayReported = false;
    private boolean isSubmitted = false;

    public BaseFormController(
        @NonNull ViewType viewType,
        @NonNull String identifier,
        @Nullable String responseType,
        @NonNull BaseModel view,
        @Nullable FormBehaviorType submitBehavior
    ) {
        super(viewType, null, null);

        this.identifier = identifier;
        this.responseType = responseType;
        this.view = view;
        this.submitBehavior = submitBehavior;

        view.addListener(this);
    }

    @NonNull
    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Nullable
    public String getResponseType() {
        return responseType;
    }

    @NonNull
    public BaseModel getView() {
        return view;
    }

    public boolean hasSubmitBehavior() {
        return submitBehavior != null;
    }

    @Override
    public List<BaseModel> getChildren() {
        return Collections.singletonList(view);
    }

    protected static String identifierFromJson(@NonNull JsonMap json) throws JsonException {
        return Identifiable.identifierFromJson(json);
    }

    protected static BaseModel viewFromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap viewJson = json.opt("view").optMap();
        return Thomas.model(viewJson);
    }

    @Nullable
    protected static FormBehaviorType submitBehaviorFromJson(@NonNull JsonMap json) throws JsonException {
        String submitString = json.opt("submit").getString();
        return submitString != null ? FormBehaviorType.from(submitString) : null;
    }

    @Override
    public boolean onEvent(@NonNull Event event, @NonNull LayoutData layoutData) {
        Logger.verbose("onEvent: %s, layoutData: %s", event, layoutData);

        LayoutData dataOverride = layoutData.withFormInfo(getFormInfo());

        switch (event.getType()) {
            case FORM_INIT:
                onNestedFormInit((FormEvent.Init) event);
                return hasSubmitBehavior() || super.onEvent(event, dataOverride);

            case FORM_INPUT_INIT:
                onInputInit((FormEvent.InputInit) event);
                return true;

            case FORM_DATA_CHANGE:
                onDataChange((FormEvent.DataChange) event);
                if (!hasSubmitBehavior()) {
                    // Update parent controller if this is a child form
                    bubbleEvent(getFormDataChangeEvent(), layoutData);
                }
                return true;

            case VIEW_ATTACHED:
                onViewAttached((Event.ViewAttachedToWindow) event);
                if (hasSubmitBehavior()) {
                    return true;
                }
                return super.onEvent(event, dataOverride);

            case BUTTON_BEHAVIOR_FORM_SUBMIT:
                // Submit form if this controller has a submit behavior.
                if (hasSubmitBehavior()) {
                    onSubmit();
                    return true;
                }
                // Otherwise update with our form data and let parent form controller handle it.
                return super.onEvent(event, dataOverride);
            default:
                return super.onEvent(event, dataOverride);
        }
    }

    private void onSubmit() {
        isSubmitted = true;
        bubbleEvent(getFormResultEvent(), LayoutData.form(getFormInfo()));
    }

    protected abstract FormEvent.Init getInitEvent();
    protected abstract FormEvent.DataChange getFormDataChangeEvent();
    protected abstract ReportingEvent.FormResult getFormResultEvent();

    private void onNestedFormInit(FormEvent.Init init) {
        updateFormValidity(init.getIdentifier(), init.isValid());
    }

    private void onInputInit(FormEvent.InputInit init) {
        updateFormValidity(init.getIdentifier(), init.isValid());

        if (inputValidity.size() == 1) {
            if (!hasSubmitBehavior()) {
                // This is a nested form, since it has no submit behavior.
                // Bubble an init event to announce this form to a parent form controller.
                bubbleEvent(getInitEvent(), LayoutData.form(getFormInfo()));
            }
        }
    }

    private void onViewAttached(Event.ViewAttachedToWindow attach) {
        if (attach.getViewType().isFormInput() && !isDisplayReported) {
            isDisplayReported = true;
            FormInfo formInfo = getFormInfo();
            bubbleEvent(new ReportingEvent.FormDisplay(formInfo), LayoutData.form(formInfo));
        }
    }

    private void onDataChange(@NonNull FormEvent.DataChange data) {
        String identifier = data.getValue().getIdentifier();
        boolean isValid = data.isValid();

        if (isValid) {
            formData.put(identifier, data.getValue());
            attributes.putAll(data.getAttributes());
        } else {
            formData.remove(identifier);
            for (AttributeName key : data.getAttributes().keySet()) {
                attributes.remove(key);
            }
        }

        updateFormValidity(identifier, isValid);

    }

    private void updateFormValidity(@NonNull String inputId, boolean isValid) {
        inputValidity.put(inputId, isValid);
        trickleEvent(new FormEvent.ValidationUpdate(isFormValid()), LayoutData.form(getFormInfo()));
    }

    protected boolean isFormValid() {
        for (Map.Entry<String, Boolean> validity : inputValidity.entrySet()) {
            if (!validity.getValue()) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    protected Collection<FormData<?>> getFormData() {
        return formData.values();
    }

    @NonNull
    protected Map<AttributeName, JsonValue> getAttributes() {
        return attributes;
    }

    @NonNull
    protected FormInfo getFormInfo() {
        return new FormInfo(identifier, getFormType(), responseType, isSubmitted);
    }

    @NonNull
    protected abstract String getFormType();

}
