/* Copyright Airship and Contributors */

package com.urbanairship.iam.modal;

import androidx.annotation.NonNull;

import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;

/**
 * Modal adapter factory.
 */
public class ModalAdapterFactory implements InAppMessageAdapter.Factory {

    @NonNull
    @Override
    public InAppMessageAdapter createAdapter(@NonNull InAppMessage message) {
        return ModalAdapter.newAdapter(message);
    }

}
