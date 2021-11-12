/*
 Copyright Airship and Contributors
 */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.ButtonClickBehaviorType;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class ButtonModel extends BaseModel implements Accessible, Identifiable {
    @NonNull
    private final String identifier;
    @NonNull
    private final List<ButtonClickBehaviorType> buttonClickBehaviors;
    @NonNull
    private final List<JsonMap> actions;
    @Nullable
    private final String contentDescription;

    protected ButtonModel(
        @NonNull ViewType type,
        @NonNull String identifier,
        @NonNull List<ButtonClickBehaviorType> buttonClickBehaviors,
        @NonNull List<JsonMap> actions,
        @Nullable @ColorInt Integer backgroundColor,
        @Nullable Border border,
        @Nullable String contentDescription
    ) {
        super(type, backgroundColor, border);

        this.identifier = identifier;
        this.buttonClickBehaviors = buttonClickBehaviors;
        this.actions = actions;
        this.contentDescription = contentDescription;
    }

    public static List<ButtonClickBehaviorType> buttonClickBehaviorsFromJson(@NonNull JsonMap json) {
        JsonList clickBehaviorsList = json.opt("button_click").optList();
        return ButtonClickBehaviorType.fromList(clickBehaviorsList);
    }

    public static List<JsonMap> actionsFromJson(@NonNull JsonMap json) {
        JsonList actionsJson = json.opt("actions").optList();
        if (actionsJson.isEmpty()) {
            return Collections.emptyList();
        }

        List<JsonMap> actions = new ArrayList<>(actionsJson.size());
        for (JsonValue value : actionsJson) {
            actions.add(value.optMap());
        }
        return actions;
    }

    @Override
    @NonNull
    public String getIdentifier() {
        return identifier;
    }

    @Override
    @Nullable
    public String getContentDescription() {
        return contentDescription;
    }

    @NonNull
    public List<ButtonClickBehaviorType> getButtonClickBehaviors() {
        return buttonClickBehaviors;
    }

    @NonNull
    public List<JsonMap> getActions() {
        return actions;
    }
}
