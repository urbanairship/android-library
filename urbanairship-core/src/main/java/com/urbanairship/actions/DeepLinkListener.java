/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import androidx.annotation.NonNull;

/**
 * Listener interface used to notify app when deep link is received.
 */
public interface DeepLinkListener {

    /**
     * Called when a new deep link is received. If the deep link is handled by the listener,
     * return {@code true} to prevent the default behavior.
     *
     * @param deepLink The deep link.
     * @return {@code true} if the deep link was handled, otherwise {@code false}.
     */
    boolean onDeepLink(@NonNull String deepLink);
}
