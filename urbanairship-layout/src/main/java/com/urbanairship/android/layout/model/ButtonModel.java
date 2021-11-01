/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import android.graphics.Color;

import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.ButtonBehavior;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ButtonModel extends BaseModel {
    @NonNull
    private final String id;
    @NonNull
    private final LabelModel label;

    @Nullable
    private final Border border;
    @Nullable
    @ColorInt
    private final Integer backgroundColor;
    @Nullable
    private final ButtonBehavior behavior;
    // TODO: should probably concrete this field up? fine for now, though...
    @Nullable
    private final JsonMap actions;

    public ButtonModel(
        @NonNull String id,
        @NonNull LabelModel label,
        @Nullable Border border,
        @Nullable @ColorInt Integer backgroundColor,
        @Nullable ButtonBehavior behavior,
        @Nullable JsonMap actions) {
        super(ViewType.BUTTON);

        this.id = id;
        this.label = label;
        this.border = border;
        this.backgroundColor = backgroundColor;
        this.behavior = behavior;
        this.actions = actions;
    }

    @NonNull
    public static ButtonModel fromJson(@NonNull JsonMap json) throws JsonException {
        String id = json.opt("identifier").optString();
        JsonMap labelJson = json.opt("label").optMap();
        JsonMap borderJson = json.opt("border").optMap();
        String colorString = json.opt("color").optString();
        String behaviorString = json.opt("behavior").optString();
        JsonMap actions = json.opt("actions").optMap();

        LabelModel label = LabelModel.fromJson(labelJson);
        Border border = borderJson.isEmpty() ? null : Border.fromJson(borderJson);
        @ColorInt Integer color = colorString.isEmpty() ? null : Color.parseColor(colorString);

        ButtonBehavior behavior = behaviorString.isEmpty() ? null : ButtonBehavior.from(behaviorString);

        return new ButtonModel(id, label, border, color, behavior, actions);
    }

    //
    // Fields
    //

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public LabelModel getLabel() {
        return label;
    }

    @Nullable
    public Border getBorder() {
        return border;
    }

    @Nullable
    @ColorInt
    public Integer getBackgroundColor() {
        return backgroundColor;
    }

    @Nullable
    public ButtonBehavior getBehavior() {
        return behavior;
    }

    @Nullable
    public JsonMap getActions() {
        return actions;
    }

    //
    // View Actions
    //

    public void onClick() {
        bubbleEvent(new Event.ButtonClick(this));
    }
}
