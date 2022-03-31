/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.Direction;
import com.urbanairship.android.layout.property.Margin;
import com.urbanairship.android.layout.property.Size;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LinearLayoutModel extends LayoutModel {
    @NonNull
    private final Direction direction;
    @NonNull
    private final List<Item> items;
    @NonNull
    private final List<BaseModel> children = new ArrayList<>();

    public LinearLayoutModel(@NonNull Direction direction, @NonNull List<Item> items,
                             @Nullable Color backgroundColor, @Nullable Border border) {
        super(ViewType.LINEAR_LAYOUT, backgroundColor, border);

        this.direction = direction;
        this.items = items;

        for (Item item : items) {
            item.view.addListener(this);
            children.add(item.view);
        }
    }

    @NonNull
    public static LinearLayoutModel fromJson(@NonNull JsonMap json) throws JsonException {
        String directionString = json.opt("direction").optString();
        JsonList itemsJson = json.opt("items").optList();
        Direction direction = Direction.from(directionString);
        List<Item> items = Item.fromJsonList(itemsJson);

        if (json.opt("randomize_children").getBoolean(false)) {
            Collections.shuffle(items);
        }

        Color backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);

        return new LinearLayoutModel(direction, items, backgroundColor, border);
    }

    @NonNull
    public Direction getDirection() {
        return direction;
    }

    @NonNull
    public List<Item> getItems() {
        return new ArrayList<>(items);
    }

    @NonNull
    public List<BaseModel> getChildren() {
       return children;
    }


    public static class Item {
        @NonNull
        private final BaseModel view;
        @NonNull
        private final Size size;
        @Nullable
        private final Margin margin;

        public Item(@NonNull BaseModel view, @NonNull Size size, @Nullable Margin margin) {
            this.view = view;
            this.size = size;
            this.margin = margin;
        }

        @NonNull
        public static Item fromJson(@NonNull JsonMap json) throws JsonException {
            JsonMap viewJson = json.opt("view").optMap();
            JsonMap sizeJson = json.opt("size").optMap();
            JsonMap marginJson = json.opt("margin").optMap();

            BaseModel view = Thomas.model(viewJson);
            Size size = Size.fromJson(sizeJson);
            Margin margin = marginJson.isEmpty() ? null : Margin.fromJson(marginJson);

            return new Item(view, size, margin);
        }

        @NonNull
        public static List<Item> fromJsonList(@NonNull JsonList json) throws JsonException {
            List<Item> items = new ArrayList<>(json.size());
            for (int i = 0; i < json.size(); i++) {
                JsonMap itemJson = json.get(i).optMap();
                Item item = Item.fromJson(itemJson);
                items.add(item);
            }
            return items;
        }

        @NonNull
        public BaseModel getView() {
            return view;
        }

        @NonNull
        public Size getSize() {
            return size;
        }

        @Nullable
        public Margin getMargin() {
            return margin;
        }
    }
}
