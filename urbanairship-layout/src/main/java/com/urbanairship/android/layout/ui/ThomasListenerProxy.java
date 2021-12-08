/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.ui;

import com.urbanairship.android.layout.ThomasListener;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.EventListener;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * An event listener that calls through to a ThomasListener.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class ThomasListenerProxy implements EventListener {

    private ThomasListener listener;

    public ThomasListenerProxy(@NonNull ThomasListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onEvent(@NonNull Event event) {
        // TODO fill out
        return false;
    }

}
