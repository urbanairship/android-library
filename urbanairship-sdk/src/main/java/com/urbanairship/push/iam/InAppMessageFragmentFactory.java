/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push.iam;

import android.annotation.TargetApi;
import android.os.Build;

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
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public abstract InAppMessageFragment createFragment(InAppMessage message);
}
