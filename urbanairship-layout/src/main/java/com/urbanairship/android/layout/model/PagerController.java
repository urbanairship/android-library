/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.event.ButtonEvent;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.PagerEvent;
import com.urbanairship.android.layout.event.ReportingEvent;
import com.urbanairship.android.layout.event.WebViewEvent;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.android.layout.reporting.PagerData;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.urbanairship.android.layout.model.Identifiable.identifierFromJson;

/**
 * Controller that manages communication between Pager and PagerIndicator children.
 */
public class PagerController extends LayoutModel implements Identifiable {
    @NonNull
    private final BaseModel view;
    @NonNull
    private final String identifier;

    private String pageIdentifier;
    private int pageIndex = -1;
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
    public boolean onEvent(@NonNull Event event, @Nullable LayoutData layoutData) {
        Logger.verbose("onEvent: %s", event);

        LayoutData override = layoutData.withPagerData(buildPagerData());


        switch (event.getType()) {
            case PAGER_INIT:
                PagerEvent.Init init = (PagerEvent.Init) event;
                boolean isReinit = isInitialized();
                // Trickle the event to update the pager indicator, if this controller contains one.
                trickleEvent(init, override);
                // Update our local state.
                reducePagerState(init);
                // If this is the first time we've been initialized, report and handle actions.
                if (!isReinit) {
                    reportPageView(init);
                    handlePageActions(init);
                }
                return true;

            case PAGER_SCROLL:
                PagerEvent.Scroll scroll = (PagerEvent.Scroll) event;
                // Report the scroll event first, so that the pager context reflects
                // the state of the pager when the swipe was initiated.
                if (!scroll.isInternal()) {
                    reportPageSwipe(scroll);
                }
                // Bubble up any actions so that they can be passed along to our actions runner at the top level.
                handlePageActions(scroll);
                // Trickle the event to update the pager indicator, if this controller contains one.
                trickleEvent(scroll, override);
                // Update our local state.
                reducePagerState(scroll);
                // Report the page view now that we've completed the pager scroll and updated state.
                reportPageView(scroll);
                return true;

            case BUTTON_BEHAVIOR_PAGER_NEXT:
            case BUTTON_BEHAVIOR_PAGER_PREVIOUS:
                trickleEvent(event, override);
                return false;

            case VIEW_INIT:
                if (((Event.ViewInit) event).getViewType() == ViewType.PAGER_INDICATOR) {
                    // Consume indicator init events.
                    return true;
                }
                return super.onEvent(event, override);

            default:
                // Pass along any other events.
                return super.onEvent(event, override);
        }
    }

    private void reducePagerState(PagerEvent event) {
        switch (event.getType()) {
            case PAGER_INIT:
                PagerEvent.Init init = (PagerEvent.Init) event;
                this.count = init.getSize();
                this.pageIndex = init.getPageIndex();
                this.pageIdentifier = init.getPageId();
                this.completed = this.count == 1;
                break;
            case PAGER_SCROLL:
                PagerEvent.Scroll scroll = (PagerEvent.Scroll) event;
                this.pageIndex = scroll.getPageIndex();
                this.pageIdentifier = scroll.getPageId();
                this.completed = this.completed || this.pageIndex == this.count - 1;
                break;
        }
    }

    private void reportPageView(PagerEvent event) {
        PagerData pagerData = buildPagerData();
        bubbleEvent(new ReportingEvent.PageView(pagerData, event.getTime()), LayoutData.pager(pagerData));
    }

    private void reportPageSwipe(PagerEvent.Scroll event) {
        PagerData data = buildPagerData();
        bubbleEvent(new ReportingEvent.PageSwipe(data,
                event.getPreviousPageIndex(),
                event.getPreviousPageId(),
                event.getPageIndex(),
                event.getPageId()),
                LayoutData.pager(data));
    }

    /** Bubble up any page actions set on the event so that they can be handled by the layout host. */
    private void handlePageActions(PagerEvent event) {
        if (event.hasPageActions()) {
            bubbleEvent(new PagerEvent.PageActions(event.getPageActions()), LayoutData.pager(buildPagerData()));
        }
    }

    @NonNull
    private PagerData buildPagerData() {
        String pageId = pageIdentifier == null ? "" : pageIdentifier;
        return new PagerData(identifier, pageIndex, pageId, count, completed);
    }

    /** Returns {@code true} if the controller has been initialized with state from a pager view. */
    private boolean isInitialized() {
        return pageIdentifier != null && pageIndex != -1 && count != -1;
    }
}
