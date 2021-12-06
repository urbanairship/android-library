package com.urbanairship.android.layout.ui;

import com.urbanairship.android.layout.BasePayload;
import com.urbanairship.android.layout.event.EventListener;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Display arguments.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class DisplayArgs {
    private final EventListener eventListener;
    private final BasePayload payload;

    public DisplayArgs(@NonNull BasePayload payload, @Nullable EventListener eventListener) {
        this.payload = payload;
        this.eventListener = eventListener;
    }

    @Nullable
    public EventListener getEventListener() {
        return eventListener;
    }

    @NonNull
    public BasePayload getPayload() {
        return payload;
    }

}
