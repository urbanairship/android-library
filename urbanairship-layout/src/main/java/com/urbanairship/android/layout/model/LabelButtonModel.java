/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.ButtonClickBehaviorType;
import com.urbanairship.android.layout.property.ButtonEnableBehaviorType;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LabelButtonModel extends ButtonModel {
    @NonNull
    private final LabelModel label;

    public LabelButtonModel(
        @NonNull String id,
        @NonNull LabelModel label,
        @NonNull List<ButtonClickBehaviorType> clickBehaviors,
        @NonNull Map<String, JsonValue> actions,
        @NonNull List<ButtonEnableBehaviorType> enableBehaviors,
        @Nullable Color backgroundColor,
        @Nullable Border border,
        @Nullable String contentDescription
    ) {
        super(ViewType.LABEL_BUTTON, id, clickBehaviors, actions, enableBehaviors, backgroundColor, border, contentDescription);

        this.label = label;
    }

    @NonNull
    public static LabelButtonModel fromJson(@NonNull JsonMap json) throws JsonException {
        String id = Identifiable.identifierFromJson(json);
        JsonMap labelJson = json.opt("label").optMap();
        LabelModel label = LabelModel.fromJson(labelJson);
        List<ButtonClickBehaviorType> clickBehaviors = buttonClickBehaviorsFromJson(json);
        Map<String, JsonValue> actions = actionsFromJson(json);
        List<ButtonEnableBehaviorType> enableBehaviors = buttonEnableBehaviorsFromJson(json);
        Color backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);
        String contentDescription = Accessible.contentDescriptionFromJson(json);

        return new LabelButtonModel(
            id,
            label,
            clickBehaviors,
            actions,
            enableBehaviors,
            backgroundColor,
            border,
            contentDescription
        );
    }

    @NonNull
    public LabelModel getLabel() {
        return label;
    }

    @NonNull
    @Override
    public String reportingDescription() {
        String labelOrIdentifier = !UAStringUtil.isEmpty(getLabel().getText())
            ? getLabel().getText()
            : getIdentifier();
        return !UAStringUtil.isEmpty(getContentDescription())
            ? getContentDescription()
            : labelOrIdentifier;
    }
}
