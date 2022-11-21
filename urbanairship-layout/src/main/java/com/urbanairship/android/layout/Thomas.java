/* Copyright Airship and Contributors */

package com.urbanairship.android.layout;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

import com.urbanairship.android.layout.display.DisplayArgsLoader;
import com.urbanairship.android.layout.display.DisplayException;
import com.urbanairship.android.layout.display.DisplayRequest;
import com.urbanairship.android.layout.info.LayoutInfo;

import com.urbanairship.android.layout.ui.LayoutBanner;
import com.urbanairship.android.layout.ui.ModalActivity;

import com.urbanairship.app.GlobalActivityMonitor;

/**
 * Entry point and related helper methods for rendering layouts based on our internal DSL.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class Thomas {
    @VisibleForTesting
    static final int MAX_SUPPORTED_VERSION = 2;
    @VisibleForTesting
    static final int MIN_SUPPORTED_VERSION = 1;

    private Thomas() {}

    /**
     * Validates that a payload can be displayed.
     * @param payload The payload.
     * @return {@code true} if valid, otherwise {@code false}.
     */
    public static boolean isValid(@NonNull LayoutInfo payload) {
        if (!(payload.getVersion() >= MIN_SUPPORTED_VERSION && payload.getVersion() <= MAX_SUPPORTED_VERSION)) {
            return false;
        }

        if (payload.getPresentation() instanceof ModalPresentation
                || payload.getPresentation() instanceof BannerPresentation) {
          return true;
        }

        return false;
    }

    @NonNull
    public static DisplayRequest prepareDisplay(@NonNull LayoutInfo payload) throws DisplayException {
        if (!isValid(payload)) {
            throw new DisplayException("Payload is not valid: " + payload.getPresentation());
        }

        if (payload.getPresentation() instanceof ModalPresentation) {
            return new DisplayRequest(payload, (context, args) -> {
                Intent intent = new Intent(context, ModalActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(ModalActivity.EXTRA_DISPLAY_ARGS_LOADER, DisplayArgsLoader.newLoader(args));
                context.startActivity(intent);
            });
        } else if (payload.getPresentation() instanceof BannerPresentation) {
            return new DisplayRequest(payload, (context, args) -> {
                LayoutBanner layoutBanner = new LayoutBanner(context, args);
                layoutBanner.display();
            });
        } else {
            throw new DisplayException("Presentation not supported: " + payload.getPresentation());
        }
    }
}
