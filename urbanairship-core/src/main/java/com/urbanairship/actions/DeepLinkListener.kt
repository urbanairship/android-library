/* Copyright Airship and Contributors */
package com.urbanairship.actions

/**
 * Listener interface used to notify app when deep link is received.
 */
public fun interface DeepLinkListener {

    /**
     * Called when a new deep link is received. If the deep link is handled by the listener,
     * return `true` to prevent the default behavior.
     *
     * @param deepLink The deep link.
     * @return `true` if the deep link was handled, otherwise `false`.
     */
    public fun onDeepLink(deepLink: String): Boolean
}
