/* Copyright Airship and Contributors */

package com.urbanairship.iam.layout;

import android.content.Context;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.android.layout.BasePayload;
import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.ThomasListener;
import com.urbanairship.android.layout.reporting.FormData;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.android.layout.reporting.PagerData;
import com.urbanairship.iam.ButtonInfo;
import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.ForegroundDisplayAdapter;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;
import com.urbanairship.iam.ResolutionInfo;
import com.urbanairship.iam.TextInfo;
import com.urbanairship.iam.assets.Assets;
import com.urbanairship.iam.events.InAppReportingEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

/**
 * Airship layout display adapter.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AirshipLayoutDisplayAdapter extends ForegroundDisplayAdapter {

    private final InAppMessage message;
    private final AirshipLayoutDisplayContent displayContent;
    private final PrepareDisplayCallback prepareDisplayCallback;

    private Thomas.PendingDisplay pendingDisplay;

    @VisibleForTesting
    interface PrepareDisplayCallback {
        Thomas.PendingDisplay prepareDisplay(@NonNull BasePayload basePayload) throws Thomas.DisplayException;
    }

    @VisibleForTesting
    private static PrepareDisplayCallback DEFAULT_CALLBACK = Thomas::prepareDisplay;

    AirshipLayoutDisplayAdapter(@NonNull InAppMessage message,
                                @NonNull AirshipLayoutDisplayContent displayContent,
                                @NonNull PrepareDisplayCallback prepareDisplayCallback) {
        this.message = message;
        this.displayContent = displayContent;
        this.prepareDisplayCallback = prepareDisplayCallback;
    }

    /**
     * Creates a new display adapter.
     *
     * @param message The in-app message.
     * @return The adapter.
     */
    @NonNull
    public static AirshipLayoutDisplayAdapter newAdapter(@NonNull InAppMessage message) {
        AirshipLayoutDisplayContent displayContent = message.getDisplayContent();
        if (displayContent == null) {
            throw new IllegalArgumentException("Invalid message for adapter: " + message);
        }

        return new AirshipLayoutDisplayAdapter(message, displayContent, DEFAULT_CALLBACK);
    }

    @PrepareResult
    @Override
    public int onPrepare(@NonNull Context context, @NonNull Assets assets) {
        // TODO check allow list
        //             return InAppMessageAdapter.CANCEL;

        try {
            this.pendingDisplay = this.prepareDisplayCallback.prepareDisplay(displayContent.getPayload());
        } catch (Thomas.DisplayException e) {
            Logger.error("Unable to display layout", e);
            return InAppMessageAdapter.CANCEL;
        }
        return InAppMessageAdapter.OK;
    }

    @Override
    public void onDisplay(@NonNull Context context, @NonNull DisplayHandler displayHandler) {
        this.pendingDisplay.setListener(new Listener(message, displayHandler))
                           .display(context);
    }

    @Override
    public void onFinish(@NonNull Context context) {

    }

    private static class Listener implements ThomasListener {

        private final InAppMessage message;
        private final DisplayHandler displayHandler;
        private final String scheduleId;

        private Listener(@NonNull InAppMessage message, @NonNull DisplayHandler displayHandler) {
            this.message = message;
            this.displayHandler = displayHandler;
            this.scheduleId = displayHandler.getScheduleId();
        }

        @Override
        public void onPageView(@NonNull PagerData pagerData, @Nullable LayoutData layoutData) {
            InAppReportingEvent event = InAppReportingEvent.pageView(scheduleId, message, pagerData)
                                                           .setLayoutData(layoutData);

            displayHandler.addEvent(event);
        }

        @Override
        public void onPageSwipe(@NonNull PagerData pagerData, int toIndex, int fromIndex, @Nullable LayoutData layoutData) {
            InAppReportingEvent event = InAppReportingEvent.pageSwipe(scheduleId, message, pagerData, toIndex, fromIndex)
                                                           .setLayoutData(layoutData);

            displayHandler.addEvent(event);
        }

        @Override
        public void onButtonTap(@NonNull String buttonId, @Nullable LayoutData layoutData) {
            InAppReportingEvent event = InAppReportingEvent.buttonTap(scheduleId, message, buttonId)
                                                           .setLayoutData(layoutData);

            displayHandler.addEvent(event);
        }

        @Override
        public void onDismiss(long displayTime) {
            ResolutionInfo resolutionInfo = ResolutionInfo.dismissed();
            InAppReportingEvent event = InAppReportingEvent.resolution(scheduleId, message, displayTime, resolutionInfo);
            displayHandler.addEvent(event);
            displayHandler.notifyFinished(resolutionInfo);
        }

        @Override
        public void onDismiss(@NonNull String buttonId, @Nullable String buttonDescription, boolean cancel, long displayTime, @Nullable LayoutData layoutData) {
            ResolutionInfo resolutionInfo = ResolutionInfo.buttonPressed(buttonId, buttonDescription, cancel);
            InAppReportingEvent event = InAppReportingEvent.resolution(scheduleId, message, displayTime, resolutionInfo)
                    .setLayoutData(layoutData);

            displayHandler.addEvent(event);
            displayHandler.notifyFinished(resolutionInfo);

            if (cancel) {
                displayHandler.cancelFutureDisplays();
            }
        }

        @Override
        public void onFormResult(@NonNull FormData<?> formData, @Nullable LayoutData layoutData) {
            InAppReportingEvent event = InAppReportingEvent.formResult(scheduleId, message, formData)
                                                           .setLayoutData(layoutData);

            displayHandler.addEvent(event);
        }

        @Override
        public void onFormDisplay(@NonNull String formId, @Nullable LayoutData layoutData) {
            InAppReportingEvent event = InAppReportingEvent.formDisplay(scheduleId, message, formId)
                                                           .setLayoutData(layoutData);

            displayHandler.addEvent(event);
        }
    }

}
