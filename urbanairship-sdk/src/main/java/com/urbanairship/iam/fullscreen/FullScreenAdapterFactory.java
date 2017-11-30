/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam.fullscreen;

import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;

/**
 * Full screen adapter factory.
 */
public class FullScreenAdapterFactory implements InAppMessageAdapter.Factory {
    @Override
    public InAppMessageAdapter createAdapter(InAppMessage message) {
        return new FullScreenAdapter(message);
    }
}
