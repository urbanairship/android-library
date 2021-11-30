/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.Direction;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ScrollLayoutModel extends LayoutModel {
    @NonNull
    private final Direction direction;
    @NonNull
    private final BaseModel view;

    public ScrollLayoutModel(@NonNull BaseModel view, @NonNull Direction direction, @Nullable Color backgroundColor, @Nullable Border border) {
        super(ViewType.SCROLL_LAYOUT, backgroundColor, border);

        this.view = view;
        this.direction = direction;

        view.addListener(this);
    }

    @NonNull
    public static ScrollLayoutModel fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap viewJson = json.opt("view").optMap();
        String directionString = json.opt("direction").optString();

        BaseModel view = Thomas.model(viewJson);
        Direction direction = Direction.from(directionString);
        Color backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);

        return new ScrollLayoutModel(view, direction, backgroundColor, border);
    }

    @NonNull
    public Direction getDirection() {
        return direction;
    }

    @NonNull
    public BaseModel getView() {
        return view;
    }

    @NonNull
    public List<BaseModel> getChildren() {
        return Collections.singletonList(view);
    }
}
