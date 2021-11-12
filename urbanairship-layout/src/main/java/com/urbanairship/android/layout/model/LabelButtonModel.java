/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.ButtonClickBehaviorType;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LabelButtonModel extends ButtonModel {
    @NonNull
    private final LabelModel label;

    public LabelButtonModel(
        @NonNull String id,
        @NonNull LabelModel label,
        @NonNull List<ButtonClickBehaviorType> behaviors,
        @NonNull List<JsonMap> actions,
        @Nullable @ColorInt Integer backgroundColor,
        @Nullable Border border,
        @Nullable String contentDescription
    ) {
        super(ViewType.LABEL_BUTTON, id, behaviors, actions, backgroundColor, border, contentDescription);

        this.label = label;
    }

    @NonNull
    public static LabelButtonModel fromJson(@NonNull JsonMap json) throws JsonException {
        String id = Identifiable.identifierFromJson(json);
        JsonMap labelJson = json.opt("label").optMap();
        LabelModel label = LabelModel.fromJson(labelJson);
        List<ButtonClickBehaviorType> behaviors = buttonClickBehaviorsFromJson(json);
        List<JsonMap> actions = actionsFromJson(json);
        @ColorInt Integer backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);
        String contentDescription = Accessible.contentDescriptionFromJson(json);

        return new LabelButtonModel(id, label,  behaviors, actions, backgroundColor, border, contentDescription);
    }

    //
    // Fields
    //

    @NonNull
    public LabelModel getLabel() {
        return label;
    }

    //
    // View Actions
    //

    public void onClick() {
        bubbleEvent(new Event.ButtonClick(this));
    }
}
