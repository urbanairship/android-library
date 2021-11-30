/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class PagerModel extends LayoutModel implements Identifiable {
    @NonNull
    private final String identifier;
    @NonNull
    private final List<BaseModel> items;
    @Nullable
    private final Boolean disableSwipe;

    public PagerModel(@NonNull String identifier, @NonNull List<BaseModel> items, @Nullable Boolean disableSwipe, @Nullable Color backgroundColor, @Nullable Border border) {
        super(ViewType.PAGER, backgroundColor, border);

        this.identifier = identifier;
        this.items = items;
        this.disableSwipe = disableSwipe;
    }

    @NonNull
    public static PagerModel fromJson(@NonNull JsonMap json) throws JsonException {
        String identifier = Identifiable.identifierFromJson(json);
        JsonList itemsJson = json.opt("items").optList();
        Boolean disableSwipe = json.opt("disable_swipe").getBoolean();
        Color backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);

        List<BaseModel> items = new ArrayList<>(itemsJson.size());
        for (int i = 0; i < itemsJson.size(); i++) {
            JsonMap itemJson = itemsJson.get(i).optMap();
            BaseModel item = Thomas.model(itemJson);
            items.add(item);
        }

        return new PagerModel(identifier, items, disableSwipe, backgroundColor, border);
    }

    @Override
    public List<BaseModel> getChildren() {
        return items;
    }

    //
    // Fields
    //

    @Override
    @NonNull
    public String getIdentifier() {
        return identifier;
    }

    @NonNull
    public List<BaseModel> getItems() {
        return items;
    }

    @Nullable
    public Boolean getDisableSwipe() {
        return disableSwipe;
    }

    //
    // View Actions
    //

    public void onScrollTo(int position) {
        bubbleEvent(new Event.PagerScroll(position));
    }

    public void onConfigured(int position) {
        bubbleEvent(new Event.PagerInit(items.size(), position));
    }
}
