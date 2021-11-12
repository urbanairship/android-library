/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.ButtonClickBehaviorType;
import com.urbanairship.android.layout.property.ButtonImage;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ImageButtonModel extends ButtonModel {
    @NonNull
    private final ButtonImage image;

    public ImageButtonModel(
        @NonNull String id,
        @NonNull ButtonImage image,
        @NonNull List<ButtonClickBehaviorType> behaviors,
        @NonNull List<JsonMap> actions,
        @Nullable @ColorInt Integer backgroundColor,
        @Nullable Border border,
        @Nullable String contentDescription
    ) {
        super(ViewType.IMAGE_BUTTON, id, behaviors, actions, backgroundColor, border, contentDescription);

        this.image = image;
    }

    @NonNull
    public static ImageButtonModel fromJson(@NonNull JsonMap json) throws JsonException {
        String id = Identifiable.identifierFromJson(json);
        JsonMap imageJson = json.opt("image").optMap();
        ButtonImage image = ButtonImage.fromJson(imageJson);
        List<ButtonClickBehaviorType> behaviors = buttonClickBehaviorsFromJson(json);
        List<JsonMap> actions = actionsFromJson(json);
        @ColorInt Integer backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);
        String contentDescription = Accessible.contentDescriptionFromJson(json);

        return new ImageButtonModel(id, image, behaviors, actions, backgroundColor, border, contentDescription);
    }

    //
    // Fields
    //

    @NonNull
    public ButtonImage getImage() {
        return image;
    }

    //
    // View Actions
    //

    public void onClick() {
        bubbleEvent(new Event.ButtonClick(this));
    }
}
