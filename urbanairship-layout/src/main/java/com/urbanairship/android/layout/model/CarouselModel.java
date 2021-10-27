/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.Layout;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

public class CarouselModel extends BaseModel {
    @NonNull
    private final String identifier;
    @NonNull
    private final List<BaseModel> items;

    public CarouselModel(@NonNull String identifier, @NonNull List<BaseModel> items) {
        super(ViewType.CAROUSEL);

        this.identifier = identifier;
        this.items = items;
    }

    @NonNull
    public static CarouselModel fromJson(@NonNull JsonMap json) throws JsonException {
        String identifier = json.opt("identifier").optString();
        JsonList itemsJson = json.opt("items").optList();

        List<BaseModel> items = new ArrayList<>(itemsJson.size());
        for (int i = 0; i < itemsJson.size(); i++) {
            JsonMap itemJson = itemsJson.get(i).optMap();
            BaseModel item = Layout.model(itemJson);
            items.add(item);
        }

        return new CarouselModel(identifier, items);
    }

    @NonNull
    public String getIdentifier() {
        return identifier;
    }

    @NonNull
    public List<BaseModel> getItems() {
        return items;
    }
}
