/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.Layout;
import com.urbanairship.android.layout.property.Direction;
import com.urbanairship.android.layout.property.Size;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;

public class ScrollLayoutModel extends LayoutModel {
    @NonNull
    private final Size size;
    @NonNull
    private final Direction direction;
    @NonNull
    private final BaseModel view;

    public ScrollLayoutModel(@NonNull BaseModel view, @NonNull Direction direction, @NonNull Size size) {
        super(ViewType.SCROLL_LAYOUT);

        this.view = view;
        this.direction = direction;
        this.size = size;
    }

    @NonNull
    public static ScrollLayoutModel fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap viewJson = json.opt("view").optMap();
        JsonMap sizeJson = json.opt("size").optMap();
        String directionString = json.opt("direction").optString();

        BaseModel view = Layout.model(viewJson);
        Direction direction = Direction.from(directionString);
        Size size = Size.fromJson(sizeJson);

        return new ScrollLayoutModel(view, direction, size);
    }

    @NonNull
    public Size getSize() {
        return size;
    }

    @NonNull
    public Direction getDirection() {
        return direction;
    }

    @NonNull
    public BaseModel getView() {
        return view;
    }
}
