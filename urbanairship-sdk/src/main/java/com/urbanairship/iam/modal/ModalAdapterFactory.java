/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam.modal;

import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;

/**
 * Modal adapter factory.
 */
public class ModalAdapterFactory implements InAppMessageAdapter.Factory {
    @Override
    public InAppMessageAdapter createAdapter(InAppMessage message) {
        return ModalAdapter.newAdapter(message);
    }
}
