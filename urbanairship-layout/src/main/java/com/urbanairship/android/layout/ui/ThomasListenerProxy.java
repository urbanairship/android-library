/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.ui;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.ThomasListener;
import com.urbanairship.android.layout.event.ButtonEvent;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.EventListener;
import com.urbanairship.android.layout.event.ReportingEvent;
import com.urbanairship.android.layout.reporting.LayoutData;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * An event listener that calls through to a ThomasListener.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class ThomasListenerProxy implements EventListener {

    @NonNull
    private final ThomasListener listener;

    public ThomasListenerProxy(@NonNull ThomasListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onEvent(@NonNull Event event, @NonNull LayoutData layoutData) {
        Logger.verbose("onEvent: %s layoutData: %s", event, layoutData);
        switch (event.getType()) {
            case REPORTING_EVENT:
                onReportingEvent((ReportingEvent) event, layoutData);
                break;

            case BUTTON_ACTIONS:
            case PAGER_PAGE_ACTIONS:
                onActionsEvent((Event.EventWithActions) event, layoutData);
                break;

            default:
                break;
        }

        return false;
    }

    private void onActionsEvent(@NonNull Event.EventWithActions event, @NonNull LayoutData layoutData) {
        listener.onRunActions(event.getActions(), layoutData);
    }

    private void onReportingEvent(@NonNull ReportingEvent event, @NonNull LayoutData layoutData) {
        switch (event.getReportType()) {
            case PAGE_VIEW:
                ReportingEvent.PageView pageView = (ReportingEvent.PageView) event;
                listener.onPageView(pageView.getPagerData(), layoutData, pageView.getDisplayedAt());
                break;

            case PAGE_SWIPE:
                ReportingEvent.PageSwipe pageSwipe = (ReportingEvent.PageSwipe) event;
                listener.onPageSwipe(
                        pageSwipe.getPagerData(),
                        pageSwipe.getToPageIndex(),
                        pageSwipe.getToPageId(),
                        pageSwipe.getFromPageIndex(),
                        pageSwipe.getFromPageId(),
                        layoutData
                );
                break;

            case BUTTON_TAP:
                ReportingEvent.ButtonTap buttonTap = (ReportingEvent.ButtonTap) event;
                listener.onButtonTap(buttonTap.getButtonId(), layoutData);
                break;

            case OUTSIDE_DISMISS:
                ReportingEvent.DismissFromOutside outsideDismiss = (ReportingEvent.DismissFromOutside) event;
                listener.onDismiss(outsideDismiss.getDisplayTime());
                break;

            case BUTTON_DISMISS:
                ReportingEvent.DismissFromButton buttonDismiss = (ReportingEvent.DismissFromButton) event;
                listener.onDismiss(
                        buttonDismiss.getButtonId(),
                        buttonDismiss.getButtonDescription(),
                        buttonDismiss.isCancel(),
                        buttonDismiss.getDisplayTime(),
                        layoutData
                );
                break;

            case FORM_RESULT:
                ReportingEvent.FormResult formResult = (ReportingEvent.FormResult) event;
                listener.onFormResult(formResult.getFormData(), layoutData);
                break;

            case FORM_DISPLAY:
                ReportingEvent.FormDisplay formDisplay = (ReportingEvent.FormDisplay) event;
                listener.onFormDisplay(formDisplay.getFormInfo(), layoutData);
                break;

            default:
                break;
        }
    }

}
