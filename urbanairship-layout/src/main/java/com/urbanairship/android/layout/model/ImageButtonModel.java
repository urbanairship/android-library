/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.property.ButtonBehavior;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ImageButtonModel extends BaseModel {
    @NonNull
    private final String id;
    @NonNull
    private final String url;

    @Nullable
    private final ButtonBehavior behavior;
    // TODO: should probably concrete this field up? fine for now, though...
    @Nullable
    private final JsonMap actions;

    public ImageButtonModel(
        @NonNull String id,
        @NonNull String url,
        @Nullable ButtonBehavior behavior,
        @Nullable JsonMap actions) {
        super(ViewType.IMAGE_BUTTON);

        this.id = id;
        this.url = url;
        this.behavior = behavior;
        this.actions = actions;
    }

    @NonNull
    public static ImageButtonModel fromJson(@NonNull JsonMap json) {
        String id = json.opt("identifier").optString();
        String url = json.opt("url").optString();
        String behaviorString = json.opt("behavior").optString();
        JsonMap actions = json.opt("actions").optMap();

        ButtonBehavior behavior = behaviorString.isEmpty() ? null : ButtonBehavior.from(behaviorString);

        return new ImageButtonModel(id, url, behavior, actions);
    }

    //
    // Fields
    //

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getUrl() {
        return url;
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
