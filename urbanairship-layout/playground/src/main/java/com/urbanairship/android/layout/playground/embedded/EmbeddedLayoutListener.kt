package com.urbanairship.android.layout.playground.embedded

import com.urbanairship.UALog
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.android.layout.reporting.FormInfo
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.json.JsonValue

internal class EmbeddedLayoutListener(
    private val layoutName: String
) : ThomasListenerInterface {

    override fun onPageView(pagerData: PagerData, state: LayoutData, displayedAt: Long) {
        UALog.d {
            "$layoutName - onPageView(pagerData: $pagerData, state: $state, displayedAt: $displayedAt)"
        }
    }

    override fun onPageSwipe(
        pagerData: PagerData,
        toPageIndex: Int,
        toPageId: String,
        fromPageIndex: Int,
        fromPageId: String,
        state: LayoutData
    ) {
        UALog.d {
            "$layoutName - onPageSwipe(pagerData: $pagerData, toPageIndex: $toPageIndex," +
                    " toPageId: $toPageId, fromPageIndex: $fromPageIndex, " +
                    "fromPageId: $fromPageId, state: $state)" }
    }

    override fun onButtonTap(
        buttonId: String,
        reportingMetadata: JsonValue?,
        state: LayoutData
    ) {
        UALog.d { "$layoutName - onButtonTap(buttonId: $buttonId, state: $state)" }
    }

    override fun onDismiss(displayTime: Long) {
        UALog.d { "$layoutName - onDismiss(displayTime: $displayTime)" }
    }

    override fun onDismiss(
        buttonId: String,
        buttonDescription: String?,
        cancel: Boolean,
        displayTime: Long,
        state: LayoutData
    ) {
        UALog.d {
            "$layoutName - onDismiss(buttonId: $buttonId, buttonDescription: $buttonDescription, " +
                    "cancel: $cancel, displayTime: $displayTime, state: $state)"
        }
    }

    override fun onFormResult(formData: FormData.BaseForm, state: LayoutData) {
        UALog.d { "$layoutName - onFormResult(formData: ${formData.toJsonValue()}, state: $state)" }
    }

    override fun onFormDisplay(formInfo: FormInfo, state: LayoutData) {
        UALog.d { "$layoutName - onFormDisplay(formInfo: $formInfo, state: $state)" }
    }

    override fun onPagerGesture(
        gestureId: String,
        reportingMetadata: JsonValue?,
        state: LayoutData
    ) {
        UALog.d { "$layoutName - onPagerGesture(gestureId: $gestureId, state: $state)" }
    }

    override fun onPagerAutomatedAction(
        actionId: String,
        reportingMetadata: JsonValue?,
        state: LayoutData
    ) {
        UALog.d { "$layoutName - onPagerAutomatedAction(actionId: $actionId, state: $state)" }
    }

    override fun onVisibilityChanged(isVisible: Boolean, isForegrounded: Boolean) {
        UALog.d { "$layoutName - onVisibilityChanged(isVisible: $isVisible, isForegrounded: $isForegrounded)" }
    }

    override fun onTimedOut(state: LayoutData?) {
        UALog.d { "$layoutName - onTimedOut(layoutContext: $state)" }
    }

}
