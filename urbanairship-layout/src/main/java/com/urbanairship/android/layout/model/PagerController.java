/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Controller that manages communication between Pager and PagerIndicator children.
 */
public class PagerController extends LayoutModel {
    @NonNull
    private final BaseModel view;
    @Nullable
    private final PagerModel pager;
    @Nullable
    private final PagerIndicatorModel pagerIndicator;

    public PagerController(@NonNull BaseModel view) {
        super(ViewType.PAGER_CONTROLLER, null, null);

        this.view = view;

        view.addListener(this);

        pager = Thomas.findByType(PagerModel.class, view);
        pagerIndicator = Thomas.findByType(PagerIndicatorModel.class, view);
    }

    @NonNull
    public static PagerController fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap viewJson = json.opt("view").optMap();
        BaseModel view = Thomas.model(viewJson);

        return new PagerController(view);
    }

    @Override
    public List<BaseModel> getChildren() {
        return Collections.singletonList(view);
    }

    @NonNull
    public BaseModel getView() {
        return view;
    }

    @Override
    public boolean onEvent(@NonNull Event event) {
        Logger.verbose("onEvent: %s", event.getType());

        // If we have both a pager and indicator as children, pass events between them.
        // We always return true because the controller should consume all pager events emitted by its children.
        switch (event.getType()) {
            case PAGER_INIT:
            case PAGER_SCROLL:
                if (pagerIndicator != null) {
                    pagerIndicator.onEvent(event);
                }
                return true;
        }

        // Otherwise, pass the event along.
        return super.onEvent(event);
    }
}
