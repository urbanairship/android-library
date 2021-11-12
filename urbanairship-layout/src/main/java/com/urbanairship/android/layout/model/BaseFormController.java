/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.property.FormBehaviorType;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import java.util.Collections;
import java.util.List;

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
    @NonNull
    private final BaseModel view;
    @Nullable
    private final FormBehaviorType submitBehavior;

    @NonNull
    private final List<BaseModel> formInputs;

    public BaseFormController(
        @NonNull ViewType viewType,
        @NonNull String identifier,
        @NonNull BaseModel view,
        @Nullable FormBehaviorType submitBehavior
    ) {
        super(viewType, null, null);

        this.identifier = identifier;
        this.view = view;
        this.submitBehavior = submitBehavior;

        view.addListener(this);

        // TODO: rework hierarchy so we can actually find all the form inputs instead of all child views...
        formInputs = Thomas.findAllByType(BaseModel.class, view);
    }

    @NonNull
    @Override
    public String getIdentifier() {
        return identifier;
    }

    @NonNull
    public BaseModel getView() {
        return view;
    }

    @Nullable
    public FormBehaviorType getSubmitBehavior() {
        return submitBehavior;
    }

    @Override
    public List<BaseModel> getChildren() {
        return Collections.singletonList(view);
    }

    @NonNull
    protected List<BaseModel> getFormInputs() {
        return formInputs;
    }

    protected static String identifierFromJson(@NonNull JsonMap json) throws JsonException {
        return Identifiable.identifierFromJson(json);
    }

    protected static BaseModel viewFromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap viewJson = json.opt("view").optMap();
        return Thomas.model(viewJson);
    }

    protected static FormBehaviorType submitBehaviorFromJson(@NonNull JsonMap json) {
        String submitString = json.opt("submit").optString();
        return FormBehaviorType.from(submitString);
    }

}
