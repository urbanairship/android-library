/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.ui;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.ThomasListener;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.EventListener;
import com.urbanairship.android.layout.event.ReportingEvent;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * An event listener that calls through to a ThomasListener.
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
    public boolean onEvent(@NonNull Event event) {
        Logger.verbose("onEvent: %s", event);
        switch (event.getType()) {
            case REPORTING_EVENT:
                return onReportingEvent((ReportingEvent) event);

            default:
                return false;
        }
    }

    private boolean onReportingEvent(ReportingEvent event) {
        switch (event.getReportType()) {
            case PAGE_VIEW:
                ReportingEvent.PageView pageView = (ReportingEvent.PageView) event;
                listener.onPageView(pageView.getPagerData(), pageView.getState(), pageView.getDisplayedAt());
                return false;

            case PAGE_SWIPE:
                ReportingEvent.PageSwipe pageSwipe = (ReportingEvent.PageSwipe) event;
                listener.onPageSwipe(
                    pageSwipe.getPagerData(),
                    pageSwipe.getToIndex(),
                    pageSwipe.getFromIndex(),
                    pageSwipe.getState()
                );
                return false;

            case BUTTON_TAP:
                ReportingEvent.ButtonTap buttonTap = (ReportingEvent.ButtonTap) event;
                listener.onButtonTap(buttonTap.getButtonId(), buttonTap.getState());
                return false;

            case OUTSIDE_DISMISS:
                ReportingEvent.DismissFromOutside outsideDismiss = (ReportingEvent.DismissFromOutside) event;
                listener.onDismiss(outsideDismiss.getDisplayTime());
                return false;

            case BUTTON_DISMISS:
                ReportingEvent.DismissFromButton buttonDismiss = (ReportingEvent.DismissFromButton) event;
                listener.onDismiss(
                    buttonDismiss.getButtonId(),
                    buttonDismiss.getButtonDescription(),
                    buttonDismiss.isCancel(),
                    buttonDismiss.getDisplayTime(),
                    buttonDismiss.getState()
                );
                return false;

            case FORM_RESULT:
                ReportingEvent.FormResult formResult = (ReportingEvent.FormResult) event;
                listener.onFormResult(formResult.getFormData(), formResult.getState());
                return false;

            case FORM_DISPLAY:
                ReportingEvent.FormDisplay formDisplay = (ReportingEvent.FormDisplay) event;
                listener.onFormDisplay(formDisplay.getFormId(), formDisplay.getState());
                return false;

            default:
                return false;
        }
    }
}
