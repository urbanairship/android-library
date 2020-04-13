/* Copyright Airship and Contributors */

package com.urbanairship.iam.fullscreen;

import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;

import androidx.annotation.NonNull;

/**
 * Full screen adapter factory.
 */
public class FullScreenAdapterFactory implements InAppMessageAdapter.Factory {

    @NonNull
    @Override
    public InAppMessageAdapter createAdapter(@NonNull InAppMessage message) {
        return FullScreenAdapter.newAdapter(message);
    }

}
