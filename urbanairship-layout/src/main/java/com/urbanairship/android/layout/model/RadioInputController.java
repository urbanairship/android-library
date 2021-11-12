/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Controller for radio inputs.
 */
public class RadioInputController extends LayoutModel implements Identifiable, Accessible, Validatable {
    @NonNull
    private final String identifier;
    @NonNull
    private final BaseModel view;
    @Nullable
    private final Boolean isRequired;
    @Nullable
    private final String contentDescription;

    @NonNull
    private final List<RadioInputModel> radioInputs;

    public RadioInputController(
        @NonNull String identifier,
        @NonNull BaseModel view,
        @Nullable Boolean isRequired,
        @Nullable String contentDescription
    ) {
        super(ViewType.RADIO_INPUT_CONTROLLER, null, null);

        this.identifier = identifier;
        this.view = view;
        this.isRequired = isRequired;
        this.contentDescription = contentDescription;

        view.addListener(this);

        radioInputs = Thomas.findAllByType(RadioInputModel.class, view);
    }

    @NonNull
    public static RadioInputController fromJson(@NonNull JsonMap json) throws JsonException {
        String identifier = Identifiable.identifierFromJson(json);
        JsonMap viewJson = json.opt("view").optMap();
        Boolean isRequired = Validatable.requiredFromJson(json);
        String contentDescription = Accessible.contentDescriptionFromJson(json);

        BaseModel view = Thomas.model(viewJson);

        return new RadioInputController(identifier, view, isRequired, contentDescription);
    }

    @Override
    public List<BaseModel> getChildren() {
        return Collections.singletonList(view);
    }

    @NonNull
    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Nullable
    @Override
    public Boolean isRequired() {
        return isRequired;
    }

    @Nullable
    @Override
    public String getContentDescription() {
        return contentDescription;
    }

    @NonNull
    public BaseModel getView() {
        return view;
    }

    @Nullable
    public Boolean getRequired() {
        return isRequired;
    }

    @Override
    public boolean onEvent(@NonNull Event event) {
        Logger.verbose("onEvent: %s", event.getType());

        // TODO: switch on radio events and consume any that should be internal to the controller and its children.

        // Pass along any other events
        return super.onEvent(event);
    }
}
