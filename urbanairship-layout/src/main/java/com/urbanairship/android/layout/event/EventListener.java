/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

import androidx.annotation.NonNull;

/** Event listener interface. */
public interface EventListener {
    /**
     * Called when an event is received.
     *
     * @param event the received {@code Event}.
     * @return {@code true} if the event was handled, {@code false} otherwise.
     */
    boolean onEvent(@NonNull Event event);
}
