/* Copyright Airship and Contributors */

package com.urbanairship.android.layout;

import com.urbanairship.android.layout.reporting.FormData;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.android.layout.reporting.PagerData;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Thomas listener.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface ThomasListener {

    /**
     * Called when a pager changes its page.
     * @param pagerData The pager data.
     * @param state The layout state.
     * @param displayedAt A timestamp from {@code DisplayTimer}.
     */
    void onPageView(@NonNull PagerData pagerData, @Nullable LayoutData state, long displayedAt);

    /**
     * Called when a pager changes its page due to a swipe gesture.
     * @param pagerData The pager data.
     * @param toPageIndex The resulting page index.
     * @param toPageId The page resulting page Id.
     * @param fromPageIndex The page index that the swipe originated on.
     * @param fromPageId The page Id that the swipe originated on.
     * @param state The layout state.
     */
    void onPageSwipe(@NonNull PagerData pagerData,
                     int toPageIndex,
                     @NonNull String toPageId,
                     int fromPageIndex,
                     @NonNull String fromPageId,
                     @Nullable LayoutData state);

    /**
     * Called when a button is tapped.
     * @param buttonId The button Id.
     * @param state The layout state.
     */
    void onButtonTap(@NonNull String buttonId, @Nullable LayoutData state);

    /**
     * Called when the view is dismissed from outside the view.
     * @param displayTime The total display time in milliseconds.
     */
    void onDismiss(long displayTime);

    /**
     * Called when the view is dismissed from a button.
     * @param buttonId The button Id.
     * @param buttonDescription The button description.
     * @param cancel If the experience should be cancelled.
     * @param displayTime The total display time in milliseconds.
     * @param state The layout state.
     */
    void onDismiss(@NonNull String buttonId,
                   @Nullable String buttonDescription,
                   boolean cancel,
                   long displayTime,
                   @Nullable LayoutData state);

    /**
     * Called when a form is submitted.
     * @param formData The form data.
     * @param state The layout state.
     */
    void onFormResult(@NonNull FormData<?> formData, @Nullable LayoutData state);

    /**
     * Called when a form is displayed.
     * @param formId The form Id.
     * @param state The layout state.
     */
    void onFormDisplay(@NonNull String formId, @Nullable LayoutData state);
}
