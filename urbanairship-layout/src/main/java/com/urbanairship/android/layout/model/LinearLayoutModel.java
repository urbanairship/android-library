/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.Layout;
import com.urbanairship.android.layout.property.Direction;
import com.urbanairship.android.layout.property.Margin;
import com.urbanairship.android.layout.property.Size;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LinearLayoutModel extends LayoutModel {
    @NonNull
    private final Direction direction;
    @NonNull
    private final List<Item> items;

    public LinearLayoutModel(@NonNull Direction direction, @NonNull List<Item> items) {
        super(ViewType.LINEAR_LAYOUT);

        this.direction = direction;
        this.items = items;
    }

    @NonNull
    public static LinearLayoutModel fromJson(@NonNull JsonMap json) throws JsonException {
        String directionString = json.opt("direction").optString();
        JsonList itemsJson = json.opt("items").optList();

        Direction direction = Direction.from(directionString);
        List<Item> items = Item.fromJsonList(itemsJson);

        return new LinearLayoutModel(direction, items);
    }

    @NonNull
    public Direction getDirection() {
        return direction;
    }

    @NonNull
    public List<Item> getItems() {
        return new ArrayList<>(items);
    }

    public static class Item {
        @NonNull
        private final BaseModel view;
        @Nullable
        private final Margin margin;
        @Nullable
        private final Float weight;
        @Nullable
        private final Size size;

        public Item(@NonNull BaseModel view, @Nullable Margin margin, @Nullable Float weight, @Nullable Size size) {
            this.view = view;
            this.margin = margin;
            this.weight = weight;
            this.size = size;
        }

        @NonNull
        public static Item fromJson(@NonNull JsonMap json) throws JsonException {
            JsonMap viewJson = json.opt("view").optMap();
            JsonMap marginJson = json.opt("margin").optMap();
            Number weightString = json.opt("weight").getNumber();
            JsonMap sizeJson = json.opt("size").optMap();

            BaseModel view = Layout.model(viewJson);
            Margin margin = marginJson.isEmpty() ? null : Margin.fromJson(marginJson);
            Float weight = weightString == null ? null : weightString.floatValue();
            Size size = sizeJson.isEmpty() ? Size.AUTO : Size.fromJson(sizeJson);

            return new Item(view, margin, weight, size);
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

        @Nullable
        public Float getWeight() {
            return weight;
        }

        @Nullable
        public Size getSize() {
            return size;
        }

        @Nullable
        public Margin getMargin() {
            return margin;
        }
    }
}
