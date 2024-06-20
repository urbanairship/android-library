package com.urbanairship.android.layout.environment

import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.reporting.LayoutData

internal interface Reporter {
    fun report(event: ReportingEvent, state: LayoutData)
}

internal class ExternalReporter(val listener: ThomasListenerInterface) : Reporter {

    override fun report(event: ReportingEvent, state: LayoutData) {
        when (event) {
            is ReportingEvent.PageView -> with(event) {
                listener.onPageView(pagerData, state, displayedAt)
            }
            is ReportingEvent.PageSwipe -> with(event) {
                listener.onPageSwipe(
                    pagerData, toPageIndex, toPageId, fromPageIndex, fromPageId, state)
            }
            is ReportingEvent.PageGesture -> with(event) {
                listener.onPagerGesture(gestureId, reportingMetadata, state)
            }
            is ReportingEvent.PageAction -> with(event) {
                listener.onPagerAutomatedAction(actionId, reportingMetadata, state)
            }
            is ReportingEvent.ButtonTap -> with(event) {
                listener.onButtonTap(buttonId, reportingMetadata, state)
            }
            is ReportingEvent.DismissFromOutside -> with(event) {
                listener.onDismiss(displayTime)
            }
            is ReportingEvent.DismissFromButton -> with(event) {
                listener.onDismiss(buttonId, buttonDescription, isCancel, displayTime, state)
            }
            is ReportingEvent.FormResult -> with(event) {
                listener.onFormResult(formData, state)
            }
            is ReportingEvent.FormDisplay -> with(event) {
                listener.onFormDisplay(formInfo, state)
            }
            is ReportingEvent.TimedOut -> with(event) {
                listener.onTimedOut(layoutData)
            }
            is ReportingEvent.VisibilityChanged -> with(event) {
                listener.onVisibilityChanged(isVisible, isForegrounded)
            }
        }
    }
}
