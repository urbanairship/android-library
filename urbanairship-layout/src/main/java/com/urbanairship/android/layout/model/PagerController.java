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

import static com.urbanairship.android.layout.model.Identifiable.identifierFromJson;

/**
 * Controller that manages communication between Pager and PagerIndicator children.
 */
public class PagerController extends LayoutModel implements Identifiable {
    @NonNull
    private final BaseModel view;
    @NonNull
    private final String identifier;

    public PagerController(@NonNull BaseModel view, @NonNull String identifier) {
        super(ViewType.PAGER_CONTROLLER, null, null);

        this.view = view;
        this.identifier = identifier;

        view.addListener(this);
    }

    @NonNull
    public static PagerController fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap viewJson = json.opt("view").optMap();
        BaseModel view = Thomas.model(viewJson);
        String identifier = identifierFromJson(json);

        return new PagerController(view, identifier);
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
    @NonNull
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public boolean onEvent(@NonNull Event event) {
        Logger.verbose("onEvent: %s", event.getType());

        switch (event.getType()) {
            case PAGER_INIT:
            case PAGER_SCROLL:
            case BUTTON_BEHAVIOR_PAGER_NEXT:
            case BUTTON_BEHAVIOR_PAGER_PREVIOUS:
                trickleEvent(event);
                return true;

            case VIEW_INIT:
                switch (((Event.ViewInit) event).getViewType()) {
                    case PAGER_INDICATOR:
                        // Consume indicator init events.
                        return true;
                    default:
                        return super.onEvent(event);
                }

            default:
                // Pass along any other events.
                return super.onEvent(event);
        }
    }
}
