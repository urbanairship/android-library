/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.Margin;
import com.urbanairship.android.layout.property.Position;
import com.urbanairship.android.layout.property.Size;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ContainerLayoutModel extends LayoutModel  {
    @NonNull
    private final List<Item> items;

    @NonNull
    private final List<BaseModel> children = new ArrayList<>();

    public ContainerLayoutModel(@NonNull List<Item> items, @Nullable Border border, @Nullable Color backgroundColor) {
        super(ViewType.CONTAINER, backgroundColor, border);

        this.items = items;

        for (Item item : items) {
            item.view.addListener(this);
            children.add(item.view);
        }
    }

    @NonNull
    public static ContainerLayoutModel fromJson(@NonNull JsonMap json) throws JsonException {
        JsonList itemsJson = json.opt("items").optList();
        List<Item> items = Item.fromJsonList(itemsJson);
        Color backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);

        return new ContainerLayoutModel(items, border, backgroundColor);
    }

    @NonNull
    public List<Item> getItems() {
        return items;
    }

    @NonNull
    public List<BaseModel> getChildren() {
        return children;
    }

    public static class Item {
        @NonNull
        private final Position position;
        @NonNull
        private final Size size;
        @NonNull
        private final BaseModel view;
        @Nullable
        private final Margin margin;

        public Item(
            @NonNull Position position,
            @NonNull Size size,
            @NonNull BaseModel view,
            @Nullable Margin margin) {
            this.position = position;
            this.size = size;
            this.view = view;
            this.margin = margin;
        }

        @NonNull
        public static Item fromJson(@NonNull JsonMap json) throws JsonException {
            JsonMap positionJson = json.opt("position").optMap();
            JsonMap sizeJson = json.opt("size").optMap();
            JsonMap viewJson = json.opt("view").optMap();
            JsonMap marginJson = json.opt("margin").optMap();

            Position position = Position.fromJson(positionJson);
            Size size = Size.fromJson(sizeJson);
            BaseModel view = Thomas.model(viewJson);
            Margin margin = marginJson.isEmpty() ? null : Margin.fromJson(marginJson);

            return new Item(position, size, view, margin);
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
        public Position getPosition() {
            return position;
        }

        @NonNull
        public Size getSize() {
            return size;
        }

        @NonNull
        public BaseModel getView() {
            return view;
        }

        @Nullable
        public Margin getMargin() {
            return margin;
        }
    }
}
