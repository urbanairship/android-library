/* Copyright Airship and Contributors */

package com.urbanairship.iam.layout;

import android.content.Context;
import android.content.Intent;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.BasePayload;
import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.EventListener;
import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.ForegroundDisplayAdapter;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;
import com.urbanairship.iam.ResolutionInfo;
import com.urbanairship.iam.assets.Assets;
import com.urbanairship.iam.html.HtmlActivity;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

/**
 * Airship layout display adapter.
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
        this.pendingDisplay
                // TODO an external friendly event listener?
                .setEventListener(event -> {
                    switch (event.getType()) {
                        case BUTTON_BEHAVIOR_DISMISS:
                            displayHandler.finished(ResolutionInfo.dismissed(), 0);
                        case BUTTON_BEHAVIOR_CANCEL:
                            displayHandler.cancelFutureDisplays();
                            displayHandler.finished(ResolutionInfo.dismissed(), 0);
                    }
                    return true;
                })
                .display(context);
    }

    @Override
    public void onFinish(@NonNull Context context) {

    }
}
