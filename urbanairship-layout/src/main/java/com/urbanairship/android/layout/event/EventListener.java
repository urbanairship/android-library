/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

import com.urbanairship.android.layout.reporting.LayoutData;

import androidx.annotation.NonNull;

/** Event listener interface. */
public interface EventListener {
    /**
     * Called when an event is received.
     *
     * @param event The received {@code Event}.
     * @param layoutData The layout data.
     * @return {@code true} if the event was handled, {@code false} otherwise.
     */
    boolean onEvent(@NonNull Event event, @NonNull LayoutData layoutData);
}
