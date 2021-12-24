/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.event.ButtonEvent;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.PagerEvent;
import com.urbanairship.android.layout.event.ReportingEvent;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.PagerData;
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

    private int index = -1;
    private int count = -1;
    private boolean completed = false;

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
        Logger.verbose("onEvent: %s", event);

        switch (event.getType()) {
            case PAGER_INIT:
                trickleEvent(event);
                reducePagerState((PagerEvent) event);
                reportPageView((PagerEvent) event);
                return true;

            case PAGER_SCROLL:
                PagerEvent.Scroll scroll = (PagerEvent.Scroll) event;
                trickleEvent(scroll);
                reducePagerState(scroll);
                reportPageView(scroll);
                if (!scroll.isInternal()) {
                    reportPageSwipe(scroll);
                }
                return true;

            case BUTTON_BEHAVIOR_PAGER_NEXT:
            case BUTTON_BEHAVIOR_PAGER_PREVIOUS:
                trickleEvent(event);
                return false;

            case VIEW_INIT:
                switch (((Event.ViewInit) event).getViewType()) {
                    case PAGER_INDICATOR:
                        // Consume indicator init events.
                        return true;
                    default:
                        return super.onEvent(event);
                }

            case BUTTON_BEHAVIOR_CANCEL:
            case BUTTON_BEHAVIOR_DISMISS:
                // Update the event with our pager data and continue bubbling it up.
                return super.onEvent(((ButtonEvent) event).overrideState(buildPagerData()));

            case REPORTING_EVENT:
                // Update the event with our pager data and continue bubbling it up.
                return super.onEvent(((ReportingEvent) event).overrideState(buildPagerData()));

            default:
                // Pass along any other events.
                return super.onEvent(event);
        }
    }

    private void reducePagerState(PagerEvent event) {
        switch (event.getType()) {
            case PAGER_INIT:
                PagerEvent.Init init = (PagerEvent.Init) event;
                this.count = init.getSize();
                this.index = init.getPosition();
                break;
            case PAGER_SCROLL:
                PagerEvent.Scroll scroll = (PagerEvent.Scroll) event;
                this.index = scroll.getPosition();
                this.completed = this.completed || this.index == this.count - 1;
                break;
        }
    }

    private void reportPageView(PagerEvent event) {
        bubbleEvent(new ReportingEvent.PageView(buildPagerData(), event.getTime()));
    }

    private void reportPageSwipe(PagerEvent.Scroll event) {
        PagerData data = buildPagerData();
        bubbleEvent(new ReportingEvent.PageSwipe(data, event.getPreviousPosition(), event.getPosition()));
    }

    private PagerData buildPagerData() {
        return new PagerData(identifier, index, count, completed);
    }
}
