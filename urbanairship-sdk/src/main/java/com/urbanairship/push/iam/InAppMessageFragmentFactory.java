/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.iam;

/**
 * Factory to create in-app message fragments.
 */
public abstract class InAppMessageFragmentFactory {

    /**
     * Creates an {@link com.urbanairship.push.iam.InAppMessageFragment} to display the given
     * {@link com.urbanairship.push.iam.InAppMessage}.
     *
     * @return A InAppMessageFragment for the given message.
     */
    public abstract InAppMessageFragment createFragment(InAppMessage message);
}
