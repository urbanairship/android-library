/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam.html;

import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;

/**
 * Full screen adapter factory.
 */
public class HtmlAdapterFactory implements InAppMessageAdapter.Factory {
    @Override
    public InAppMessageAdapter createAdapter(InAppMessage message) {
        return new HtmlDisplayAdapter(message);
    }
}
