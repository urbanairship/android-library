package com.urbanairship.android.layout.ui;

import com.urbanairship.android.layout.BasePayload;
import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.ThomasListener;
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
    private final ThomasListener listener;
    private final BasePayload payload;

    public DisplayArgs(@NonNull BasePayload payload, @Nullable ThomasListener listener) {
        this.payload = payload;
        this.listener = listener;
    }

    @Nullable
    public ThomasListener getListener() {
        return listener;
    }

    @NonNull
    public BasePayload getPayload() {
        return payload;
    }

}
