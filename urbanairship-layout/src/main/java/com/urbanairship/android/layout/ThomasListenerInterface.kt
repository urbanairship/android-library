/* Copyright Airship and Contributors */
package com.urbanairship.android.layout

import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.reporting.FormData.BaseForm
import com.urbanairship.android.layout.reporting.FormInfo
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.json.JsonValue

/** Thomas listener. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface ThomasListenerInterface {

    /**
     * Called when a pager changes its page.
     *
     * @param pagerData The pager data.
     * @param state The layout state.
     * @param displayedAt A timestamp from `DisplayTimer`.
     */
    public fun onPageView(pagerData: PagerData, state: LayoutData, displayedAt: Long)

    /**
     * Called when a pager changes its page due to a swipe gesture.
     *
     * @param pagerData The pager data.
     * @param toPageIndex The resulting page index.
     * @param toPageId The page resulting page Id.
     * @param fromPageIndex The page index that the swipe originated on.
     * @param fromPageId The page Id that the swipe originated on.
     * @param state The layout state.
     */
    public fun onPageSwipe(
        pagerData: PagerData,
        toPageIndex: Int,
        toPageId: String,
        fromPageIndex: Int,
        fromPageId: String,
        state: LayoutData
    )

    /**
     * Called when a button is tapped.
     *
     * @param buttonId The button Id.
     * @param reportingMetadata Optional reporting metadata.
     * @param state The layout state.
     */
    public fun onButtonTap(buttonId: String, reportingMetadata: JsonValue?, state: LayoutData)

    /**
     * Called when the view is dismissed from outside the view.
     *
     * @param displayTime The total display time in milliseconds.
     */
    public fun onDismiss(displayTime: Long)

    /**
     * Called when the view is dismissed from a button.
     *
     * @param buttonId The button Id.
     * @param buttonDescription The button description.
     * @param cancel If the experience should be cancelled.
     * @param displayTime The total display time in milliseconds.
     * @param state The layout state.
     */
    public fun onDismiss(
        buttonId: String,
        buttonDescription: String?,
        cancel: Boolean,
        displayTime: Long,
        state: LayoutData
    )

    /**
     * Called when a form is submitted.
     *
     * @param formData The form data.
     * @param state The layout state.
     */
    public fun onFormResult(formData: BaseForm, state: LayoutData)

    /**
     * Called when a form is displayed.
     *
     * @param formInfo The form info.
     * @param state The layout state.
     */
    public fun onFormDisplay(formInfo: FormInfo, state: LayoutData)

    /**
     * Called when a pager changes its page due to a tap.
     *
     * @param gestureId The gesture Id.
     * @param reportingMetadata Optional reporting metadata.
     * @param state The layout state.
     */
    public fun onPagerGesture(gestureId: String, reportingMetadata: JsonValue?, state: LayoutData)

    /**
     * Called when a pager changes its page due to a swipe.
     *
     * @param actionId The action Id.
     * @param reportingMetadata Optional reporting metadata.
     * @param state The layout state.
     */
    public fun onPagerAutomatedAction(
        actionId: String,
        reportingMetadata: JsonValue?,
        state: LayoutData
    )

    /**
     * Called whenever the view visibility changes
     *
     * @param isVisible The visibility state.
     * @param isForegrounded The app state.
     */
    public fun onVisibilityChanged(isVisible: Boolean, isForegrounded: Boolean)

    /**
     * Called when a view is dismissed because it timed out.
     *
     * @param state Optional layout state.
     */
    public fun onTimedOut(state: LayoutData?)
}
