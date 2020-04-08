/* Copyright Airship and Contributors */

package com.urbanairship.iam.html;

import androidx.annotation.NonNull;

import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;

/**
 * HTML adapter factory.
 */
public class HtmlAdapterFactory implements InAppMessageAdapter.Factory {

    @NonNull
    @Override
    public InAppMessageAdapter createAdapter(@NonNull InAppMessage message) {
        return HtmlDisplayAdapter.newAdapter(message);
    }

}
