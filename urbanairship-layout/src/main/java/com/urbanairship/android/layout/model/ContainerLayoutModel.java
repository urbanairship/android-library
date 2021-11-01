/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.Layout;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Margin;
import com.urbanairship.android.layout.property.Position;
import com.urbanairship.android.layout.property.Size;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ContainerLayoutModel extends LayoutModel  {
    @NonNull
    private final List<Item> items;
    @Nullable
    private final Border border;
    @Nullable
    @ColorInt
    private final Integer backgroundColor;

    @NonNull
    private final List<BaseModel> children = new ArrayList<>();

    public ContainerLayoutModel(@NonNull List<Item> items, @Nullable Border border, @Nullable @ColorInt Integer backgroundColor) {
        super(ViewType.CONTAINER);

        this.items = items;
        this.border = border;
        this.backgroundColor = backgroundColor;

        for (Item item : items) {
            item.view.addListener(this);
            children.add(item.view);
        }
    }

    @NonNull
    public static ContainerLayoutModel fromJson(@NonNull JsonMap json) throws JsonException {
        JsonList itemsJson = json.opt("items").optList();
        List<Item> items = Item.fromJsonList(itemsJson);
        Border border = borderFromJson(json);
        @ColorInt Integer backgroundColor = backgroundColorFromJson(json);

        return new ContainerLayoutModel(items, border, backgroundColor);
    }

    @NonNull
    public List<Item> getItems() {
        return items;
    }

    @Nullable
    public Border getBorder() {
        return border;
    }

    @Nullable
    @ColorInt
    public
    Integer getBackgroundColor() {
        return backgroundColor;
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
        @Nullable
        private final Size maxSize;

        public Item(
            @NonNull Position position,
            @NonNull Size size,
            @NonNull BaseModel view,
            @Nullable Margin margin,
            @Nullable Size maxSize) {
            this.position = position;
            this.size = size;
            this.view = view;
            this.margin = margin;
            this.maxSize = maxSize;
        }

        @NonNull
        public static Item fromJson(@NonNull JsonMap json) throws JsonException {
            JsonMap positionJson = json.opt("position").optMap();
            JsonMap sizeJson = json.opt("size").optMap();
            JsonMap viewJson = json.opt("view").optMap();
            JsonMap marginJson = json.opt("margin").optMap();
            JsonMap maxSizeJson = json.opt("maxSize").optMap();

            Position position = Position.fromJson(positionJson);
            Size size = Size.fromJson(sizeJson);
            BaseModel view = Layout.model(viewJson);
            Margin margin = marginJson.isEmpty() ? null : Margin.fromJson(marginJson);
            Size maxSize = maxSizeJson.isEmpty() ? null : Size.fromJson(maxSizeJson);

            return new Item(position, size, view, margin, maxSize);
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

        @Nullable
        public Size getMaxSize() {
            return maxSize;
        }
    }
}
